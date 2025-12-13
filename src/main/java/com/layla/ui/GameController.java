package com.layla.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.layla.AppContext;
import com.layla.core.AssetsManager;
import com.layla.core.GameEntity;
import com.layla.core.GameLoop;
import com.layla.core.InputService;
import com.layla.db.DatabaseService;
import com.layla.entities.Boss;
import com.layla.entities.Coin;
import com.layla.entities.GreedButton;
import com.layla.entities.ItemPedestal;
import com.layla.entities.Player;
import com.layla.entities.Rock;
import com.layla.entities.SpawnIndicator;
import com.layla.items.ItemDefinition;
import com.layla.items.ItemId;
import com.layla.items.ItemPoolType;
import com.layla.items.ItemRegistry;
import com.layla.model.CharacterType;
import com.layla.model.Enemy;
import com.layla.model.EnemyProfile;
import com.layla.model.EnemyType;
import com.layla.model.PlayerStatId;
import com.layla.services.AchievementService;
import com.layla.services.ShootingService;
import com.layla.services.SoundService;
import com.layla.services.StatsService;
import com.layla.ui.ShopOverlayController.ShopOffer;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class GameController implements ViewLifecycle {

    @FXML private Pane gameArea;
    @FXML private HBox hudBar;
    @FXML private Label scoreLabel;
    @FXML private Label coinLabel;
    @FXML private Label floorLabel;
    @FXML private Label healthLabel; // Ignorado/Oculto
    @FXML private StackPane root;
    @FXML private StackPane overlayLayer;

    private ImageView backgroundView;

    private javafx.scene.Node pauseOverlay;
    private javafx.scene.Node gameOverOverlay;
    private javafx.scene.Node shopOverlay;

    private VBox bossHealthBox;
    private ProgressBar bossHealthBar;
    private Label bossNameLabel;

    // Tendero físico (ShopKeeper)
    private GameEntity shopKeeperEntity;
    private double shopCooldown = 0.0; // Cooldown para evitar re-entrada inmediata

    // ESTADO PERSISTENTE DE LA TIENDA
    private List<ShopOffer> currentShopOffers = new ArrayList<>();
    private int currentRerollPrice = 1;

    private boolean musicStarted = false;
    private int score = 500;
    private int coins = Math.max(0, com.layla.AppContext.balance().startCoins);
    private Label timeLabel;

    private int wavesPerFloor = 5;
    private static final int MAX_FLOORS = 5;
    private int currentFloor = 1;
    private int currentWave = 0;

    private double nextWaveTimer = 0.0;
    private int pendingSpawns = 0;
    private boolean waveInProgress = false;

    // Estado Greed
    private boolean timerStopped = false;
    private boolean moneyPenaltyActive = false;
    private double buttonSafetyTimer = 0.0;

    private double constantSpawnInterval;
    private double timeUntilNextSpawn = 0.0;

    private boolean gameStarted = false;
    private boolean paused = false;
    private boolean gameOverShown = false;

    private GameLoop gameLoop;
    private Player player;
    private boolean playerSpawnListenerAdded = false;
    private boolean startGateOpen = false;

    private Boss activeBoss = null;
    private GreedButton greedButton;

    private InputService input;
    private final StatsService statsService = com.layla.AppContext.stats();
    private ShootingService shootingService;
    private final SoundService sound = new SoundService();
    private final DatabaseService db = AppContext.db();
    private final AchievementService achievements = AppContext.achievements();

    private double bias = 0.2;
    private boolean tickerAdded = false;
    private GameEntity ticker;

    private final List<Enemy> enemies = new ArrayList<>();
    private final List<GameEntity> obstacles = new ArrayList<>();
    private final ThreadLocalRandom enemyRng = ThreadLocalRandom.current();
    private static final double SEPARATION_EPS = 1e-5;
    private static final double MAX_SEPARATION_STEP = 6.0;
    private static final int SHOP_OFFER_COUNT = 3;
    private static final int SHOP_REROLL_BASE_PRICE = 1; // Precio base inicial

    private static final int SCORE_PENALTY_PER_SECOND = 1;
    private static final int SCORE_PENALTY_ON_DAMAGE = 20;
    private static final int SCORE_PENALTY_ON_BUY = 30;

    private int comboCount = 0;
    private double comboMultiplier = 1.0;
    private double comboTimer = 0.0;
    private static final double COMBO_MAX_TIME = 3.5;

    private double scoreTimer = 0.0;
    private double lastPlayerHealth = -1.0;

    private int rewardItemCursor = 0;
    private HudView hud;
    private ItemHudView itemHud;
    private double hudRefreshTimer = 0.0;
    private boolean shootingArmed = false;
    private double shootingArmTimer = 0.6;

    @FXML
    private void onPausePressed() {
        if (gameOverShown || pauseOverlay != null || paused) return;
        paused = true;
        Platform.runLater(() -> { if (gameLoop != null && gameLoop.isRunning()) gameLoop.stop(); });

        pauseOverlay = OverlayRouter.showOverlay(overlayLayer, "ui/pause_overlay.fxml", 0.50, controller -> {
            if (controller instanceof PauseOverlayController poc) {
                poc.setOnResume(this::resumeFromPause);
                poc.setOnBackToMenu(() -> {
                    OverlayRouter.closeOverlay(overlayLayer, pauseOverlay);
                    pauseOverlay = null;
                    Platform.runLater(() -> {
                        if (gameLoop != null && gameLoop.isRunning()) gameLoop.stop();
                        paused = false;
                        backToMenu();
                    });
                });
                poc.setOnSettings(() -> {
                    final javafx.scene.Node[] settingsNode = new javafx.scene.Node[1];
                    settingsNode[0] = OverlayRouter.showOverlay(overlayLayer, "ui/settings.fxml", 0.90, c -> {
                        if (c instanceof SettingsController sc) {
                            sc.setStatsService(statsService);
                            sc.setOverlayHost(overlayLayer);
                            sc.setInitialCoins(coins);
                            sc.setOnCoinsChanged(newCoins -> {
                                coins = Math.max(0, newCoins);
                                updateHudLabels();
                            });
                            sc.setOnClose(() -> OverlayRouter.closeOverlay(overlayLayer, settingsNode[0]));
                        }
                    });
                });
            }
        });
    }

    private void resumeFromPause() {
        if (pauseOverlay != null) {
            OverlayRouter.closeOverlay(overlayLayer, pauseOverlay);
            pauseOverlay = null;
        }
        Platform.runLater(() -> {
            if (gameLoop != null && !gameLoop.isRunning()) gameLoop.start();
            paused = false;
        });
    }

    private static String formatTime(double seconds) {
        int s = (int) Math.ceil(seconds);
        return String.format("%02d", s);
    }

    private void updateHudLabels() {
        String multiplierText = comboMultiplier > 1.0 ? String.format(" (x%.1f)", comboMultiplier) : "";
        if (scoreLabel != null) scoreLabel.setText("Score: " + score + multiplierText);
        if (coinLabel  != null) coinLabel.setText("Coins: " + coins);

        String floorName = switch(currentFloor) {
            case 1 -> "Basement";
            case 2 -> "Caves";
            case 3 -> "Depths";
            case 4 -> "Womb";
            default -> "Sheol";
        };

        String waveText = currentWave == 0 ? "Ready" : currentWave + "/" + wavesPerFloor;
        if (floorLabel != null) floorLabel.setText(floorName + " - Wave " + waveText);

        if (timeLabel != null) {
            if (activeBoss != null) {
                timeLabel.setText("BOSS");
                timeLabel.setStyle("-fx-text-fill: #ff0000; -fx-font-weight: bold;");
            } else if (waveInProgress) {
                if (timerStopped) {
                    timeLabel.setText("PAUSED");
                    timeLabel.setStyle("-fx-text-fill: #aaaaff; -fx-font-weight: bold;");
                } else {
                    timeLabel.setText("Next: " + formatTime(nextWaveTimer));
                    timeLabel.setStyle("-fx-text-fill: white;");
                    if (nextWaveTimer <= 3.0) {
                        timeLabel.setStyle("-fx-text-fill: #ff5555; -fx-effect: dropshadow(gaussian, red, 10, 0.5, 0, 0);");
                    }
                }
            } else {
                timeLabel.setText(currentWave == 0 ? "START" : "CLEAR");
                timeLabel.setStyle("-fx-text-fill: #55ff55;");
            }
        }

        if (activeBoss != null && bossHealthBar != null) {
            bossHealthBar.setProgress(activeBoss.getHp() / activeBoss.getMaxHp());
        }

        // ASEGURAR VISIBILIDAD DE ITEMS
        if (itemHud != null) {
            itemHud.toFront();
            itemHud.refresh();
        }
    }

    @FXML
    private void initialize() {
        if (hudBar != null && healthLabel != null) {
            hudBar.getChildren().remove(healthLabel);
        }

        if (hudBar != null) {
            if (timeLabel == null) {
                timeLabel = new Label();
                timeLabel.getStyleClass().add("hud-timer");
                timeLabel.setStyle("-fx-font-size: 24px;");
            }
            if (!hudBar.getChildren().contains(timeLabel)) {
                hudBar.getChildren().add(hudBar.getChildren().size(), timeLabel);
            }
        }

        bossHealthBar = new ProgressBar(1.0);
        bossHealthBar.setPrefWidth(600);
        bossHealthBar.setStyle("-fx-accent: #cc0000; -fx-control-inner-background: #333333; -fx-text-box-border: transparent;");
        bossNameLabel = new Label("BOSS");
        bossNameLabel.setStyle("-fx-text-fill: #ffaaaa; -fx-font-weight: bold; -fx-font-size: 18px; -fx-effect: dropshadow(gaussian, black, 2, 1, 0, 0);");

        bossHealthBox = new VBox(4, bossNameLabel, bossHealthBar);
        bossHealthBox.setAlignment(Pos.CENTER);
        bossHealthBox.setManaged(true);
        bossHealthBox.setVisible(false);

        if (overlayLayer != null) {
            overlayLayer.getChildren().add(bossHealthBox);
            StackPane.setAlignment(bossHealthBox, Pos.TOP_CENTER);
            StackPane.setMargin(bossHealthBox, new Insets(80, 0, 0, 0));
        }

        updateHudLabels();
    }

    private void applyBalanceToRuntimePlayer() {
        if (player == null) return;
        var bal = com.layla.AppContext.balance();

        CharacterType charType = AppContext.getSelectedCharacter();
        double initialMaxHp = bal.maxHp;

        if (charType == CharacterType.THE_FRAGILE) {
            initialMaxHp = 2.0;
            statsService.setBaseStat(PlayerStatId.PROJECTILE_DAMAGE, 1.5);
            statsService.setBaseStat(PlayerStatId.MOVE_SPEED, statsService.getBaseStat(PlayerStatId.MOVE_SPEED) * 1.1);
        } else {
            statsService.setBaseStat(PlayerStatId.PROJECTILE_DAMAGE, 1.0);
        }

        statsService.setBaseStat(PlayerStatId.MAX_HEALTH, initialMaxHp);
        player.setMaxHealth(statsService.getMaxHealth());
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    @Override
    public void onEnter() {
        this.wavesPerFloor = AppContext.getRunModifiers().wavesPerFloor;

        resetPlainGameArea();
        if (hudBar != null) {
            hudBar.setOpacity(1.0);
            new FadeTransition(Duration.millis(400), hudBar).play();
        }

        NotificationService ns = AppContext.notifications();
        if (root != null && !root.getChildren().contains(ns.getView())) {
            root.getChildren().add(ns.getView());
            StackPane.setAlignment(ns.getView(), Pos.TOP_CENTER);
            StackPane.setMargin(ns.getView(), new Insets(10, 0, 0, 0));
        }

        Platform.runLater(() -> {
            var sc = gameArea.getScene();
            if (input == null) input = new InputService();
            input.attach(sc);
            sc.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    if (!gameOverShown && !paused) onPausePressed();
                    else if (paused) resumeFromPause();
                } else {
                    handleDebugItemHotkeys(e.getCode());
                }
            });
            gameArea.requestFocus();
        });

        if (gameLoop == null) gameLoop = new GameLoop(gameArea);
        if (!playerSpawnListenerAdded) {
            playerSpawnListenerAdded = true;
            gameArea.widthProperty().addListener((obs, ow, nw) -> maybeSpawnPlayer());
            gameArea.heightProperty().addListener((obs, oh, nh) -> maybeSpawnPlayer());
        }
        maybeSpawnPlayer();
    }

    @Override
    public void onExit() {
        if (gameLoop != null) gameLoop.stop();
        if (input != null) input.detach();
        startGateOpen = false;
        gameOverShown = false;
        paused = false;
        activeBoss = null;
        waveInProgress = false;
        if (greedButton != null) greedButton = null;
        shopKeeperEntity = null;
    }

    public void signalGameStart() {
        if (startGateOpen) return;
        startGateOpen = true;
        gameStarted = true;

        currentFloor = 1;
        currentWave = 0;
        enemies.clear();
        pendingSpawns = 0;

        currentRerollPrice = SHOP_REROLL_BASE_PRICE;
        currentShopOffers = generateShopOffers(SHOP_OFFER_COUNT);

        changeFloorVisuals();

        spawnGreedButton();
        spawnShopKeeper();

        updateHudLabels();
        maybeSpawnPlayer();
        addTickerIfNeeded();
        startFloorMusicIfNeeded();

        AppContext.notifications().showNotification("GREED MODE", "Touch button to start!", 4.0);
    }

    private void spawnGreedButton() {
        if (greedButton != null) {
            gameLoop.removeEntity(greedButton);
            obstacles.remove(greedButton);
        }

        double w = gameArea.getWidth();
        double h = gameArea.getHeight();
        if (w <= 0) w = 1200;
        if (h <= 0) h = 800;

        double cx = w / 2.0;
        double cy = h / 2.0;

        // Comprobación rápida para no caer encima de una roca
        boolean obstructed = false;
        for (GameEntity e : obstacles) {
            if(e instanceof Rock) {
                Node view = e.getView();
                if (view != null) {
                    double ox = view.getLayoutX() + 25;
                    double oy = view.getLayoutY() + 25;
                    if (Math.hypot(cx - ox, cy - oy) < 80) {
                        obstructed = true;
                        break;
                    }
                }
            }
        }

        if (obstructed) {
            cx += 200.0;
        }

        greedButton = new GreedButton(cx, cy, gameArea, (btn) -> {
            if (!waveInProgress) {
                // START: Permite iniciar oleada normal O Boss si toca (<= wavesPerFloor)
                if (currentWave <= wavesPerFloor) {
                    btn.setActiveState(true);
                    startNextWave();
                }
            } else {
                // STOP: Check de seguridad (delay 3s)
                if (buttonSafetyTimer > 0) return;

                if (!timerStopped && nextWaveTimer > 0) {
                    player.takeDamage(1.0);
                    sound.play("hurt");

                    coins = Math.max(0, coins - 5);
                    updateHudLabels();

                    timerStopped = true;
                    btn.setActiveState(false);

                    AppContext.notifications().showNotification("PAUSED!", "-1 HP, -5 Coins", 2.0);
                } else if (!timerStopped) {
                     player.takeDamage(0.5);
                     sound.play("hurt");
                }
            }
        });
        greedButton.setActiveState(false);
        gameLoop.addEntity(greedButton);
        checkAndPushInteractive(greedButton); // Asegura que no esté sobre nada
    }

    public void backToMenu() { SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml"); }
    public StackPane getOverlayLayer() { return overlayLayer; }
    public StatsService getStatsService() { return statsService; }

    public void startFloorMusicIfNeeded() {
        if (!musicStarted) {
            try {
                AssetsManager.playMusic("basement1.mp3", true);
                musicStarted = true;
            } catch (Exception ex) { System.err.println("Music error: " + ex.getMessage()); }
        }
    }

    private void startWave(int wave) {
        if (wave > wavesPerFloor) {
            spawnBoss();
            return;
        }

        waveInProgress = true;
        timerStopped = false;
        moneyPenaltyActive = false;
        buttonSafetyTimer = 3.0;

        if (greedButton != null) greedButton.setActiveState(true);
        removeShopKeeper();

        // Si es la oleada final, invocamos al BOSS
        if (wave == wavesPerFloor) {
            spawnBoss();
            return;
        }

        // Oleada normal
        nextWaveTimer = 10.0 + (wave * 2.0);

        double difficulty = currentFloor * 1.5 + wave * 0.5;
        int enemyCount = (int)((4 + (difficulty * 1.3)) * Math.max(0.1, AppContext.getRunModifiers().spawnRateMult));

        spawnWaveEnemies(Math.max(1, enemyCount));

        constantSpawnInterval = Math.max(0.25, 2.0 - (difficulty * 0.15));
        timeUntilNextSpawn = 1.0;

        updateHudLabels();
    }

    private void updateGreedLogic(double dt) {
        if (activeBoss != null) return;

        // Reducir temporizador de seguridad si está activo
        if (buttonSafetyTimer > 0) buttonSafetyTimer -= dt;

        if (!waveInProgress) {
            if (shopKeeperEntity == null && enemies.isEmpty() && pendingSpawns == 0) {
                spawnShopKeeper();
                if (greedButton != null) greedButton.setActiveState(false);
            }
            return;
        }

        if (!timerStopped) {
            nextWaveTimer -= dt;
        }

        timeUntilNextSpawn -= dt;
        if (timeUntilNextSpawn <= 0.0) { }

        boolean timeUp = nextWaveTimer <= 0.0;
        boolean allDead = enemies.isEmpty() && pendingSpawns == 0;

        if (allDead) {
            // Oleada limpia
            if (nextWaveTimer > 0 && !moneyPenaltyActive) {
                spawnRewardCoins(player.getView().getLayoutX(), player.getView().getLayoutY(), 1);
                AppContext.notifications().showNotification("QUICK CLEAR!", "+1 Coin", 2.0);
            }

            nextWaveTimer = 0;

            // Si la siguiente es <= al Boss, iniciamos
            if (currentWave < wavesPerFloor) {
                startNextWave();
            } else {
                // Hemos terminado la oleada normal justo antes del Boss (o el Boss mismo ya murió)
                waveInProgress = false;
                if (greedButton != null) greedButton.setActiveState(false);
            }
        }
        else if (timeUp && !timerStopped) {
            // Tiempo agotado (Stacking)
            if (currentWave < wavesPerFloor) {
                // Permitimos stackear hasta la oleada anterior al Boss
                if (currentWave < wavesPerFloor - 1) {
                    currentWave++;
                    spawnWaveEnemiesForCurrentWave();
                    nextWaveTimer = 10.0 + (currentWave * 2.0);
                    AppContext.notifications().showNotification("OVERWHELMED!", "Waves are stacking!", 2.0);
                }
            }
        }
    }

    private void spawnWaveEnemiesForCurrentWave() {
        double difficulty = currentFloor * 1.5 + currentWave * 0.5;
        int enemyCount = (int)((4 + (difficulty * 1.3)) * Math.max(0.1, AppContext.getRunModifiers().spawnRateMult));
        spawnWaveEnemies(Math.max(1, enemyCount));
        updateHudLabels();
    }

    private void startNextWave() {
        if (gameOverShown || !startGateOpen) return;
        currentWave++;

        // Si superamos el máximo de oleadas (ya matamos al Boss), siguiente piso
        if (currentWave > wavesPerFloor) {
            // Esto solo ocurre si pulsamos el botón DESPUÉS de matar al boss
            return;
        }

        startWave(currentWave);
        updateHudLabels();
    }

    private void spawnShopKeeper() {
        if (shopKeeperEntity != null) {
            gameLoop.removeEntity(shopKeeperEntity);
            obstacles.remove(shopKeeperEntity);
        }
        if (gameArea == null) return;

        double w = gameArea.getWidth(); if (w <= 0) w = 1200;
        double h = gameArea.getHeight(); if (h <= 0) h = 800;
        double cx = w / 2.0;
        double cy = h / 2.0;

        if (greedButton != null) {
            Node btnView = greedButton.getView();
            cx = btnView.getLayoutX() + 22;
            cy = btnView.getLayoutY() + 22;
        }

        final double finalCx = cx;
        final double finalCy = cy;

        shopKeeperEntity = new GameEntity() {
            StackPane view;
            {
                view = new StackPane();
                Rectangle body = new Rectangle(40, 40, Color.SADDLEBROWN);
                body.setArcWidth(10); body.setArcHeight(10);
                body.setStroke(Color.BLACK); body.setStrokeWidth(2);
                Text label = new Text("SHOP");
                label.setFill(Color.GOLD);
                label.setStyle("-fx-font-weight: bold;");
                view.getChildren().addAll(body, label);

                view.setLayoutX(finalCx - 20);
                view.setLayoutY(finalCy - 100);
                view.setEffect(new javafx.scene.effect.DropShadow(5, Color.BLACK));
            }
            @Override public void update(double dt) {
                if (player != null && !paused && !gameOverShown && shopCooldown <= 0) {
                    Bounds b1 = view.getBoundsInParent();
                    Bounds b2 = player.getBounds();
                    double dist = Math.hypot(b1.getCenterX() - b2.getCenterX(), b1.getCenterY() - b2.getCenterY());

                    if (dist < 30.0) {
                        showShopOverlay();
                    }
                }
            }
            @Override public Node getView() { return view; }
            @Override public Bounds getBounds() { return view.getBoundsInParent(); }
        };
        gameLoop.addEntity(shopKeeperEntity);
        obstacles.add(shopKeeperEntity); // Se añade para lógica, pero se ignora en colisiones

        checkAndPushInteractive(shopKeeperEntity);
    }

    /**
     * Mueve la entidad si está colisionando con una roca u otro obstáculo.
     * Ahora intenta varias direcciones hasta encontrar un hueco.
     */
    private void checkAndPushInteractive(GameEntity entity) {
        if (entity.getView() == null) return;

        int tries = 0;
        boolean collided = true;

        while (collided && tries < 15) {
            collided = false;
            Bounds b = entity.getBounds();

            for (GameEntity obs : obstacles) {
                if (obs != entity && obs.getBounds().intersects(b)) {
                    collided = true;
                    // Mover hacia la derecha/abajo para encontrar hueco
                    entity.getView().setLayoutX(entity.getView().getLayoutX() + 60);
                    // Si se sale mucho, bajar de linea
                    if (entity.getView().getLayoutX() > gameArea.getWidth() - 100) {
                        entity.getView().setLayoutX(100);
                        entity.getView().setLayoutY(entity.getView().getLayoutY() + 60);
                    }
                    break;
                }
            }
            tries++;
        }
    }

    private void removeShopKeeper() {
        if (shopKeeperEntity != null) {
            gameLoop.removeEntity(shopKeeperEntity);
            obstacles.remove(shopKeeperEntity);
            shopKeeperEntity = null;
        }
    }

    private void spawnWaveEnemies(int count) {
        if (gameArea == null || player == null) return;
        double w = gameArea.getWidth(); if (w<=0) w=1200;
        double h = gameArea.getHeight(); if (h<=0) h=800;

        for (int i = 0; i < count; i++) {
            double x = 0, y = 0;
            boolean valid = false;
            for(int tries=0; tries<10; tries++) {
                x = enemyRng.nextDouble(20, w - 40);
                y = enemyRng.nextDouble(20, h - 40);
                if (isValidSpawn(x, y, 20, 20)) {
                    valid = true;
                    break;
                }
            }
            if(!valid) { x=50; y=50; }

            EnemyType type = pickEnemyTypeForWave();
            double spawnDelay = enemyRng.nextDouble(0.2, 1.5);
            createSpawnIndicator(x, y, type, spawnDelay);
        }
    }

    private void createSpawnIndicator(double x, double y, EnemyType type, double delay) {
        pendingSpawns++;
        SpawnIndicator indicator = new SpawnIndicator(x, y, 1.5 + delay, gameArea, (ind) -> {
            spawnRealEnemy(ind.getX(), ind.getY(), type);
            pendingSpawns--;
        });
        gameLoop.addEntity(indicator);
    }

    private void spawnRealEnemy(double x, double y, EnemyType type) {
        double difficulty = currentFloor * 1.0 + (currentWave * 0.1);
        if (AppContext.isHardMode()) difficulty *= 1.5;

        double hpMul = 1.0 + difficulty * 0.2;
        double speedMul = 1.0 + difficulty * 0.05;
        double dmgMul = 1.0 + difficulty * 0.1;

        var mods = AppContext.getRunModifiers();
        hpMul *= Math.max(0.1, mods.enemyHpMult);
        dmgMul *= Math.max(0.1, mods.enemyDmgMult);

        Enemy enemy = new Enemy(
            type, gameArea, this::getPlayerCenter,
            () -> obstacles,
            e -> {
                gameLoop.removeEntity(e);
                if (e instanceof Enemy en) {
                    enemies.remove(en);
                    db.incrementEnemyStatAsync(AppContext.getProfileId(), en.getType().name(), DatabaseService.StatType.KILLED);
                    long total = db.getStatTotal(AppContext.getProfileId(), "TOTAL_KILLS");
                    achievements.onEnemyKilled(total + 1);
                    handleEnemyDeathRewards(en);
                }
            },
            ge -> gameLoop.addEntity(ge),
            k -> sound.play(k),
            hpMul, speedMul, dmgMul
        );
        enemy.setPosition(x, y);
        enemies.add(enemy);
        gameLoop.addEntity(enemy);

        db.incrementEnemyStatAsync(AppContext.getProfileId(), type.name(), DatabaseService.StatType.SEEN);
    }

    private void spawnBoss() {
        waveInProgress = true;
        nextWaveTimer = 99999;

        if (greedButton != null) {
            gameLoop.removeEntity(greedButton);
            greedButton = null;
        }
        removeShopKeeper();

        for (Enemy e : new ArrayList<>(enemies)) gameLoop.removeEntity(e);
        enemies.clear();
        pendingSpawns = 0;

        double hpMult = Math.max(0.1, AppContext.getRunModifiers().enemyHpMult);
        double bossHp = (400 + (currentFloor * 250)) * hpMult;

        double bx = gameArea.getWidth()/2 - 40;
        double by = gameArea.getHeight()/2 - 100;
        String bossId = "BOSS_FLOOR_" + currentFloor;

        java.util.function.Consumer<GameEntity> projectileRemover = (ent) -> gameLoop.removeEntity(ent);

        if (currentFloor == 5) {
            bossHp *= 2.0;
            bossNameLabel.setText("THE HARVESTER (FINAL BOSS)");
            bossNameLabel.setStyle("-fx-text-fill: #ff0000; -fx-font-size: 24px; -fx-font-weight: bold; -fx-effect: dropshadow(gaussian, black, 4, 1, 0, 0);");
            activeBoss = new com.layla.entities.FinalBoss(bx, by, bossHp, gameArea, this::getPlayerCenter, (deadBoss) -> handleBossDeath(bossId), (proj) -> gameLoop.addEntity(proj), projectileRemover, bossId);
        } else {
            bossNameLabel.setText("BOSS - FLOOR " + currentFloor);
            bossNameLabel.setStyle("-fx-text-fill: #ffaaaa; -fx-font-weight: bold; -fx-font-size: 18px;");
            activeBoss = new Boss(bx, by, bossHp, gameArea, this::getPlayerCenter, (deadBoss) -> handleBossDeath(bossId), (proj) -> gameLoop.addEntity(proj), projectileRemover, bossId);
        }

        gameLoop.addEntity(activeBoss);
        db.incrementEnemyStatAsync(AppContext.getProfileId(), bossId, DatabaseService.StatType.SEEN);

        if (bossHealthBox != null) {
            bossHealthBox.setVisible(true);
            bossHealthBox.setManaged(true);
        }
    }

    private void handleBossDeath(String bossId) {
        if (activeBoss != null) {
            gameLoop.removeEntity(activeBoss);
            activeBoss = null;
            db.incrementEnemyStatAsync(AppContext.getProfileId(), bossId, DatabaseService.StatType.KILLED);
            achievements.onBossKilled();
        }
        if (bossHealthBox != null) bossHealthBox.setVisible(false);

        int bossScore = (int)(1000 * currentFloor * comboMultiplier);
        this.score += bossScore;

        spawnRewardCoins(gameArea.getWidth()/2, gameArea.getHeight()/2, 50);
        updateHudLabels();

        waveInProgress = false;

        for (Enemy e : new ArrayList<>(enemies)) e.applyDamage(99999);

        spawnRoomRewardPedestal();
        spawnShopKeeper();

        spawnNextFloorButton();
    }

    private void spawnNextFloorButton() {
        if (greedButton != null) gameLoop.removeEntity(greedButton);

        double w = gameArea.getWidth(); if (w <= 0) w = 1200;
        double h = gameArea.getHeight(); if (h <= 0) h = 800;
        double cx = w / 2.0;
        double cy = h / 2.0;

        greedButton = new GreedButton(cx, cy, gameArea, (btn) -> {
            loadNextFloor();
        });
        greedButton.setAsExit();
        gameLoop.addEntity(greedButton);
        checkAndPushInteractive(greedButton); // FIX: Comprobar colisión tras spawnear
    }

    private void loadNextFloor() {
        currentFloor++;
        if (currentFloor > MAX_FLOORS) {
            showVictoryOverlay();
            return;
        }

        currentWave = 0;
        enemies.clear();
        obstacles.clear();

        waveInProgress = false;
        timerStopped = false;
        moneyPenaltyActive = false;

        currentRerollPrice = SHOP_REROLL_BASE_PRICE;
        currentShopOffers = generateShopOffers(SHOP_OFFER_COUNT);

        changeFloorVisuals();
        spawnRoomLayout();
        spawnGreedButton();
        spawnShopKeeper();

        updateHudLabels();

        if(player != null) {
            player.setPosition(gameArea.getWidth()/2 - 10, gameArea.getHeight()/2 + 80);
        }

        AppContext.notifications().showNotification("FLOOR " + currentFloor, "New challenges await!", 3.0);
    }

    private EnemyType pickEnemyTypeForWave() {
        int roll = enemyRng.nextInt(100);
        if (currentFloor == 1) {
            if(roll < 70) return EnemyType.SHOOTER;
            if(roll < 90) return EnemyType.MELEE;
            return EnemyType.TURRET;
        }

        if (roll < 25) return EnemyType.SHOOTER;
        if (roll < 50) return EnemyType.MELEE;
        if (roll < 75) return EnemyType.TANK;
        if (roll < 90) return EnemyType.TURRET;
        return EnemyType.KAMIKAZE;
    }

    private void maybeSpawnPlayer() {
        if (!startGateOpen) return;
        if (player != null || input == null || gameLoop == null) return;
        if (gameArea.getWidth() <= 0 && gameArea.getPrefWidth() <= 0) return;

        player = new Player(input, gameArea, statsService);
        applyBalanceToRuntimePlayer();
        player.setHealth(statsService.getMaxHealth());

        double w = gameArea.getWidth() > 0 ? gameArea.getWidth() : 1200;
        double h = gameArea.getHeight() > 0 ? gameArea.getHeight() : 800;

        player.setPosition(w/2 - 10, h/2 + 100);

        lastPlayerHealth = player.getHealth();

        shootingService = new ShootingService(statsService);
        shootingService.setTargetSupplier(this::getHomingTargets);
        shootingArmed = false;

        hud = new HudView(statsService, player);
        hud.setTranslateX(12); hud.setTranslateY(12);
        overlayLayer.getChildren().add(hud);
        StackPane.setAlignment(hud, Pos.TOP_LEFT);

        itemHud = new ItemHudView(statsService);
        itemHud.refresh();
        overlayLayer.getChildren().add(itemHud);
        StackPane.setAlignment(itemHud, Pos.TOP_RIGHT);
        StackPane.setMargin(itemHud, new Insets(12, 8, 12, 8));

        // ASEGURAR QUE EL HUD DE ITEMS ESTÁ AL FRENTE
        itemHud.toFront();
        hud.toFront();

        gameLoop.addEntity(player);
        if (!gameLoop.isRunning()) gameLoop.start();
    }

    private List<GameEntity> getHomingTargets() {
        List<GameEntity> targets = new ArrayList<>(enemies);
        if (activeBoss != null && !activeBoss.isDead()) targets.add(activeBoss);
        return targets;
    }

    private void addTickerIfNeeded() {
        if (!startGateOpen || gameLoop == null) return;
        if (ticker != null) return;

        ticker = new GameEntity() {
            private final Group view = new Group();
            @Override public void update(double dt) {
                if (!shootingArmed) {
                    shootingArmTimer -= dt;
                    if (shootingArmTimer <= 0.0) shootingArmed = true;
                }

                if (shopCooldown > 0) shopCooldown -= dt;

                if (!paused && !gameOverShown) {
                    updateGreedLogic(dt);

                    if (greedButton != null) greedButton.update(dt);
                    if (shopKeeperEntity != null) shopKeeperEntity.update(dt);

                    scoreTimer += dt;
                    if (scoreTimer >= 1.0) {
                        score = Math.max(0, score - SCORE_PENALTY_PER_SECOND);
                        scoreTimer -= 1.0;
                        updateHudLabels();
                    }

                    if (comboTimer > 0.0) {
                        comboTimer -= dt;
                        if (comboTimer <= 0.0) {
                            comboCount = 0;
                            comboMultiplier = 1.0;
                            updateHudLabels();
                        }
                    }

                    if (activeBoss != null) {
                        activeBoss.update(dt);
                        if (player != null && !player.isDead() && activeBoss.getBounds().intersects(player.getBounds())) {
                            player.setLastHitSource("BOSS_FLOOR_" + currentFloor);
                            player.takeDamage(1.0);
                        }
                    }
                }

                if (shootingService != null && input != null) shootingService.update(dt, input.getMoveVector());
                if (shootingArmed && !paused && player != null && !player.isDead()) tryShootNow();

                if (player != null) {
                    double currentHp = player.getHealth();
                    if (lastPlayerHealth > 0 && currentHp < lastPlayerHealth) {
                        score = Math.max(0, score - SCORE_PENALTY_ON_DAMAGE);
                        comboCount = 0;
                        comboMultiplier = 1.0;
                        comboTimer = 0.0;
                        updateHudLabels();
                    }
                    lastPlayerHealth = currentHp;
                }

                hudRefreshTimer -= dt;
                if (hud != null && hudRefreshTimer <= 0.0) {
                    hud.refresh();
                    updateHudLabels();
                    hudRefreshTimer = 0.1;
                }

                resolveObstacleCollisions(dt);
                applyEnemySeparation(dt);

                if (!gameOverShown && player != null && player.isDead()) showGameOverOverlay();
            }
            @Override public javafx.scene.Node getView() { return view; }
        };
        gameLoop.addEntity(ticker);
        tickerAdded = true;
    }

    private void handleEnemyDeathRewards(Enemy en) {
        if (enemyRng.nextDouble() < 0.3) {
            int amount = enemyRng.nextInt(1, 4);
            if (timerStopped) amount = Math.max(1, amount / 2);
            spawnRewardCoins(en.getCenterX(), en.getCenterY(), amount);
        }

        comboCount++;
        comboTimer = COMBO_MAX_TIME;
        comboMultiplier = Math.min(5.0, 1.0 + (comboCount * 0.1));
        EnemyProfile profile = com.layla.AppContext.balance().profile(en.getType());
        int basePoints = (profile != null) ? profile.score : 10;
        this.score += (int)(basePoints * comboMultiplier);
        updateHudLabels();
    }

    private void spawnRoomLayout() {
        for (GameEntity r : obstacles) {
            if (r instanceof Rock rock) rock.destroy();
            gameLoop.removeEntity(r);
        }
        obstacles.clear();

        int pattern = enemyRng.nextInt(4);
        double w = gameArea.getWidth(); double h = gameArea.getHeight();
        if (w<=0) w=1200; if(h<=0) h=800;
        double cx = w/2; double cy = h/2;

        if (pattern == 1) {
            spawnRockRow(cx - 80, cx + 80, cy, true);
            spawnRockRow(cy - 80, cy + 80, cx, false);
        } else if (pattern == 2) {
            spawnRock(cx - 150, cy - 100); spawnRock(cx + 150, cy - 100);
            spawnRock(cx - 150, cy + 100); spawnRock(cx + 150, cy + 100);
        } else if (pattern == 3) {
            for(int i=0; i<6; i++) {
                double rx = enemyRng.nextDouble(100, w-100);
                double ry = enemyRng.nextDouble(100, h-100);
                if(Math.abs(rx-cx) > 100 || Math.abs(ry-cy) > 100) spawnRock(rx, ry);
            }
        }

        List<GameEntity> interactives = new ArrayList<>();
        if (shopKeeperEntity != null) interactives.add(shopKeeperEntity);
        if (greedButton != null) interactives.add(greedButton);

        for (GameEntity ent : interactives) {
            checkAndPushInteractive(ent);
        }
    }

    private void spawnRock(double x, double y) {
        Rock r = new Rock(x, y, gameArea);
        obstacles.add(r);
        gameLoop.addEntity(r);
    }

    private void spawnRockRow(double start, double end, double fixed, boolean horizontal) {
        double step = Rock.SIZE;
        for (double p = start; p <= end; p += step) {
            if (horizontal) spawnRock(p, fixed);
            else spawnRock(fixed, p);
        }
    }

    private void resetPlainGameArea() {
        if (gameArea == null || root == null) return;
        gameArea.setScaleX(1.0); gameArea.setScaleY(1.0);
        gameArea.setManaged(true); gameArea.setClip(null);

        AnchorPane anchor = null;
        for (var n : root.getChildren()) {
            if (n instanceof AnchorPane ap) { anchor = ap; break; }
        }

        if (anchor != null) {
            if (!anchor.getChildren().contains(gameArea)) anchor.getChildren().add(gameArea);
            AnchorPane.setTopAnchor(gameArea, 72.0);
            AnchorPane.setBottomAnchor(gameArea, 0.0);
            AnchorPane.setLeftAnchor(gameArea, 0.0);
            AnchorPane.setRightAnchor(gameArea, 0.0);
            if (hudBar != null) hudBar.toFront();
        }
        changeFloorVisuals();
    }

    private void changeFloorVisuals() {
        if (gameArea.getParent() instanceof AnchorPane parent) {
            gameArea.setStyle("-fx-background-color: transparent;");

            if (backgroundView == null) {
                backgroundView = new ImageView();
                AnchorPane.setTopAnchor(backgroundView, 72.0);
                AnchorPane.setBottomAnchor(backgroundView, 0.0);
                AnchorPane.setLeftAnchor(backgroundView, 0.0);
                AnchorPane.setRightAnchor(backgroundView, 0.0);
                parent.getChildren().add(0, backgroundView);
            }

            try {
                String bgName = switch(currentFloor) {
                    case 1 -> "basement";
                    case 2 -> "caves";
                    case 3 -> "depths";
                    case 4 -> "womb";
                    default -> "sheol";
                };
                String path = "assets/background/" + bgName + ".png";
                Image img = AssetsManager.loadImage(path);
                if (img == null) {
                    img = AssetsManager.loadImage("assets/background/basement.png");
                    if (img == null) img = new Image(getClass().getResourceAsStream("/assets/background/basement.png"));
                }
                backgroundView.setImage(img);
                backgroundView.setPreserveRatio(false);
                backgroundView.fitWidthProperty().bind(gameArea.widthProperty());
                backgroundView.fitHeightProperty().bind(gameArea.heightProperty());
            } catch (Exception e) {
                String color = switch(currentFloor) {
                    case 1 -> "#2b2010";
                    case 2 -> "#202020";
                    case 3 -> "#10102b";
                    case 4 -> "#4a0000";
                    default -> "#000000";
                };
                gameArea.setStyle("-fx-background-color: " + color + ";");
            }
            backgroundView.toBack();
            if (hudBar != null) hudBar.toFront();
        }
    }

    private void showShopOverlay() {
        paused = true;
        if (gameLoop != null) gameLoop.stop();
        final javafx.scene.Node[] overlayRef = new javafx.scene.Node[1];
        overlayRef[0] = OverlayRouter.showOverlay(overlayLayer, "ui/shop_overlay.fxml", 0.90, controller -> {
            if (controller instanceof ShopOverlayController soc) {
                soc.setStatsService(statsService);
                soc.setCoins(coins);
                soc.setRerollBasePrice(currentRerollPrice);
                soc.setOffers(currentShopOffers);
                soc.setOnCoinsChanged(newCoins -> {
                    coins = Math.max(0, newCoins);
                    updateHudLabels();
                });
                soc.setOnItemsChanged(() -> {
                    score = Math.max(0, score - SCORE_PENALTY_ON_BUY);
                    if (hud != null) hud.refresh();
                    if (itemHud != null) itemHud.refresh();
                    updateHudLabels();
                });

                soc.setOnRerollPriceChanged(newPrice -> {
                    currentRerollPrice = newPrice;
                });

                soc.setOnRerollRequested(c -> {
                    currentShopOffers = generateShopOffers(SHOP_OFFER_COUNT);
                    c.setOffers(currentShopOffers);
                });
                soc.setOnClose(() -> {
                    coins = soc.getCoins();
                    updateHudLabels();
                    OverlayRouter.closeOverlay(overlayLayer, overlayRef[0]);
                    shopOverlay = null;
                    Platform.runLater(() -> {
                        if (gameLoop != null && !gameOverShown) gameLoop.start();
                        paused = false;
                        shopCooldown = 5.0;
                    });
                });
                soc.onShow();
            }
        });
        shopOverlay = overlayRef[0];
    }

    private void spawnRewardCoins(double x, double y, int amount) {
        Coin coin = new Coin(x, y, amount, gameArea, statsService, player, c -> {
            coins += c.getValue();
            updateHudLabels();
            sound.play("coin");
            gameLoop.removeEntity(c);
        });
        gameLoop.addEntity(coin);
    }

    private void spawnRoomRewardPedestal() {
        if (gameLoop == null || gameArea == null) return;
        List<ItemDefinition> availableItems = ItemRegistry.getUnlockedByPool(ItemPoolType.TREASURE, achievements);
        if (availableItems.isEmpty()) return;
        ItemDefinition def = availableItems.get(rewardItemCursor % availableItems.size());
        final ItemId itemId = def.getId();
        rewardItemCursor++;

        double w = gameArea.getWidth(); if (w<=0) w=1200;
        double h = gameArea.getHeight(); if (h<=0) h=800;

        ItemPedestal pedestal = new ItemPedestal(itemId, gameArea, statsService,
            e -> { gameLoop.removeEntity(e); if (itemHud != null) itemHud.refresh(); showItemPickupOverlay(itemId); },
            k -> sound.play(k)
        );
        pedestal.setPosition(w/2, h/2 + 80);
        gameLoop.addEntity(pedestal);

        // FIX: Evitar que el pedestal salga sobre una roca y añadirlo a obstáculos
        checkAndPushInteractive(pedestal);
        obstacles.add(pedestal);
    }

    private void showItemPickupOverlay(ItemId itemId) {
        paused = true;
        if (gameLoop != null) gameLoop.stop();
        final Node[] overlayRef = new Node[1];
        overlayRef[0] = OverlayRouter.showOverlay(overlayLayer, "ui/item_pickup_overlay.fxml", 0.90, controller -> {
            if (controller instanceof ItemPickupOverlayController ipc) {
                ItemDefinition def = ItemRegistry.getDefinition(itemId);
                String name = (def != null ? def.getName() : itemId.name());
                String desc = (def != null ? def.getDescription() : "");
                Image icon = null;
                String path = ItemRegistry.getIconPath(itemId);
                if (path != null) icon = AssetsManager.loadImage(path.startsWith("/") ? path.substring(1) : path);
                ipc.setItem(name, desc, icon);
                ipc.setOnClose(() -> {
                    OverlayRouter.closeOverlay(overlayLayer, overlayRef[0]);
                    Platform.runLater(this::showShopOverlay);
                });
            }
        });
    }

    private List<ShopOffer> generateShopOffers(int count) {
        List<ShopOffer> offers = new ArrayList<>();
        List<ItemDefinition> shopItems = ItemRegistry.getUnlockedByPool(ItemPoolType.SHOP, achievements);
        List<ItemDefinition> treasureItems = ItemRegistry.getUnlockedByPool(ItemPoolType.TREASURE, achievements);
        List<ItemDefinition> pool = new ArrayList<>(shopItems);
        pool.addAll(treasureItems);
        if (pool.isEmpty()) return offers;
        for (int i = 0; i < count; i++) {
            ItemDefinition def = pool.get(enemyRng.nextInt(pool.size()));
            int price = Math.max(5, 10 + Math.max(0, currentWave - 1) * 2 + enemyRng.nextInt(0, 6));
            offers.add(new ShopOffer(def.getId(), price));
        }
        return offers;
    }

    private boolean handleDebugItemHotkeys(KeyCode code) {
        if (code == null) return false;
        ItemId itemId = switch (code) {
            case DIGIT1 -> ItemId.SWIFT_BOOTS;
            case DIGIT2 -> ItemId.GLASS_CANNON;
            case DIGIT3 -> ItemId.TEARS_UP;
            default -> null;
        };
        if (itemId != null) {
            statsService.grantItem(itemId);
            applyBalanceToRuntimePlayer();
            return true;
        }
        return false;
    }

    private boolean isValidSpawn(double ex, double ey, double ew, double eh) {
        double[] center = getPlayerCenter();
        double enemyCx = ex + ew * 0.5;
        double enemyCy = ey + eh * 0.5;
        double dx = center[0] - enemyCx;
        double dy = center[1] - enemyCy;
        return Math.hypot(dx, dy) > 200.0;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(v, max));
    }

    // -------------------------------------------------------------------------
    // MÉTODOS AÑADIDOS / MODIFICADOS
    // -------------------------------------------------------------------------

    private void tryShootNow() {
        if (player == null || shootingService == null || input == null) return;
        double[] aim = input.getAimArrowCardinal();
        if (Math.abs(aim[0]) > 0.01 || Math.abs(aim[1]) > 0.01) {
             shootingService.setAim(aim[0], aim[1]);
             Bounds bounds = player.getBounds();
             double originX = bounds.getCenterX();
             double originY = bounds.getCenterY();
             shootingService.tryShoot(gameArea, gameLoop, originX, originY, player, proj -> {
                 // sound.play("shoot.wav");
             });
        }
    }

    private double[] getPlayerCenter() {
        if (player == null) return new double[]{0, 0};
        Bounds b = player.getBounds();
        return new double[]{ b.getCenterX(), b.getCenterY() };
    }

    private void resolveObstacleCollisions(double dt) {
        if (player != null) {
            for (GameEntity obs : obstacles) {
                 resolveCollision(player, obs);
            }
        }
        for (Enemy e : enemies) {
            for (GameEntity obs : obstacles) {
                resolveCollision(e, obs);
            }
        }
    }

    /**
     * Lógica mejorada de colisión: Separación por profundidad de penetración (Muro sólido).
     * También ignora la tienda.
     */
    private void resolveCollision(GameEntity dynamic, GameEntity staticEnt) {
         // SOLUCIÓN PROBLEMA 2: Ignorar colisión con la tienda para poder entrar
         if (staticEnt == shopKeeperEntity) return;

         if (dynamic.getView() == null || staticEnt.getView() == null) return;

         Bounds d = dynamic.getBounds();
         Bounds s = staticEnt.getBounds();

         // Si no se tocan, no hacemos nada
         if (!d.intersects(s)) return;

         double dx = d.getCenterX() - s.getCenterX();
         double dy = d.getCenterY() - s.getCenterY();

         // Calcular mitad de anchos/altos para saber cuánto solapamiento hay
         double halfWidths = (d.getWidth() / 2.0) + (s.getWidth() / 2.0);
         double halfHeights = (d.getHeight() / 2.0) + (s.getHeight() / 2.0);

         // overlapX = Cuánto se metió en X
         double overlapX = halfWidths - Math.abs(dx);
         // overlapY = Cuánto se metió en Y
         double overlapY = halfHeights - Math.abs(dy);

         if (overlapX > 0 && overlapY > 0) {
             // SOLUCIÓN PROBLEMA 1: Resolver en el eje de menor penetración para evitar "rebote"
             if (overlapX < overlapY) {
                 // Empujar fuera en X exactamente la distancia que se metió
                 double sign = Math.signum(dx);
                 dynamic.getView().setLayoutX(dynamic.getView().getLayoutX() + (overlapX * sign));
             } else {
                 // Empujar fuera en Y exactamente la distancia que se metió
                 double sign = Math.signum(dy);
                 dynamic.getView().setLayoutY(dynamic.getView().getLayoutY() + (overlapY * sign));
             }
         }
    }

    private void applyEnemySeparation(double dt) {
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e1 = enemies.get(i);
            for (int j = i + 1; j < enemies.size(); j++) {
                Enemy e2 = enemies.get(j);

                double dx = e1.getCenterX() - e2.getCenterX();
                double dy = e1.getCenterY() - e2.getCenterY();
                double distSq = dx*dx + dy*dy;

                if (distSq < 900) {
                    double dist = Math.sqrt(distSq);
                    if (dist < 0.1) dist = 0.1;
                    double force = (30.0 - dist) / dist;
                    double pushX = dx * force * dt * 50.0;
                    double pushY = dy * force * dt * 50.0;
                    e1.setPosition(e1.getX() + pushX, e1.getY() + pushY);
                    e2.setPosition(e2.getX() - pushX, e2.getY() - pushY);
                }
            }
        }
    }

    /**
     * Muestra la pantalla de Game Over y permite reiniciar correctamente.
     */
    private void showGameOverOverlay() {
        if (gameOverShown) return;
        gameOverShown = true;
        if (gameLoop != null) gameLoop.stop();

        db.recordRunEndAsync(AppContext.getProfileId(), false, score, currentFloor);

        gameOverOverlay = OverlayRouter.showOverlay(overlayLayer, "ui/game_over.fxml", 0.85, c -> {
            if (c instanceof GameOverController goc) {
                goc.setTitle("GAME OVER");
                goc.setOnRetry(() -> {
                     OverlayRouter.closeOverlay(overlayLayer, gameOverOverlay);
                     gameOverOverlay = null;

                     // SOLUCIÓN PROBLEMA 2: Recarga completa y señal de inicio
                     SceneRouter.goWithFadeKeepSize("ui/game.fxml");
                     SceneRouter.whenControllerIs(GameController.class, GameController::signalGameStart);
                });
                goc.setOnBackToMenu(() -> {
                    OverlayRouter.closeOverlay(overlayLayer, gameOverOverlay);
                    gameOverOverlay = null;
                    backToMenu();
                });
            }
        });
    }

    private void showVictoryOverlay() {
         if (gameOverShown) return;
        gameOverShown = true;
        if (gameLoop != null) gameLoop.stop();

        db.recordRunEndAsync(AppContext.getProfileId(), true, score, currentFloor);

        gameOverOverlay = OverlayRouter.showOverlay(overlayLayer, "ui/game_over.fxml", 0.85, c -> {
            if (c instanceof GameOverController goc) {
                goc.setTitle("VICTORY!");
                 goc.setOnRetry(() -> {
                     OverlayRouter.closeOverlay(overlayLayer, gameOverOverlay);
                     gameOverOverlay = null;

                     // SOLUCIÓN PROBLEMA 2: Recarga completa y señal de inicio
                     SceneRouter.goWithFadeKeepSize("ui/game.fxml");
                     SceneRouter.whenControllerIs(GameController.class, GameController::signalGameStart);
                });
                goc.setOnBackToMenu(() -> {
                    OverlayRouter.closeOverlay(overlayLayer, gameOverOverlay);
                    gameOverOverlay = null;
                    backToMenu();
                });
            }
        });
    }
}
