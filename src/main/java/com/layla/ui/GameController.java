package com.layla.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.layla.core.AssetsManager;
import com.layla.core.GameEntity;
import com.layla.core.GameLoop;
import com.layla.core.InputService;
import com.layla.entities.Player;
import com.layla.entities.Projectile;
import com.layla.items.ItemId;
import com.layla.model.Enemy;
import com.layla.model.EnemyProfile;
import com.layla.model.EnemyType;
import com.layla.model.PlayerStatId;
import com.layla.services.ShootingService;
import com.layla.services.SoundService;
import com.layla.services.StatsService;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class GameController implements ViewLifecycle {

    // ---------- ÁREA PRINCIPAL ----------
    @FXML private Pane gameArea;

    // ---------- HUD (barra superior) ----------
    @FXML private HBox hudBar;
    @FXML private Label scoreLabel;
    @FXML private Label floorLabel;
    @FXML private Label healthLabel;

    // ---------- ROOTS / OVERLAYS ----------
    @FXML private StackPane root;
    @FXML private StackPane overlayLayer;
    private javafx.scene.Node pauseOverlay;
    private javafx.scene.Node gameOverOverlay;

    // ---------- MÚSICA ----------
    private String currentTrack = null;
    private boolean musicStarted = false;

    // ---------- HUD: timer y score ----------
    private Timeline hudTimer;        // Reloj MM:SS
    private long elapsedSeconds = 0;
    private int score = 500;
    private Label timeLabel;

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
    private GameEntity ticker; // Ticker invisible que vive en el GameLoop (cooldowns, HUD, game over, etc.)

    // Enemigos
    private final List<Enemy> enemies = new ArrayList<>();
    private boolean enemiesSpawned = false;
    private final ThreadLocalRandom enemyRng = ThreadLocalRandom.current();

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
            pauseHudTimer();
        });

        pauseOverlay = OverlayRouter.showOverlay(overlayLayer, "ui/pause_overlay.fxml", 0.50, controller -> {
            if (controller instanceof PauseOverlayController poc) {
                poc.setOnResume(this::resumeFromPause);
                poc.setOnBackToMenu(() -> {
                    OverlayRouter.closeOverlay(overlayLayer, pauseOverlay);
                    pauseOverlay = null;
                    Platform.runLater(() -> {
                        if (gameLoop != null && gameLoop.isRunning()) gameLoop.stop();
                        stopHudTimer();
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
                            sc.setOverlayHost(overlayLayer); // host para abrir stats_panel.fxml encima del juego
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
            resumeHudTimer();
            paused = false;
        });
        settingsOpen = false;
    }

    /** Abre el Stats Panel sobre el overlayLayer del juego, con live-sync al HUD. */
    private void openStatsPanel() {
        final javafx.scene.Node[] statsNode = new javafx.scene.Node[1];
        statsNode[0] = OverlayRouter.showOverlay(overlayLayer, "ui/stats_panel.fxml", 0.90, controller -> {
            if (controller instanceof StatsPanelController sp) {
                sp.setStatsService(com.layla.AppContext.stats());
                sp.setOnClose(() -> OverlayRouter.closeOverlay(overlayLayer, statsNode[0]));
                sp.setOnStatsChanged(() -> {
                    // 1) Refresca HUD al instante
                    if (hud != null) hud.refresh();
                    // 2) Aplica balance en caliente al jugador
                    applyBalanceToRuntimePlayer();
                    // 3) Aplica balance en caliente a TODOS los enemigos activos
                    applyBalanceToRuntimeEnemies();
                });
                sp.onShow();
            }
        });
    }

    /** Aplica en runtime el balance (maxHp/startHp) al player actual sin romper la partida. */
    private void applyBalanceToRuntimePlayer() {
        if (player == null) return;
        var bal = com.layla.AppContext.balance();

        // Ajusta la vida máxima al vuelo y clampa la actual si es necesario.
        statsService.setBaseStat(PlayerStatId.MAX_HEALTH, bal.maxHp);
        player.setMaxHealth(statsService.getMaxHealth());

        // Si el maxHp baja por debajo de la salud actual, setMaxHealth ya la clampa.
        // Si quieres que al subir maxHp NO cambie la actual, no hagas nada más.

        // Opcional: si quieres que al tocar startHp en el panel, se aplique SOLO al respawn,
        // no modificamos la actual aquí. Si prefieres que se aplique en caliente, descomenta:
        // player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }


    // ===================== HUD TIMER =====================
    private void pauseHudTimer()  { if (hudTimer != null) hudTimer.pause(); }
    private void resumeHudTimer() { if (hudTimer != null) hudTimer.play();  }
    private void stopHudTimer()   { if (hudTimer != null) { hudTimer.stop(); hudTimer = null; } }

    private static String formatMMSS(long totalSeconds) {
        long shown = Math.max(0, totalSeconds);
        long mm = shown / 60;
        long ss = shown % 60;
        return String.format("%02d:%02d", mm, ss);
    }

    private void updateHudLabels() {
        if (scoreLabel != null) scoreLabel.setText("Score: " + score);
        if (floorLabel != null) floorLabel.setText("Floor: 1");
        if (timeLabel  != null) timeLabel.setText("Time: " + formatMMSS(elapsedSeconds));
        if (healthLabel != null) {
            healthLabel.setVisible(false);
            healthLabel.setManaged(false); // para que no reserve espacio en el HBox
        }
    }

    private void startHudTimerIfNeeded() {
        if (!startGateOpen) return;
        if (hudTimer != null) return;
        elapsedSeconds = 0;
        score = 500;
        updateHudLabels();
        hudTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            elapsedSeconds += 1;
            if (score > 0) score = Math.max(0, score - 1);
            updateHudLabels();
        }));
        hudTimer.setCycleCount(Timeline.INDEFINITE);
        hudTimer.play();
    }

    // ===================== INICIALIZACIÓN FXML =====================
    @FXML
    private void initialize() {
        resetHUD();
        if (hudBar != null) {
            if (timeLabel == null) {
                timeLabel = new Label();
                timeLabel.getStyleClass().add("hud-timer");
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
                e.setMaxHealth(profile.baseHp);
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
                System.out.println("[HUD] Scene styles applied: " + sc.getStylesheets());
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

        // Listeners de tamaño (spawn player + enemigos al tener medidas)
        if (!playerSpawnListenerAdded) {
            playerSpawnListenerAdded = true;
            gameArea.widthProperty().addListener((obs, ow, nw) -> {
                maybeSpawnPlayer();
                trySpawnInitialEnemies();
            });
            gameArea.heightProperty().addListener((obs, oh, nh) -> {
                maybeSpawnPlayer();
                trySpawnInitialEnemies();
            });
        }

        // Intento inicial
        maybeSpawnPlayer();
        trySpawnInitialEnemies();

        // Input
        Platform.runLater(() -> {
            Scene scene = gameArea.getScene();
            if (scene == null) return;
            if (input == null) input = new InputService();
            input.attach(scene);

            // Disparo inmediato con flechas
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

        // Cerrar overlays si estaban abiertos
        if (gameOverOverlay != null) {
            OverlayRouter.closeOverlay(overlayLayer, gameOverOverlay);
            gameOverOverlay = null;
        }
        if (ticker != null && gameLoop != null) {
            gameLoop.removeEntity(ticker);
            ticker = null;
        }
        if (pauseOverlay != null) {
            OverlayRouter.closeOverlay(overlayLayer, pauseOverlay);
            pauseOverlay = null;
        }
        gameOverShown = false;
        tickerAdded = false;
        paused = false;

        // Quitar player
        if (player != null && gameLoop != null) {
            gameLoop.removeEntity(player);
            player = null;
        }

        // Quitar enemigos
        if (!enemies.isEmpty() && gameLoop != null) {
            for (Enemy enemy : new ArrayList<>(enemies)) {
                gameLoop.removeEntity(enemy);
            }
        }
        enemies.clear();
        enemiesSpawned = false;

        // Quitar HUD lateral
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
        stopHudTimer();
        settingsOpen = false;
        if (input != null) input.detach();
        tickerAdded = false;
    }

    // ==================== API PÚBLICA BÁSICA ====================
    public void signalGameStart() {
        if (startGateOpen) return; // ya arrancado
        startGateOpen = true;

        gameStarted = true;
        elapsedSeconds = 0;
        score = 500;

        // Arranques que antes hacías en onEnter
        startHudTimerIfNeeded();
        updateHudLabels();

        maybeSpawnPlayer();         // ahora sí puede spawnear
        addTickerIfNeeded();        // engancha ticker
        trySpawnInitialEnemies();   // primer batch

        startFloorMusicIfNeeded();  // música de piso
    }

    public void setHUD(int score, int floor, int health) {
        if (scoreLabel != null) scoreLabel.setText("Score: " + score);
        if (floorLabel != null) floorLabel.setText("Floor: " + floor);
        if (healthLabel != null) healthLabel.setText("HP: " + health);
    }

    private void resetHUD() {
        elapsedSeconds = 0;
        score = 500;
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

        // Aplica balance global de vida
        var bal = com.layla.AppContext.balance();
        statsService.setBaseStat(PlayerStatId.MAX_HEALTH, bal.maxHp);
        player.setMaxHealth(bal.maxHp);
        player.setHealth(bal.startHp);

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
        itemHud.setTranslateX(-12);
        itemHud.setTranslateY(12);
        itemHud.refresh();
        if (overlayLayer != null) {
            overlayLayer.getChildren().add(itemHud);
            StackPane.setAlignment(itemHud, Pos.TOP_RIGHT);
        } else {
            gameArea.getChildren().add(itemHud);
        }

        double startX = Math.max(0.0, (width - player.getWidth()) / 2.0);
        double startY = Math.max(0.0, (height - player.getHeight()) / 2.0);
        player.setPosition(startX, startY);
        gameLoop.addEntity(player);

        if (!gameLoop.isRunning()) gameLoop.start();

        trySpawnInitialEnemies();
        Platform.runLater(() -> { enemiesSpawned = false; trySpawnInitialEnemies(); });
    }

    // ==================== SHOOTING (solo flechas) ====================
    /** Ticker invisible: cooldown flechas, refresco HUD, watchdog de spawn y GameOver. */
        /** Ticker invisible: cooldown flechas, refresco HUD, watchdog de spawn y GameOver. */
    private void addTickerIfNeeded() {
        if (!startGateOpen || gameLoop == null) return;
        if (ticker != null) return; // ya existe un ticker registrado

        ticker = new GameEntity() {
            private final Group view = new Group(); // invisible
            private double spawnRetryTimer = 0.75;
            private boolean spawnRetried = false;

            @Override public void update(double dt) {
                if (!shootingArmed) {
                    shootingArmTimer -= dt;
                    if (shootingArmTimer <= 0.0) shootingArmed = true;
                }

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
                            player,                     // ← owner
                            (Projectile p) -> sound.play("shot")
                        );
                    }
                }

                hudRefreshTimer -= dt;
                if (hud != null && hudRefreshTimer <= 0.0) {
                    hud.refresh();
                    hudRefreshTimer = 0.1;
                }

                if (!gameOverShown && player != null && player.isDead()) {
                    showGameOverOverlay();
                    return;
                }

                if (!enemiesSpawned && !spawnRetried) {
                    spawnRetryTimer -= dt;
                    if (spawnRetryTimer <= 0.0) {
                        trySpawnInitialEnemies();
                        spawnRetried = true;
                    }
                }
            }

            @Override public javafx.scene.Node getView() { return view; }
            @Override public void onCollision(GameEntity other) { /* no-op */ }
        };

        gameLoop.addEntity(ticker);
        tickerAdded = true; // puedes mantenerlo si quieres para debugging
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

        shootingService.tryShoot(
            gameArea,
            gameLoop,
            px, py,
            player,                     // ← owner
            (Projectile p) -> sound.play("shot")
        );
    }

    /** Debug hotkeys so we can spawn passive items quickly. */
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
            System.out.println("[DEBUG] Passive item granted: " + itemId);

            // 🔍 LOG DE STATS EFECTIVAS TRAS APLICAR EL ITEM
            System.out.printf(
                "[DEBUG] Stats now -> moveSpeed=%.2f, fireRate=%.2f, projSpeed=%.2f, projRange=%.2f, projDamage=%.2f, maxHp=%.2f%n",
                statsService.getMoveSpeed(),
                statsService.getFireRate(),
                statsService.getProjectileSpeed(),
                statsService.getProjectileRange(),
                statsService.getProjectileDamage(),
                statsService.getMaxHealth()
            );

            applyBalanceToRuntimePlayer();
            if (hud != null) hud.refresh();
            if (itemHud != null) itemHud.refresh();
        } else {
            System.out.println("[DEBUG] Item already owned (unique): " + itemId);
        }
        return granted;
    }


    // ==================== ENEMIGOS ====================
    private double[] getPlayerCenter() {
        double px, py;
        if (player != null) {
            px = player.getView().getLayoutX() + player.getWidth() * 0.5;
            py = player.getView().getLayoutY() + player.getHeight() * 0.5;
        } else if (gameArea != null) {
            px = gameArea.getWidth() * 0.5;
            py = gameArea.getHeight() * 0.5;
        } else {
            px = 0.0;
            py = 0.0;
        }
        return new double[] { px, py };
    }

    private boolean isValidSpawn(double ex, double ey, double ew, double eh) {
        double[] center = getPlayerCenter();
        double enemyCx = ex + ew * 0.5;
        double enemyCy = ey + eh * 0.5;
        double dx = center[0] - enemyCx;
        double dy = center[1] - enemyCy;
        return Math.hypot(dx, dy) > 50.0;
    }

    private EnemyType pickTypeByWeight() {
        Map<EnemyType, Integer> weights = com.layla.AppContext.balance().spawnWeights();
        if (weights.isEmpty()) return EnemyType.SHOOTER;
        int total = 0;
        for (Integer w : weights.values()) {
            if (w != null && w > 0) total += w;
        }
        if (total <= 0) return EnemyType.SHOOTER;
        int roll = enemyRng.nextInt(total);
        int acc = 0;
        for (var entry : weights.entrySet()) {
            int weight = Math.max(0, entry.getValue());
            if (weight == 0) continue;
            acc += weight;
            if (roll < acc) {
                return entry.getKey();
            }
        }
        return EnemyType.SHOOTER;
    }

   private void spawnEnemies(int count) {
        if (gameLoop == null || gameArea == null) return;
        double width = gameArea.getWidth();
        double height = gameArea.getHeight();
        if (width <= 0.0 || height <= 0.0) return;

        for (int i = 0; i < count; i++) {
            EnemyType type = pickTypeByWeight();

            Enemy enemy = new Enemy(
                type,
                gameArea,
                this::getPlayerCenter,
                e -> { // onRemove
                    gameLoop.removeEntity(e);
                    if (e instanceof Enemy en) {
                        enemies.remove(en);

                        // ✅ NUEVO: score por perfil de enemigo
                        var bal = com.layla.AppContext.balance();
                        EnemyProfile profile = bal.profile(en.getType());

                        int bonus = 0;
                        if (profile != null) {
                            bonus = profile.score;
                        }

                        score = Math.max(0, score + bonus);
                        updateHudLabels();

                        if (bonus != 0) {
                            double cx = en.getView().getLayoutX() + en.getWidth() * 0.5;
                            double cy = en.getView().getLayoutY() + en.getHeight() * 0.5;
                            var ft = new FloatingTextEntity("+" + bonus, cx, cy, x -> gameLoop.removeEntity(x));
                            gameLoop.addEntity(ft);
                        }
                    } else {
                        enemies.removeIf(x -> x == e);
                    }
                },
                ge -> gameLoop.addEntity(ge), // onSpawn (proyectiles enemigos)
                k -> sound.play(k)            // sfx
            );

            // ... resto igual
            double enemyWidth = enemy.getWidth();
            double enemyHeight = enemy.getHeight();
            double maxX = Math.max(0.0, width - enemyWidth);
            double maxY = Math.max(0.0, height - enemyHeight);

            double posX = 0.0;
            double posY = 0.0;
            boolean placed = false;

            for (int attempt = 0; attempt < 20; attempt++) {
                double candidateX = maxX <= 0.0 ? 0.0 : enemyRng.nextDouble(0.0, maxX);
                double candidateY = maxY <= 0.0 ? 0.0 : enemyRng.nextDouble(0.0, maxY);
                if (isValidSpawn(candidateX, candidateY, enemyWidth, enemyHeight)) {
                    posX = candidateX;
                    posY = candidateY;
                    placed = true;
                    break;
                }
            }

            if (!placed) {
                posX = Math.max(0.0, Math.min(maxX, width * 0.5 - enemyWidth * 0.5));
                posY = Math.max(0.0, Math.min(maxY, height * 0.5 - enemyHeight * 0.5));
            }

            enemy.setPosition(posX, posY);
            enemies.add(enemy);
            gameLoop.addEntity(enemy);
        }
    }

    private void trySpawnInitialEnemies() {
        if (!startGateOpen) return;
        if (enemiesSpawned) return;
        if (player == null || gameArea == null) return;
        if (gameArea.getWidth() <= 0.0 || gameArea.getHeight() <= 0.0) return;
        spawnEnemies(5);
        enemiesSpawned = true;
    }

    // ==================== GAME OVER ====================
    private void showGameOverOverlay() {
        if (gameOverShown) return;
        gameOverShown = true;
        sound.play("player_death");

        if (gameLoop != null && gameLoop.isRunning()) gameLoop.stop();
        pauseHudTimer();

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
        if (pauseOverlay != null) {
            OverlayRouter.closeOverlay(overlayLayer, pauseOverlay);
            pauseOverlay = null;
        }
        paused = false;

        if (gameLoop != null) {
            if (player != null) gameLoop.removeEntity(player);
            for (Enemy e : new ArrayList<>(enemies)) gameLoop.removeEntity(e);
        }
        if (ticker != null) {
                gameLoop.removeEntity(ticker);
                ticker = null;
        }

        enemies.clear();
        enemiesSpawned = false;

        tickerAdded = false;
        shootingArmed = false;
        gameOverShown = false;

        elapsedSeconds = 0;
        score = 500;
        updateHudLabels();
        stopHudTimer();
        startHudTimerIfNeeded();

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

        statsService.clearItems();
        if (itemHud != null) itemHud.refresh();

        player = null;
        maybeSpawnPlayer();
        addTickerIfNeeded();

        if (gameLoop != null && !gameLoop.isRunning()) gameLoop.start();
    }

    // ==================== LAYOUT PLANO ====================
    private void resetPlainGameArea() {
        if (gameArea == null || root == null) return;

        gameArea.setScaleX(1.0);
        gameArea.setScaleY(1.0);
        gameArea.setManaged(true);
        gameArea.setClip(null);

        AnchorPane anchor = null;
        for (var n : root.getChildren()) {
            if (n instanceof AnchorPane ap) { anchor = ap; break; }
        }
        if (anchor != null && gameArea.getParent() != anchor) {
            var p = gameArea.getParent();
            if (p instanceof Pane pp) pp.getChildren().remove(gameArea);
            if (p instanceof Group gg) gg.getChildren().remove(gameArea);
            if (!anchor.getChildren().contains(gameArea)) anchor.getChildren().add(gameArea);
            AnchorPane.setTopAnchor(gameArea, 72.0);
            AnchorPane.setLeftAnchor(gameArea, 0.0);
            AnchorPane.setRightAnchor(gameArea, 0.0);
            AnchorPane.setBottomAnchor(gameArea, 0.0);
        }

        gameArea.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        gameArea.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        gameArea.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

        gameArea.setStyle("-fx-background-color: #111111;");
    }
}
