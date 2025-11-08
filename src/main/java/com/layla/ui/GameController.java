package com.layla.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.layla.core.AssetsManager;
import com.layla.core.GameEntity;
import com.layla.core.GameLoop;
import com.layla.core.InputService;
import com.layla.entities.DummyEntity;
import com.layla.entities.Enemy;
import com.layla.entities.Player;
import com.layla.entities.Projectile;
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
    private boolean demoEntitiesAdded = false;
    private boolean playerSpawnListenerAdded = false;

    // ---------- Input / servicios ----------
    private InputService input;
    private final StatsService statsService = com.layla.AppContext.stats();
    private ShootingService shootingService;
    private final SoundService sound = new SoundService();

    // Disparo (bias WASD sobre flechas)
    private double bias = 0.2;

    // Ticker auxiliar
    private boolean tickerAdded = false;

    // Enemigos
    private final List<Enemy> enemies = new ArrayList<>();
    private boolean enemiesSpawned = false;

    // HUD lateral (estadísticas)
    private HudView hud;
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
                    // 1) Refresca el HUD inmediatamente
                    if (hud != null) hud.refresh();
                    // 2) Aplica cambios de balance (HP máx/clamp) al jugador activo
                    applyBalanceToRuntimePlayer();
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
        double prevMax = player.getMaxHealth();
        player.setMaxHealth(bal.maxHp);

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
        if (healthLabel != null) healthLabel.setText("HP");
    }

    private void startHudTimerIfNeeded() {
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
                KeyCode c = e.getCode();
                if (c == KeyCode.UP || c == KeyCode.DOWN || c == KeyCode.LEFT || c == KeyCode.RIGHT) {
                    tryShootNow();
                }
            });

            gameArea.requestFocus();
            scene.setOnMouseClicked(e -> gameArea.requestFocus());

            maybeSpawnPlayer();
            addTickerIfNeeded();
        });

        // HUD
        startHudTimerIfNeeded();
        updateHudLabels();

        // Dummies de demo (opcional)
        if (!demoEntitiesAdded) {
            if (gameArea.getWidth() > 0) {
                var d1 = new DummyEntity(50, 80,  90, gameArea);
                var d2 = new DummyEntity(200,120, 60, gameArea);
                gameLoop.addEntity(d1);
                gameLoop.addEntity(d2);
                if (!gameLoop.isRunning()) gameLoop.start();
                demoEntitiesAdded = true;
            } else {
                gameArea.widthProperty().addListener((obs, ow, nw) -> {
                    if (!demoEntitiesAdded && nw.doubleValue() > 0) {
                        var d1 = new DummyEntity(50, 80,  90, gameArea);
                        var d2 = new DummyEntity(200,120, 60, gameArea);
                        gameLoop.addEntity(d1);
                        gameLoop.addEntity(d2);
                        if (!gameLoop.isRunning()) gameLoop.start();
                        demoEntitiesAdded = true;
                    }
                });
            }
        }
    }

    @Override
    public void onExit() {
        if (gameLoop != null && gameLoop.isRunning()) gameLoop.stop();

        // Cerrar overlays si estaban abiertos
        if (gameOverOverlay != null) {
            OverlayRouter.closeOverlay(overlayLayer, gameOverOverlay);
            gameOverOverlay = null;
        }
        if (pauseOverlay != null) {
            OverlayRouter.closeOverlay(overlayLayer, pauseOverlay);
            pauseOverlay = null;
        }
        gameOverShown = false;
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

        shootingService = null;
        stopHudTimer();
        settingsOpen = false;
        if (input != null) input.detach();
        tickerAdded = false;
    }

    // ==================== API PÚBLICA BÁSICA ====================
    public void signalGameStart() {
        if (gameStarted) return;
        gameStarted = true;
        elapsedSeconds = 0;
        score = 500;
        updateHudLabels();
        startHudTimerIfNeeded();
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
        if (player != null || input == null || gameLoop == null) return;

        double width = gameArea.getWidth();
        double height = gameArea.getHeight();
        if (width <= 0 || height <= 0) return;

        player = new Player(input, gameArea, statsService);

        // Aplica balance global de vida
        var bal = com.layla.AppContext.balance();
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
    private void addTickerIfNeeded() {
        if (tickerAdded || gameLoop == null) return;

        GameEntity ticker = new GameEntity() {
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

        shootingService.tryShoot(
            gameArea,
            gameLoop,
            px, py,
            player,                     // ← owner
            (Projectile p) -> sound.play("shot")
        );
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

    private void spawnEnemies(int count) {
        if (gameLoop == null || gameArea == null) return;
        double width = gameArea.getWidth();
        double height = gameArea.getHeight();
        if (width <= 0.0 || height <= 0.0) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        var bal = com.layla.AppContext.balance();

        for (int i = 0; i < count; i++) {
            // velocidad alrededor de la media (±15)
            double speed = bal.enemySpeedAvg - 15.0 + rng.nextDouble(30.0);

            Enemy enemy = new Enemy(
                gameArea,
                this::getPlayerCenter,
                speed,
                bal.enemyBaseHp,
                e -> {
                    gameLoop.removeEntity(e);
                    enemies.remove(e);
                    // bonus configurable
                    int bonus = (int) Math.round(Math.pow(bal.enemyBaseHp, 0.2) * bal.enemyScoreK);
                    score = Math.max(0, score + bonus);
                    updateHudLabels();
                },
                ge -> gameLoop.addEntity(ge),
                k -> sound.play(k)
            );

            double enemyWidth = enemy.getWidth();
            double enemyHeight = enemy.getHeight();
            double maxX = Math.max(0.0, width - enemyWidth);
            double maxY = Math.max(0.0, height - enemyHeight);

            double posX = 0.0;
            double posY = 0.0;
            boolean placed = false;

            for (int attempt = 0; attempt < 20; attempt++) {
                double candidateX = maxX <= 0.0 ? 0.0 : rng.nextDouble(0.0, maxX);
                double candidateY = maxY <= 0.0 ? 0.0 : rng.nextDouble(0.0, maxY);
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
