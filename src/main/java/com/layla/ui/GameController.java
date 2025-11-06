// Issue 6 – Bucle del juego (AnimationTimer)
// Archivo: src/main/java/com/layla/ui/GameController.java

package com.layla.ui;

import com.layla.core.AssetsManager;
import com.layla.core.GameEntity;
import com.layla.core.GameLoop;
import com.layla.core.InputService;
import com.layla.entities.DummyEntity;
import com.layla.entities.Player;
import com.layla.entities.Projectile;
import com.layla.services.ShootingService;
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
    @FXML private Pane gameArea; // fx:id="gameArea"

    // ---------- HUD ----------
    @FXML private HBox hudBar;
    @FXML private Label scoreLabel;
    @FXML private Label floorLabel;
    @FXML private Label healthLabel;

    // ---------- ROOTS / OVERLAYS ----------
    @FXML private StackPane root;
    @FXML private StackPane overlayLayer;
    private javafx.scene.Node pauseOverlay;

    // ---------- MÚSICA ----------
    private String currentTrack = null;
    private boolean musicStarted = false;

    // ===== HUD: timer y score =====
    private Timeline hudTimer;        // <-- Reloj MM:SS
    private long elapsedSeconds = 0;
    private int score = 500;
    private Label timeLabel;

    // Estado
    private boolean gameStarted = false;
    private boolean paused = false;

    // Game loop
    private GameLoop gameLoop;
    private Player player;
    private boolean demoEntitiesAdded = false;
    private boolean playerSpawnListenerAdded = false;

    // Settings desde pausa
    private boolean settingsOpen = false;

    // Input
    private InputService input;
    private final StatsService statsService = new StatsService();

    // Disparo
    private ShootingService shootingService;
    private double bias = 0.35;        // cuánto influye el movimiento en el tiro
    private boolean tickerAdded = false;

    // HUD lateral (stats)
    private HudView hud;
    private double hudRefreshTimer = 0.0; // <-- REFRESCO HUD de stats

    // ===================== CONTROLES PAUSA =====================
    @FXML
    private void onPausePressed() {
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
                            sc.setOnClose(() -> {
                                OverlayRouter.closeOverlay(overlayLayer, settingsNode[0]);
                                settingsOpen = false;
                            });
                        }
                    };
                    settingsNode[0] = OverlayRouter.showOverlay(overlayLayer, "ui/settings.fxml", 0.90, settingsConsumer);
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

    private void pauseHudTimer() { if (hudTimer != null) hudTimer.pause(); }
    private void resumeHudTimer() { if (hudTimer != null) hudTimer.play(); }
    private void stopHudTimer() { if (hudTimer != null) { hudTimer.stop(); hudTimer = null; } }

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
        if (healthLabel != null) healthLabel.setText("HP: 100");
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
        // Layout plano: gameArea tal cual bajo el HUD
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
                    if (settingsOpen) return;
                    if (pauseOverlay != null || paused) resumeFromPause(); else onPausePressed();
                }
            });
            gameArea.setFocusTraversable(true);
            gameArea.requestFocus();
        });

        // GameLoop
        if (gameLoop == null) gameLoop = new GameLoop(gameArea);

        if (!playerSpawnListenerAdded) {
            playerSpawnListenerAdded = true;
            gameArea.widthProperty().addListener((obs, ow, nw) -> maybeSpawnPlayer());
            gameArea.heightProperty().addListener((obs, oh, nh) -> maybeSpawnPlayer());
        }
        maybeSpawnPlayer();

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
                    tryShootNow();  // flechas (cardinal + bias WASD)
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

        // Dummies de demo
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
            maybeSpawnPlayer();
        }
    }

    @Override
    public void onExit() {
        if (gameLoop != null && gameLoop.isRunning()) gameLoop.stop();
        if (player != null && gameLoop != null) {
            gameLoop.removeEntity(player);
            player = null;
        }
        stopHudTimer();
        settingsOpen = false;
        if (input != null) input.detach();
    }

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

    private void maybeSpawnPlayer() {
        if (player != null || input == null || gameLoop == null) return;
        double width = gameArea.getWidth();
        double height = gameArea.getHeight();
        if (width <= 0 || height <= 0) return;

        player = new Player(input, gameArea, statsService);
        // Servicio de disparo (lee siempre de StatsService)
        shootingService = new ShootingService(statsService);

        // HUD lateral
        hud = new HudView(statsService, player);
        hud.setTranslateX(12);
        hud.setTranslateY(12);
        if (overlayLayer != null) {
            overlayLayer.getChildren().add(hud);
            StackPane.setAlignment(hud, Pos.TOP_LEFT);
        } else {
            // fallback por si no hay overlayLayer
            gameArea.getChildren().add(hud);
        }

        double startX = Math.max(0.0, (width - player.getWidth()) / 2.0);
        double startY = Math.max(0.0, (height - player.getHeight()) / 2.0);
        player.setPosition(startX, startY);
        gameLoop.addEntity(player);

        if (!gameLoop.isRunning()) gameLoop.start();
    }

    // ==================== SHOOTING (solo flechas) ====================

    /** Ticker invisible: cooldown flechas + refresco HUD. */
    private void addTickerIfNeeded() {
        if (tickerAdded || gameLoop == null) return;

        GameEntity ticker = new GameEntity() {
            private final Group view = new Group(); // invisible

            @Override public void update(double dt) {
                // Actualiza cooldown interno y aim basado en movimiento
                if (shootingService != null && input != null) {
                    shootingService.update(dt, input.getMoveVector());
                }
                // Disparo con flechas (cardinal + bias por WASD)
                if (player != null && input != null && shootingService != null) {
                    double[] ar = input.getAimArrowCardinal();
                    if (ar[0] != 0 || ar[1] != 0) {
                        // Bias estilo Isaac: flecha + 20% del WASD si hay
                        double[] mv = input.getMoveVector();
                        double fx = ar[0], fy = ar[1];
                        if ((mv[0] != 0 || mv[1] != 0)) {
                            fx = 0.8 * ar[0] + 0.2 * mv[0];
                            fy = 0.8 * ar[1] + 0.2 * mv[1];
                        }
                        shootingService.setAim(fx, fy);
                        // Centro del player como origen (ShootingService ya compensa el radio del proyectil)
                        double px = player.getView().getLayoutX() + player.getWidth() / 2.0;
                        double py = player.getView().getLayoutY() + player.getHeight() / 2.0;
                        shootingService.tryShoot(gameArea, gameLoop, px, py, (Projectile p) -> {});
                    }
                }

                // Refresco del HUD de stats (cada 100ms aprox)
                hudRefreshTimer -= dt;
                if (hud != null && hudRefreshTimer <= 0.0) {
                    hud.refresh();
                    hudRefreshTimer = 0.1;
                }
            }
            @Override public javafx.scene.Node getView() { return view; }
            @Override public void onCollision(GameEntity other) { /* no-op */ }
        };

        gameLoop.addEntity(ticker);
        tickerAdded = true;
    }

    // Disparo inmediato (si mantienes esta acción fuera del loop)
    private void tryShootNow() {
        if (player == null || input == null || shootingService == null) return;
        double[] ar = input.getAimArrowCardinal();
        if (ar[0] == 0 && ar[1] == 0) return;
        double[] mv = input.getMoveVector();
        double fx = 0.8 * ar[0] + 0.2 * mv[0];
        double fy = 0.8 * ar[1] + 0.2 * mv[1];
        shootingService.setAim(fx, fy);
        double px = player.getView().getLayoutX() + player.getWidth() / 2.0;
        double py = player.getView().getLayoutY() + player.getHeight() / 2.0;
        shootingService.tryShoot(gameArea, gameLoop, px, py, (Projectile p) -> {});
    }

    // ==================== LAYOUT PLANO (sin viewport) ====================
    /** Garantiza layout plano: gameArea bajo HUD, sin clips/escalas. */
    private void resetPlainGameArea() {
        if (gameArea == null || root == null) return;

        // Quitar posibles restos de envoltorios previos
        gameArea.setScaleX(1.0);
        gameArea.setScaleY(1.0);
        gameArea.setManaged(true);
        gameArea.setClip(null);

        // Reinsertar en el AnchorPane del FXML (primer hijo del root)
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

        // Fondo por si el CSS no cargó
        gameArea.setStyle("-fx-background-color: #111111;");
    }
}
