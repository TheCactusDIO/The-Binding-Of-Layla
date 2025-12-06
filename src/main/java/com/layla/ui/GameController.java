package com.layla.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.layla.core.AssetsManager;
import com.layla.core.GameEntity;
import com.layla.core.GameLoop;
import com.layla.core.InputService;
import com.layla.entities.Coin;
import com.layla.entities.ItemPedestal;
import com.layla.entities.Player;
import com.layla.entities.Projectile;
import com.layla.items.ItemDefinition;
import com.layla.items.ItemId;
import com.layla.items.ItemRegistry;
import com.layla.model.Enemy;
import com.layla.model.EnemyProfile;
import com.layla.model.EnemyType;
import com.layla.model.PlayerStatId;
import com.layla.services.ShootingService;
import com.layla.services.SoundService;
import com.layla.services.StatsService;
import com.layla.ui.ShopOverlayController.ShopOffer;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class GameController implements ViewLifecycle {

    // ---------- ÁREA PRINCIPAL ----------
    @FXML private Pane gameArea;

    // ---------- HUD (barra superior) ----------
    @FXML private HBox hudBar;
    @FXML private Label scoreLabel;
    @FXML private Label coinLabel;
    @FXML private Label floorLabel;
    @FXML private Label healthLabel;

    // ---------- ROOTS / OVERLAYS ----------
    @FXML private StackPane root;
    @FXML private StackPane overlayLayer;
    private javafx.scene.Node pauseOverlay;
    private javafx.scene.Node gameOverOverlay;
    private javafx.scene.Node shopOverlay;

    // ---------- MÚSICA ----------
    private String currentTrack = null;
    private boolean musicStarted = false;

    // ---------- HUD: timer y score ----------
    private int score = 500;
    private int coins = Math.max(0, com.layla.AppContext.balance().startCoins);
    private Label timeLabel;

    // ---------- Lógica de Oleadas (Survival) ----------
    private double waveTimer = 0.0;        // Tiempo restante de la oleada
    private double spawnTimer = 0.0;       // Tiempo para el próximo grupo de enemigos
    private boolean waveActive = false;    // Si la oleada está en curso
    private int currentWave = 1;

    // ---------- Estado ----------
    private boolean gameStarted = false;
    private boolean paused = false;
    private boolean settingsOpen = false;
    private boolean gameOverShown = false;

    // ---------- Game loop / entidades ----------
    private GameLoop gameLoop;
    private Player player;
    private boolean playerSpawnListenerAdded = false;

    // --- Start gate (juego no arranca hasta que se abra) ---
    private boolean startGateOpen = false;

    // ---------- Input / servicios ----------
    private InputService input;
    private final StatsService statsService = com.layla.AppContext.stats();
    private ShootingService shootingService;
    private final SoundService sound = new SoundService();

    // Disparo (bias WASD sobre flechas)
    private double bias = 0.2;

    // Ticker auxiliar
    private boolean tickerAdded = false;
    private GameEntity ticker; // Ticker invisible que vive en el GameLoop

    // Enemigos
    private final List<Enemy> enemies = new ArrayList<>();
    private final ThreadLocalRandom enemyRng = ThreadLocalRandom.current();
    private static final double SEPARATION_EPS = 1e-5;
    private static final double MAX_SEPARATION_STEP = 6.0;
    private static final int SHOP_OFFER_COUNT = 3;
    private static final int SHOP_REROLL_BASE_PRICE = 1;

    // Recompensas por sala
    private int rewardItemCursor = 0;

    // HUD lateral (estadísticas)
    private HudView hud;
    private ItemHudView itemHud;
    private double hudRefreshTimer = 0.0;

    // Anti-spam de disparo tras spawn del player
    private boolean shootingArmed = false;
    private double shootingArmTimer = 0.6; // s

    // ===================== PAUSA =====================
    @FXML
    private void onPausePressed() {
        if (gameOverShown) return; // No permitir pausa en Game Over
        if (pauseOverlay != null || paused) return;
        paused = true;

        Platform.runLater(() -> {
            if (gameLoop != null && gameLoop.isRunning()) gameLoop.stop();
        });

        pauseOverlay = OverlayRouter.showOverlay(overlayLayer, "ui/pause_overlay.fxml", 0.50, controller -> {
            if (controller instanceof PauseOverlayController poc) {
                poc.setOnResume(this::resumeFromPause);
                poc.setOnBackToMenu(() -> {
                    OverlayRouter.closeOverlay(overlayLayer, pauseOverlay);
                    pauseOverlay = null;
                    Platform.runLater(() -> {
                        if (gameLoop != null && gameLoop.isRunning()) gameLoop.stop();
                        paused = false;
                        settingsOpen = false;
                        backToMenu();
                    });
                });
                poc.setOnSettings(() -> {
                    final javafx.scene.Node[] settingsNode = new javafx.scene.Node[1];
                    settingsOpen = true;
                    java.util.function.Consumer<Object> settingsConsumer = c -> {
                        if (c instanceof SettingsController sc) {
                            sc.setStatsService(statsService);
                            sc.setOverlayHost(overlayLayer);
                            sc.setInitialCoins(coins);

                            sc.setOnCoinsChanged(newCoins -> {
                                coins = Math.max(0, newCoins);
                                updateHudLabels();
                            });

                            sc.setOnClose(() -> {
                                OverlayRouter.closeOverlay(overlayLayer, settingsNode[0]);
                                settingsOpen = false;
                            });
                        }
                    };
                    settingsNode[0] = OverlayRouter.showOverlay(
                        overlayLayer, "ui/settings.fxml", 0.90, settingsConsumer
                    );
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
        settingsOpen = false;
    }

    /** Aplica en runtime el balance de vida al player actual (modelo de 1 sola vida). */
    private void applyBalanceToRuntimePlayer() {
        if (player == null) return;
        var bal = com.layla.AppContext.balance();
        statsService.setBaseStat(PlayerStatId.MAX_HEALTH, bal.maxHp);
        player.setMaxHealth(statsService.getMaxHealth());
    }

    // ===================== HUD & HELPERS =====================
    private static String formatTime(double seconds) {
        int s = (int) Math.ceil(seconds);
        return String.format("%02d", s);
    }

    private void updateHudLabels() {
        if (scoreLabel != null) scoreLabel.setText("Score: " + score);
        if (coinLabel  != null) coinLabel.setText("Coins: " + coins);
        if (floorLabel != null) floorLabel.setText("Oleada: " + currentWave);
        if (timeLabel  != null) {
            if (waveActive) {
                timeLabel.setText("Time: " + formatTime(waveTimer));
                timeLabel.setStyle("-fx-text-fill: white;");
                if (waveTimer <= 5.0) {
                    timeLabel.setStyle("-fx-text-fill: #ff5555; -fx-effect: dropshadow(gaussian, red, 10, 0.5, 0, 0);");
                }
            } else {
                timeLabel.setText(gameOverShown ? "DEAD" : "CLEAR");
                timeLabel.setStyle("-fx-text-fill: #55ff55;");
            }
        }
        if (healthLabel != null) {
            healthLabel.setVisible(false);
            healthLabel.setManaged(false);
        }
    }

    @FXML
    private void initialize() {
        if (hudBar != null) {
            if (timeLabel == null) {
                timeLabel = new Label();
                timeLabel.getStyleClass().add("hud-timer");
                timeLabel.setStyle("-fx-font-size: 24px;"); // Más grande para el survival
            }
            if (!hudBar.getChildren().contains(timeLabel)) {
                int insertAt = hudBar.getChildren().indexOf(floorLabel);
                if (insertAt < 0) insertAt = hudBar.getChildren().size();
                hudBar.getChildren().add(insertAt + 1, timeLabel);
            }
        }
        updateHudLabels();
    }

    /** Aplica el nuevo perfil al vuelo a TODOS los enemigos existentes. */
    private void applyBalanceToRuntimeEnemies() {
        var bal = com.layla.AppContext.balance();
        for (Enemy e : enemies) {
            EnemyProfile profile = bal.profile(e.getType());
            if (profile != null) {
                e.setMaxHealth(profile.baseHp * e.getHpMultiplier());
            }
        }
    }

    // ==================== CICLO DE VIDA (router) ====================
    @Override
    public void onEnter() {
        resetPlainGameArea();

        // Fade-in HUD
        if (hudBar != null) {
            hudBar.setOpacity(0.0);
            var ft = new FadeTransition(Duration.millis(400), hudBar);
            ft.setFromValue(0.0);
            ft.setToValue(1.0);
            ft.play();
        }

        // Aplica estilos HUD a la Scene
        Platform.runLater(() -> {
            var sc = gameArea.getScene();
            if (sc != null) {
                sc.getStylesheets().remove(UIStyles.hud());
                sc.getStylesheets().add(UIStyles.hud());
                if (!sc.getStylesheets().contains(UIStyles.global()))
                    sc.getStylesheets().add(UIStyles.global());
                if (!sc.getStylesheets().contains(UIStyles.game()))
                    sc.getStylesheets().add(UIStyles.game());
            }
        });

        // ESC para pausa
        Platform.runLater(() -> {
            var scene = gameArea.getScene();
            if (scene == null) return;
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    if (gameOverShown) return;
                    if (settingsOpen) return;
                    if (pauseOverlay != null || paused) resumeFromPause(); else onPausePressed();
                }
            });
            gameArea.setFocusTraversable(true);
            gameArea.requestFocus();
        });

        // GameLoop
        if (gameLoop == null) gameLoop = new GameLoop(gameArea);

        // Listeners de tamaño
        if (!playerSpawnListenerAdded) {
            playerSpawnListenerAdded = true;
            gameArea.widthProperty().addListener((obs, ow, nw) -> {
                maybeSpawnPlayer();
            });
            gameArea.heightProperty().addListener((obs, oh, nh) -> {
                maybeSpawnPlayer();
            });
        }

        maybeSpawnPlayer();

        // Input
        Platform.runLater(() -> {
            Scene scene = gameArea.getScene();
            if (scene == null) return;
            if (input == null) input = new InputService();
            input.attach(scene);

            scene.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
                if (handleDebugItemHotkeys(e.getCode())) {
                    e.consume();
                    return;
                }
                KeyCode c = e.getCode();
                if (c == KeyCode.UP || c == KeyCode.DOWN || c == KeyCode.LEFT || c == KeyCode.RIGHT) {
                    tryShootNow();
                }
            });

            gameArea.requestFocus();
            scene.setOnMouseClicked(e -> gameArea.requestFocus());
        });
    }

    @Override
    public void onExit() {
        if (gameLoop != null && gameLoop.isRunning()) gameLoop.stop();

        // Cerrar overlays
        if (gameOverOverlay != null) { OverlayRouter.closeOverlay(overlayLayer, gameOverOverlay); gameOverOverlay = null; }
        if (shopOverlay != null) { OverlayRouter.closeOverlay(overlayLayer, shopOverlay); shopOverlay = null; }
        if (pauseOverlay != null) { OverlayRouter.closeOverlay(overlayLayer, pauseOverlay); pauseOverlay = null; }

        if (ticker != null && gameLoop != null) { gameLoop.removeEntity(ticker); ticker = null; }

        gameOverShown = false;
        tickerAdded = false;
        paused = false;
        waveActive = false;

        // Limpiar entidades
        if (player != null && gameLoop != null) { gameLoop.removeEntity(player); player = null; }
        if (!enemies.isEmpty() && gameLoop != null) {
            for (Enemy enemy : new ArrayList<>(enemies)) gameLoop.removeEntity(enemy);
        }
        enemies.clear();

        if (hud != null) {
            if (overlayLayer != null) overlayLayer.getChildren().remove(hud);
            else if (gameArea != null) gameArea.getChildren().remove(hud);
            hud = null;
        }
        if (itemHud != null) {
            if (overlayLayer != null) overlayLayer.getChildren().remove(itemHud);
            else if (gameArea != null) gameArea.getChildren().remove(itemHud);
            itemHud = null;
        }

        startGateOpen = false;
        shootingService = null;
        settingsOpen = false;
        if (input != null) input.detach();
    }

    // ==================== API PÚBLICA BÁSICA ====================
    public void signalGameStart() {
        if (startGateOpen) return;
        startGateOpen = true;

        gameStarted = true;
        score = 500;
        coins = Math.max(0, com.layla.AppContext.balance().startCoins);
        currentWave = 1;

        rewardItemCursor = 0;
        waveActive = false;

        prepareWave(currentWave);

        updateHudLabels();
        maybeSpawnPlayer();
        addTickerIfNeeded();
        startFloorMusicIfNeeded();
    }

    private void prepareWave(int wave) {
        waveTimer = 20.0 + (wave * 5.0);
        spawnTimer = 1.0; // Primer spawn rápido
        waveActive = true;
    }

    private void resetHUD() {
        score = 500;
        coins = Math.max(0, com.layla.AppContext.balance().startCoins);
        updateHudLabels();
    }

    public void backToMenu() { SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml"); }
    public StackPane getOverlayLayer() { return overlayLayer; }
    public StatsService getStatsService() { return statsService; }

    public void startFloorMusicIfNeeded() {
        if (!musicStarted) {
            String[] floor1Tracks = { "basement1.mp3", "basement2.mp3", "basement3.mp3" };
            int idx = java.util.concurrent.ThreadLocalRandom.current().nextInt(floor1Tracks.length);
            currentTrack = floor1Tracks[idx];
            try {
                AssetsManager.playMusic(currentTrack, true);
                musicStarted = true;
            } catch (Exception ex) {
                System.err.println("[GameController] Could not play music: " + ex.getMessage());
            }
        }
    }

    // ==================== SPAWN PLAYER ====================
    private void maybeSpawnPlayer() {
        if (!startGateOpen) return;
        if (player != null || input == null || gameLoop == null) return;

        double width = gameArea.getWidth();
        double height = gameArea.getHeight();
        if (width <= 0 || height <= 0) return;

        player = new Player(input, gameArea, statsService);

        var bal = com.layla.AppContext.balance();
        statsService.setBaseStat(PlayerStatId.MAX_HEALTH, bal.maxHp);
        double baseHp = statsService.getMaxHealth();
        player.setMaxHealth(baseHp);
        player.setHealth(baseHp);

        shootingService = new ShootingService(statsService);
        shootingArmed = false;
        shootingArmTimer = 0.6;

        hud = new HudView(statsService, player);
        hud.setTranslateX(12);
        hud.setTranslateY(12);
        if (overlayLayer != null) {
            overlayLayer.getChildren().add(hud);
            StackPane.setAlignment(hud, Pos.TOP_LEFT);
        } else {
            gameArea.getChildren().add(hud);
        }

        itemHud = new ItemHudView(statsService);
        itemHud.refresh();
        if (overlayLayer != null) {
            overlayLayer.getChildren().add(itemHud);
            StackPane.setAlignment(itemHud, Pos.TOP_RIGHT);
            StackPane.setMargin(itemHud, new Insets(12, 8, 12, 8));
        } else {
            gameArea.getChildren().add(itemHud);
        }

        double startX = Math.max(0.0, (width - player.getWidth()) / 2.0);
        double startY = Math.max(0.0, (height - player.getHeight()) / 2.0);
        player.setPosition(startX, startY);
        gameLoop.addEntity(player);

        if (!gameLoop.isRunning()) gameLoop.start();
    }

    // ==================== TICKER & GAME LOGIC ====================
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

                // --- 1. Lógica de Oleada (Survival) ---
                if (waveActive && !paused && !gameOverShown) {
                    waveTimer -= dt;
                    updateHudLabels();

                    if (waveTimer <= 0.0) {
                        endWave();
                    } else {
                        spawnTimer -= dt;
                        if (spawnTimer <= 0.0) {
                            spawnEnemyBatch();
                            spawnTimer = Math.max(0.5, 2.5 - (currentWave * 0.15));
                        }
                    }
                }

                // --- 2. Disparo ---
                if (shootingService != null && input != null) {
                    shootingService.update(dt, input.getMoveVector());
                }

                if (shootingArmed && !paused && player != null && !player.isDead() && input != null && shootingService != null) {
                    double[] ar = input.getAimArrowCardinal();
                    if (ar[0] != 0 || ar[1] != 0) {
                        double[] mv = input.getMoveVector();
                        double fx = (1.0 - bias) * ar[0] + bias * mv[0];
                        double fy = (1.0 - bias) * ar[1] + bias * mv[1];
                        shootingService.setAim(fx, fy);

                        double px = player.getView().getLayoutX() + player.getWidth() / 2.0;
                        double py = player.getView().getLayoutY() + player.getHeight() / 2.0;

                        shootingService.tryShoot(
                            gameArea,
                            gameLoop,
                            px, py,
                            player,
                            (Projectile p) -> sound.play("shot")
                        );
                    }
                }

                // --- 3. HUD y Utilidades ---
                hudRefreshTimer -= dt;
                if (hud != null && hudRefreshTimer <= 0.0) {
                    hud.refresh();
                    hudRefreshTimer = 0.1;
                }

                applyEnemySeparation(dt);

                if (!gameOverShown && player != null && player.isDead()) {
                    showGameOverOverlay();
                }
            }

            @Override public javafx.scene.Node getView() { return view; }
            @Override public void onCollision(GameEntity other) {}
        };

        gameLoop.addEntity(ticker);
        tickerAdded = true;
    }

    private void tryShootNow() {
        if (!shootingArmed || paused) return;
        if (player == null || player.isDead() || input == null || shootingService == null) return;

        double[] ar = input.getAimArrowCardinal();
        if (ar[0] == 0 && ar[1] == 0) return;

        double[] mv = input.getMoveVector();
        double fx = (1.0 - bias) * ar[0] + bias * mv[0];
        double fy = (1.0 - bias) * ar[1] + bias * mv[1];
        shootingService.setAim(fx, fy);

        double px = player.getView().getLayoutX() + player.getWidth() / 2.0;
        double py = player.getView().getLayoutY() + player.getHeight() / 2.0;

        shootingService.tryShoot(gameArea, gameLoop, px, py, player, (Projectile p) -> sound.play("shot"));
    }

    private boolean handleDebugItemHotkeys(KeyCode code) {
        if (code == null) return false;
        ItemId itemId = switch (code) {
            case DIGIT1 -> ItemId.SWIFT_BOOTS;
            case DIGIT2 -> ItemId.GLASS_CANNON;
            case DIGIT3 -> ItemId.TEARS_UP;
            case DIGIT4 -> ItemId.RANGE_UP;
            case DIGIT5 -> ItemId.SHOT_SPEED_UP;
            default -> null;
        };
        if (itemId == null) return false;

        boolean granted = statsService.grantItem(itemId);
        if (granted) {
            applyBalanceToRuntimePlayer();
            if (hud != null) hud.refresh();
            if (itemHud != null) itemHud.refresh();
            var def = com.layla.items.ItemRegistry.getDefinition(itemId);
            if (player != null && gameLoop != null && def != null) {
                double cx = player.getView().getLayoutX() + player.getWidth() * 0.5;
                double cy = player.getView().getLayoutY() - 10.0;
                gameLoop.addEntity(new FloatingTextEntity(def.getName(), cx, cy, e -> gameLoop.removeEntity(e)));
            }
        }
        return granted;
    }

    // ==================== ENEMIGOS & OLEADAS ====================

    private void spawnEnemyBatch() {
        if (gameLoop == null || gameArea == null) return;
        double width = gameArea.getWidth();
        double height = gameArea.getHeight();
        if (width <= 0.0 || height <= 0.0) return;

        int batchSize = 1 + (currentWave / 3);

        double waveIndex = Math.max(1.0, currentWave);
        double hpMul = 1.0 + 0.15 * (waveIndex - 1.0);
        double speedMul = 1.0 + 0.05 * (waveIndex - 1.0);
        double dmgMul = 1.0 + 0.10 * (waveIndex - 1.0);

        for (int i = 0; i < batchSize; i++) {
            createAndSpawnEnemy(hpMul, speedMul, dmgMul, width, height);
        }
    }

    private void createAndSpawnEnemy(double hpMul, double speedMul, double dmgMul, double width, double height) {
        EnemyType type = pickTypeByWeight();

        Enemy enemy = new Enemy(
            type,
            gameArea,
            this::getPlayerCenter,
            e -> {
                gameLoop.removeEntity(e);
                if (e instanceof Enemy en) {
                    enemies.remove(en);
                    handleEnemyDeathRewards(en);
                }
            },
            ge -> gameLoop.addEntity(ge),
            k -> sound.play(k),
            hpMul,
            speedMul,
            dmgMul
        );

        placeEnemySafely(enemy, width, height);

        enemies.add(enemy);
        gameLoop.addEntity(enemy);
    }

    private void handleEnemyDeathRewards(Enemy en) {
        var bal = com.layla.AppContext.balance();
        EnemyProfile profile = bal.profile(en.getType());

        int bonus = 0;
        int coinGain = 0;
        if (profile != null) {
            bonus = profile.score;
            if (profile.score > 0) {
                coinGain = Math.max(1, profile.score / 10);
            }
        }

        score = Math.max(0, score + bonus);

        if (coinGain > 0) {
            double cx = en.getCenterX();
            double cy = en.getCenterY();
            Coin coin = new Coin(cx, cy, coinGain, gameArea, statsService, player,
                c -> {
                    coins += c.getValue();
                    updateHudLabels();
                    sound.play("item");
                    gameLoop.removeEntity(c);

                    // Floating text
                    double tx = c.getView().getLayoutX();
                    double ty = c.getView().getLayoutY() - 10;
                    gameLoop.addEntity(new FloatingTextEntity("+" + c.getValue() + "¢", tx, ty, x -> gameLoop.removeEntity(x)));
                }
            );
            gameLoop.addEntity(coin);
        }

        updateHudLabels();
    }

    private void placeEnemySafely(Enemy enemy, double width, double height) {
        double enemyWidth = enemy.getWidth();
        double enemyHeight = enemy.getHeight();
        double maxX = Math.max(0.0, width - enemyWidth);
        double maxY = Math.max(0.0, height - enemyHeight);
        double posX = 0.0, posY = 0.0;
        boolean placed = false;

        for (int attempt = 0; attempt < 15; attempt++) {
            double candidateX = enemyRng.nextDouble(0.0, maxX);
            double candidateY = enemyRng.nextDouble(0.0, maxY);
            if (isValidSpawn(candidateX, candidateY, enemyWidth, enemyHeight)) {
                posX = candidateX;
                posY = candidateY;
                placed = true;
                break;
            }
        }

        if (!placed) { posX = 10; posY = 10; }
        enemy.setPosition(posX, posY);
    }

    private void endWave() {
        waveActive = false;
        List<Enemy> toKill = new ArrayList<>(enemies);
        for (Enemy e : toKill) {
            e.applyDamage(99999);
        }
        spawnRoomRewardPedestal();
    }

    private double[] getPlayerCenter() {
        if (player != null) {
            return new double[] {
                player.getView().getLayoutX() + player.getWidth() * 0.5,
                player.getView().getLayoutY() + player.getHeight() * 0.5
            };
        }
        return new double[] { 0, 0 };
    }

    private boolean isValidSpawn(double ex, double ey, double ew, double eh) {
        double[] center = getPlayerCenter();
        double enemyCx = ex + ew * 0.5;
        double enemyCy = ey + eh * 0.5;
        double dx = center[0] - enemyCx;
        double dy = center[1] - enemyCy;
        return Math.hypot(dx, dy) > 250.0;
    }

    private EnemyType pickTypeByWeight() {
        Map<EnemyType, Integer> weights = com.layla.AppContext.balance().spawnWeights();
        if (weights.isEmpty()) return EnemyType.SHOOTER;
        int total = weights.values().stream().filter(w -> w > 0).mapToInt(Integer::intValue).sum();
        if (total <= 0) return EnemyType.SHOOTER;
        int roll = enemyRng.nextInt(total);
        int acc = 0;
        for (var entry : weights.entrySet()) {
            int w = Math.max(0, entry.getValue());
            if (w == 0) continue;
            acc += w;
            if (roll < acc) return entry.getKey();
        }
        return EnemyType.SHOOTER;
    }

    private void spawnRoomRewardPedestal() {
        if (gameLoop == null || gameArea == null) return;
        double width = gameArea.getWidth();
        double height = gameArea.getHeight();
        if (width <= 0.0 || height <= 0.0) return;

        ItemId[] allItems = ItemId.values();
        final ItemId itemId = allItems[rewardItemCursor % allItems.length];
        rewardItemCursor++;

        ItemPedestal pedestal = new ItemPedestal(
            itemId,
            gameArea,
            statsService,
            e -> {
                gameLoop.removeEntity(e);
                if (itemHud != null) itemHud.refresh();
                showItemPickupOverlay(itemId);
            },
            k -> sound.play(k)
        );

        pedestal.setPosition((width - pedestal.getWidth()) * 0.5, (height - pedestal.getHeight()) * 0.5);
        gameLoop.addEntity(pedestal);
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

    private void showShopOverlay() {
        paused = true;
        if (gameLoop != null) gameLoop.stop();

        final javafx.scene.Node[] overlayRef = new javafx.scene.Node[1];
        overlayRef[0] = OverlayRouter.showOverlay(overlayLayer, "ui/shop_overlay.fxml", 0.90, controller -> {
            if (controller instanceof ShopOverlayController soc) {
                soc.setStatsService(statsService);
                soc.setCoins(coins);
                soc.setRerollBasePrice(SHOP_REROLL_BASE_PRICE);
                soc.setOffers(generateShopOffers(SHOP_OFFER_COUNT));

                soc.setOnCoinsChanged(newCoins -> {
                    coins = Math.max(0, newCoins);
                    updateHudLabels();
                });
                soc.setOnItemsChanged(() -> {
                    if (hud != null) hud.refresh();
                    if (itemHud != null) itemHud.refresh();
                });
                soc.setOnRerollRequested(c -> c.setOffers(generateShopOffers(SHOP_OFFER_COUNT)));

                soc.setOnClose(() -> {
                    coins = soc.getCoins();
                    updateHudLabels();
                    OverlayRouter.closeOverlay(overlayLayer, overlayRef[0]);
                    shopOverlay = null;
                    Platform.runLater(() -> {
                        startNextWave();
                        if (gameLoop != null && !gameOverShown) gameLoop.start();
                        paused = false;
                    });
                });
                soc.onShow();
            }
        });
        shopOverlay = overlayRef[0];
    }

    private List<ShopOffer> generateShopOffers(int count) {
        List<ShopOffer> offers = new ArrayList<>();
        ItemId[] ids = ItemId.values();
        int basePrice = Math.max(5, 10 + Math.max(0, currentWave - 1) * 2);
        for (int i = 0; i < count; i++) {
            ItemId itemId = ids[enemyRng.nextInt(ids.length)];
            int price = Math.max(5, basePrice + enemyRng.nextInt(0, 6));
            offers.add(new ShopOffer(itemId, price));
        }
        return offers;
    }

    private void startNextWave() {
        if (gameOverShown || !startGateOpen) return;
        currentWave++;
        prepareWave(currentWave);
        updateHudLabels();
    }

    private void resetPlainGameArea() {
        if (gameArea == null || root == null) return;
        gameArea.setScaleX(1.0); gameArea.setScaleY(1.0);
        gameArea.setManaged(true); gameArea.setClip(null);

        AnchorPane anchor = null;
        for (var n : root.getChildren()) if (n instanceof AnchorPane ap) { anchor = ap; break; }

        if (anchor != null && gameArea.getParent() != anchor) {
            var p = gameArea.getParent();
            if (p instanceof Pane pp) pp.getChildren().remove(gameArea);
            if (!anchor.getChildren().contains(gameArea)) anchor.getChildren().add(gameArea);
            AnchorPane.setTopAnchor(gameArea, 72.0);
            AnchorPane.setBottomAnchor(gameArea, 0.0);
            AnchorPane.setLeftAnchor(gameArea, 0.0);
            AnchorPane.setRightAnchor(gameArea, 0.0);
        }
        gameArea.setStyle("-fx-background-color: #111111;");
    }

    private void applyEnemySeparation(double dt) {
        if (gameArea == null || enemies.isEmpty()) return;
        double dtScale = dt > 0.0 ? Math.max(0.75, Math.min(1.25, dt * 60.0)) : 1.0;
        double maxStep = MAX_SEPARATION_STEP * dtScale;
        double areaW = gameArea.getWidth();
        double areaH = gameArea.getHeight();

        for (int i = 0; i < enemies.size(); i++) {
            Enemy a = enemies.get(i);
            if (a.isDead()) continue;

            // Vs otros enemigos
            for (int j = i + 1; j < enemies.size(); j++) {
                Enemy b = enemies.get(j);
                if (b.isDead()) continue;

                double dx = b.getCenterX() - a.getCenterX();
                double dy = b.getCenterY() - a.getCenterY();
                double distSq = dx*dx + dy*dy;
                double radSum = a.getCollisionRadius() + b.getCollisionRadius();
                if (distSq < radSum*radSum) {
                    double dist = Math.sqrt(distSq);
                    if (dist < SEPARATION_EPS) dist = SEPARATION_EPS;
                    double overlap = radSum - dist;
                    double push = Math.min(overlap * 0.5, maxStep);
                    double nx = dx / dist;
                    double ny = dy / dist;

                    a.setPosition(
                        clamp(a.getView().getLayoutX() - nx * push, 0, areaW - a.getWidth()),
                        clamp(a.getView().getLayoutY() - ny * push, 0, areaH - a.getHeight())
                    );
                    b.setPosition(
                        clamp(b.getView().getLayoutX() + nx * push, 0, areaW - b.getWidth()),
                        clamp(b.getView().getLayoutY() + ny * push, 0, areaH - b.getHeight())
                    );
                }
            }
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(v, max));
    }

    private void showGameOverOverlay() {
        if (gameOverShown) return;
        gameOverShown = true;
        sound.play("player_death");
        if (gameLoop != null) gameLoop.stop();

        gameOverOverlay = OverlayRouter.showOverlay(overlayLayer, "ui/game_over.fxml", 0.75, controller -> {
            if (controller instanceof GameOverController goc) {
                goc.setOnRetry(() -> {
                    OverlayRouter.closeOverlay(overlayLayer, gameOverOverlay);
                    gameOverOverlay = null;
                    restartGame();
                });
                goc.setOnBackToMenu(() -> {
                    OverlayRouter.closeOverlay(overlayLayer, gameOverOverlay);
                    gameOverOverlay = null;
                    backToMenu();
                });
            }
        });
    }

    private void restartGame() {
        if (pauseOverlay != null) { OverlayRouter.closeOverlay(overlayLayer, pauseOverlay); pauseOverlay = null; }
        if (shopOverlay != null) { OverlayRouter.closeOverlay(overlayLayer, shopOverlay); shopOverlay = null; }
        paused = false;

        if (gameLoop != null) gameLoop.clearEntities();
        if (ticker != null) ticker = null;

        enemies.clear();
        currentWave = 1;
        score = 500;
        coins = Math.max(0, com.layla.AppContext.balance().startCoins);
        statsService.clearItems();

        gameOverShown = false;
        shootingArmed = false;
        tickerAdded = false;
        rewardItemCursor = 0;

        updateHudLabels();

        if (hud != null) {
            if (overlayLayer != null) overlayLayer.getChildren().remove(hud);
            else gameArea.getChildren().remove(hud);
            hud = null;
        }
        if (itemHud != null) {
            if (overlayLayer != null) overlayLayer.getChildren().remove(itemHud);
            else gameArea.getChildren().remove(itemHud);
            itemHud = null;
        }

        player = null;
        maybeSpawnPlayer();
        addTickerIfNeeded();
        if (gameLoop != null) gameLoop.start();

        prepareWave(currentWave);
    }
}
