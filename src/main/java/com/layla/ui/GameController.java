// Issue 6 – Bucle del juego (AnimationTimer)
// Archivo: src/main/java/com/layla/ui/GameController.java
// Propósito: Controlador principal de la escena de juego. Integra GameLoop,
// gestión de pausa (overlay), HUD, entrada de teclado y música del piso.
// Notas:
// - Este archivo asume que existen: ViewLifecycle, OverlayRouter, PauseOverlayController,
//   SceneRouter, SettingsController y AssetsManager en tu proyecto.
// - El GameLoop añade/quita nodos en el hilo de JavaFX y gestiona colisiones AABB.
// - Java 21.

package com.layla.ui;

import com.layla.core.AssetsManager;
import com.layla.core.GameLoop;
import com.layla.core.InputService;
import com.layla.core.VideoSettings;
import com.layla.core.GameEntity;          // <- para el ticker
import com.layla.entities.DummyEntity;
import com.layla.entities.Player;
import com.layla.entities.Projectile;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import static java.lang.Math.hypot;
import static java.lang.Math.max;

public class GameController implements ViewLifecycle {

    // ---------- ÁREA PRINCIPAL (root de render del juego) ----------
    @FXML private Pane gameArea; // Debe existir en game.fxml con fx:id="gameArea"

    // ---------- HUD ----------
    @FXML private HBox hudBar;
    @FXML private Label scoreLabel;
    @FXML private Label floorLabel;
    @FXML private Label healthLabel;

    // ---------- ROOTS / OVERLAYS ----------
    @FXML private StackPane root;          // root de la escena de juego
    @FXML private StackPane overlayLayer;  // capa superior para overlays (pausa, etc.)
    private javafx.scene.Node pauseOverlay;

    // ---------- MÚSICA ----------
    private String currentTrack = null;
    private boolean musicStarted = false;

    // ===== HUD: timer y score =====
    private Timeline hudTimer;
    private long elapsedSeconds = 0;
    private int score = 500;
    private Label timeLabel;

    // Viewport virtual configurable
    private StackPane viewportLayer;
    private Group worldGroup;
    private boolean viewportConfigured = false;

    // Estado
    private boolean gameStarted = false;
    private boolean paused = false;

    // Game loop
    private GameLoop gameLoop;
    private Player player;
    private boolean demoEntitiesAdded = false;
    private boolean playerSpawnListenerAdded = false;

    // Settings desde pausa: evitar “doble ESC”
    private boolean settingsOpen = false;

    // (Gate reservado)
    @SuppressWarnings("unused")
    private static volatile boolean INTRO_GATE = false;
    public static void setIntroGate(boolean v) { INTRO_GATE = v; }

    // Input
    private InputService input;

    // === SHOOTING: temporizadores y bias ===
    private double shootTimer = 0.0;            // decrece cada frame
    private double bias = 0.35;                 // cuánto influye el movimiento en el tiro
    private boolean tickerAdded = false;        // para añadir el ticker una sola vez

    // ===================== CONTROLES PAUSA =====================
    @FXML
    private void onPausePressed() {
        System.out.println("[GameController] onPausePressed() called");
        if (pauseOverlay != null || paused) return; // ya en pausa o abriéndose

        paused = true; // marca primero para evitar reentradas

        // Pausar loop y timer en FX
        Platform.runLater(() -> {
            if (gameLoop != null && gameLoop.isRunning()) {
                gameLoop.stop();
                System.out.println("[GameController] GameLoop.stop() solicitado; running=" + gameLoop.isRunning());
            } else {
                System.out.println("[GameController] GameLoop ya estaba parado o null");
            }
            pauseHudTimer();
        });

        // Abrimos overlay de pausa (con oscurecimiento leve 0.50)
        pauseOverlay = OverlayRouter.showOverlay(overlayLayer, "ui/pause_overlay.fxml", 0.50, controller -> {
            if (controller instanceof PauseOverlayController poc) {
                // Reanudar
                poc.setOnResume(this::resumeFromPause);

                // Volver a menú
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

                // Abrir Settings dentro de pausa (con fondo más fuerte 0.90)
                poc.setOnSettings(() -> {
                    final javafx.scene.Node[] settingsNode = new javafx.scene.Node[1];
                    settingsOpen = true;

                    java.util.function.Consumer<Object> settingsConsumer = new java.util.function.Consumer<>() {
                        @Override public void accept(Object c) {
                            if (c instanceof SettingsController sc) {
                                sc.setOnClose(() -> {
                                    OverlayRouter.closeOverlay(overlayLayer, settingsNode[0]);
                                    settingsOpen = false; // volvemos a permitir ESC de pausa
                                });
                            }
                        }
                    };

                    settingsNode[0] = OverlayRouter.showOverlay(
                            overlayLayer,
                            "ui/settings.fxml",
                            0.90,
                            settingsConsumer
                    );
                });
            }
        });

        System.out.println("[GameController] Pause overlay shown");
    }

    // ===================== HELPERS PAUSA + HUD TIMER =====================

    private void resumeFromPause() {
        if (pauseOverlay != null) {
            OverlayRouter.closeOverlay(overlayLayer, pauseOverlay);
            pauseOverlay = null;
        }
        Platform.runLater(() -> {
            if (gameLoop != null && !gameLoop.isRunning()) {
                gameLoop.start();
                System.out.println("[GameController] GameLoop.start() tras Resume; running=" + gameLoop.isRunning());
            }
            resumeHudTimer();
            paused = false;
        });
        settingsOpen = false;
        System.out.println("[GameController] Resume via ESC/Resume button");
    }

    private void pauseHudTimer() {
        if (hudTimer != null) hudTimer.pause();
    }

    private void resumeHudTimer() {
        if (hudTimer != null) hudTimer.play();
    }

    private void stopHudTimer() {
        if (hudTimer != null) {
            hudTimer.stop();
            hudTimer = null;
        }
    }

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
        System.out.println("[GameController] initialize()");
        resetHUD();

        // Insertar timeLabel en HUD con spacing homogéneo
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

        elapsedSeconds = 0;
        score = 500;
        updateHudLabels();
    }


    // ==================== CICLO DE VIDA (router) ====================
    @Override
    public void onEnter() {
        System.out.println("[GameController] onEnter()");

        removeLegacyViewportCodeIfAny();
        configureVirtualViewportIfNeeded();

        // Fade-in HUD
        if (hudBar != null) {
            hudBar.setOpacity(0.0);
            var ft = new FadeTransition(Duration.millis(400), hudBar);
            ft.setFromValue(0.0);
            ft.setToValue(1.0);
            ft.play();
        }

        // Asegurar ESC para pausa
        Platform.runLater(() -> {
            var scene = gameArea.getScene();
            if (scene == null) {
                System.err.println("[GameController] WARNING: scene is null in onEnter()");
                return;
            }
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    if (settingsOpen) return;
                    if (pauseOverlay != null || paused) {
                        resumeFromPause();
                    } else {
                        onPausePressed();
                    }
                }
            });

            gameArea.setFocusTraversable(true);
            gameArea.requestFocus();
            System.out.println("[GameController] Focus requested for gameArea");
        });

        // Crear GameLoop si no existe
        if (gameLoop == null) {
            gameLoop = new GameLoop(gameArea);
            // gameLoop.setDebugLoggingEnabled(true);
        }
        if (!playerSpawnListenerAdded) {
            playerSpawnListenerAdded = true;
            gameArea.widthProperty().addListener((obs, oldW, newW) -> maybeSpawnPlayer());
            gameArea.heightProperty().addListener((obs, oldH, newH) -> maybeSpawnPlayer());
        }
        maybeSpawnPlayer();

        // === INPUT: adjuntar InputService cuando la Scene está lista ===
        Platform.runLater(() -> {
            Scene scene = gameArea.getScene();
            if (scene == null) return;

            if (input == null) input = new InputService();
            input.attach(scene);

            // Disparo instantáneo al presionar flechas
            scene.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
                KeyCode c = e.getCode();
                if (c == KeyCode.UP || c == KeyCode.DOWN || c == KeyCode.LEFT || c == KeyCode.RIGHT) {
                    tryShootNow(); // intento inmediato al presionar flecha
                }
            });

            gameArea.requestFocus();
            scene.setOnMouseClicked(e -> gameArea.requestFocus());

            maybeSpawnPlayer();
            addTickerIfNeeded(); // <— añade el ticker que gestiona cooldown + autofire
        });

        // Timer HUD
        startHudTimerIfNeeded();
        updateHudLabels();

        // Entidades de demo
        if (!demoEntitiesAdded) {
            gameArea.widthProperty().addListener((obs, oldW, newW) -> {
                maybeSpawnPlayer();
                if (!demoEntitiesAdded && newW.doubleValue() > 0) {
                    double maxX = newW.doubleValue();
                    var d1 = new DummyEntity(50, 80,  90, gameArea);
                    var d2 = new DummyEntity(200, 120, 60, gameArea);
                    gameLoop.addEntity(d1);
                    gameLoop.addEntity(d2);
                    if (!gameLoop.isRunning()) gameLoop.start();
                    demoEntitiesAdded = true;
                    System.out.println("[GameController] Demo entities added & GameLoop started (via width listener)");
                }
            });

            if (gameArea.getWidth() > 0) {
                double maxX = gameArea.getWidth();
                var d1 = new DummyEntity(50, 80,  90, gameArea);   // velocidad 90 px/s a modo ejemplo
                var d2 = new DummyEntity(200,120, 60, gameArea);

                gameLoop.addEntity(d1);
                gameLoop.addEntity(d2);

                if (!gameLoop.isRunning()) gameLoop.start();
                demoEntitiesAdded = true;
                System.out.println("[GameController] Demo entities added immediately & GameLoop started");
            }
            maybeSpawnPlayer();
        }
    }

    @Override
    public void onExit() {
        System.out.println("[GameController] onExit()");
        if (gameLoop != null && gameLoop.isRunning()) {
            gameLoop.stop();
            System.out.println("[GameController] GameLoop stopped on exit");
        }
        if (player != null && gameLoop != null) {
            gameLoop.removeEntity(player);
            player = null;
        }
        stopHudTimer();
        settingsOpen = false;
        // No hace falta desmontar InputService: la escena cambia y GC limpia handlers.
    }

    public void signalGameStart() {
        if (gameStarted) return;
        gameStarted = true;

        elapsedSeconds = 0;
        score = 500;
        updateHudLabels();
        startHudTimerIfNeeded();

        System.out.println("[GameController] signalGameStart(): gameplay started");
    }

    /** Actualiza el HUD con valores concretos (si hiciera falta). */
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

    public void backToMenu() {
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }

    public StackPane getOverlayLayer() { return overlayLayer; }

    public void startFloorMusicIfNeeded() {
        if (!musicStarted) {
            String[] floor1Tracks = { "basement1.mp3", "basement2.mp3", "basement3.mp3" };
            int idx = java.util.concurrent.ThreadLocalRandom.current().nextInt(floor1Tracks.length);
            currentTrack = floor1Tracks[idx];
            try {
                System.out.println("[GameController] Starting floor music: " + currentTrack);
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

        player = new Player(input, gameArea);
        double startX = Math.max(0.0, (width - player.getWidth()) / 2.0);
        double startY = Math.max(0.0, (height - player.getHeight()) / 2.0);
        player.setPosition(startX, startY);
        gameLoop.addEntity(player);

        if (!gameLoop.isRunning()) {
            gameLoop.start();
        }
    }

    // ==================== SHOOTING IMPLEMENTATION ====================

    /** Añade un "ticker" invisible al GameLoop para cooldown + autofire continuo. */
    private void addTickerIfNeeded() {
        if (tickerAdded || gameLoop == null) return;

        GameEntity ticker = new GameEntity() {
            private final Group view = new Group(); // invisible, sin hijos

            @Override public void update(double dt) {
                // actualiza cooldown
                shootTimer = max(0.0, shootTimer - dt);

                if (player == null || input == null) return;

                // autofire si hay flecha pulsada y cooldown listo
                double[] ar = input.getAimArrowCardinal(); // cardinal solo
                if ((ar[0] != 0 || ar[1] != 0) && shootTimer == 0.0) {
                    spawnProjectileWithAim(ar[0], ar[1]);
                    shootTimer = getFireCooldown(); // TODO(stats)
                }
            }
            @Override public javafx.scene.Node getView() { return view; }
            @Override public void onCollision(GameEntity other) { /* no-op */ }
        };

        gameLoop.addEntity(ticker);
        tickerAdded = true;
    }

    /** Disparo inmediato al presionar una flecha (sin esperar al frame siguiente). */
    private void tryShootNow() {
        if (shootTimer > 0 || player == null || input == null) return;
        double[] ar = input.getAimArrowCardinal();
        if (ar[0] == 0 && ar[1] == 0) return;
        spawnProjectileWithAim(ar[0], ar[1]);
        shootTimer = getFireCooldown(); // TODO(stats)
    }

    /** Crea y añade el proyectil con “sesgo” por movimiento WASD. */
    private void spawnProjectileWithAim(double ax, double ay) {
        if (player == null || gameLoop == null) return;

        // movimiento del jugador (WASD) normalizado
        double[] mv = input.getMoveVector();
        double mvx = mv[0], mvy = mv[1];
        double mvlen = hypot(mvx, mvy);
        if (mvlen > 0.0001) { mvx /= mvlen; mvy /= mvlen; } else { mvx = 0; mvy = 0; }

        // aplica sesgo: finalAim = normalize(aimArrow + bias * moveDir)
        double fx = ax + bias * mvx;
        double fy = ay + bias * mvy;
        double flen = hypot(fx, fy);
        if (flen < 0.0001) { fx = ax; fy = ay; flen = hypot(fx, fy); }
        fx /= flen; fy /= flen;

        // Usar layoutX/layoutY para posiciones absolutas del mundo
        double px = player.getView().getLayoutX() + player.getWidth() / 2.0 - 4.0;
        double py = player.getView().getLayoutY() + player.getHeight() / 2.0 - 4.0;

        // === Stats de disparo: usar helpers (luego leerán del Player)
        double speed = getProjectileSpeed();             // TODO(stats)
        double lifetime = getProjectileLifetimeByRange(); // TODO(stats) convierte "rango" a vida en segundos

        Projectile p = new Projectile(fx, fy, speed, lifetime, gameArea, gameLoop::removeEntity);
        p.getView().setTranslateX(px);
        p.getView().setTranslateY(py);

        gameLoop.addEntity(p);
    }

    // ==================== TODO(stats): centralizar lecturas ====================

    /** Velocidad de la lágrima (px/s). Sustituir por Player/Stats más adelante. */
    private double getProjectileSpeed() {
        // TODO(stats): return player.getStats().getTearSpeed();
        return 520.0;
    }

    /** Fire rate / cooldown entre lágrimas (s). Sustituir por Player/Stats. */
    private double getFireCooldown() {
        // TODO(stats): return player.getStats().getFireCooldownSeconds();
        return 0.25;
    }

    /** A partir del “rango” convertir a lifetime (s) según la velocidad. */
    private double getProjectileLifetimeByRange() {
        // TODO(stats):
        // double rangePx = player.getStats().getRangePixels();
        // return rangePx / getProjectileSpeed();
        double defaultRangePx = 520.0 * 1.2; // mismo efecto que lifetime 1.2s con speed 520
        return defaultRangePx / getProjectileSpeed();
    }

    public void setVirtualResolution(int w, int h) {
        w = Math.max(320, w);
        h = Math.max(240, h);
        VideoSettings.get().setResolution(w, h);
    }

   private void configureVirtualViewportIfNeeded() {
        if (viewportConfigured || gameArea == null || root == null) return;

        // 1) Saca gameArea de su AnchorPane (el HUD se queda en el AnchorPane)
        javafx.scene.layout.AnchorPane anchorParent = (javafx.scene.layout.AnchorPane) gameArea.getParent();
        if (anchorParent != null) {
            anchorParent.getChildren().remove(gameArea);
        }

        // 2) El mundo no debe afectar al layout del padre
        gameArea.setManaged(false);

        // 3) Capa de viewport que rellena TODO el root
        viewportLayer = new StackPane(gameArea);                // <-- metemos gameArea directamente
        viewportLayer.setPickOnBounds(true);
        viewportLayer.setStyle("-fx-background-color: black;");
        StackPane.setAlignment(gameArea, Pos.CENTER);

        // 4) Hacer que el viewport ocupe todo el root
        viewportLayer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        viewportLayer.prefWidthProperty().bind(root.widthProperty());
        viewportLayer.prefHeightProperty().bind(root.heightProperty());

        // 5) Insertar EL PRIMERO (debajo del HUD y del overlay)
        if (!root.getChildren().contains(viewportLayer)) {
            root.getChildren().add(0, viewportLayer);
        }

        // 6) Clip del tamaño visible del viewport (no escalado)
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(viewportLayer.widthProperty());
        clip.heightProperty().bind(viewportLayer.heightProperty());
        viewportLayer.setClip(clip);

        // 7) Mantener tamaño FIJO del mundo (resolución virtual)
        Runnable applyWorldSize = () -> {
            int vw = com.layla.core.VideoSettings.get().getVirtualWidth();
            int vh = com.layla.core.VideoSettings.get().getVirtualHeight();
            gameArea.setMinSize(vw, vh);
            gameArea.setPrefSize(vw, vh);
            gameArea.setMaxSize(vw, vh);
        };
        applyWorldSize.run();
        com.layla.core.VideoSettings.get().virtualWidthProperty().addListener((o, a, b) -> applyWorldSize.run());
        com.layla.core.VideoSettings.get().virtualHeightProperty().addListener((o, a, b) -> applyWorldSize.run());

        // 8) ESCALAR SOLO gameArea (Region de tamaño fijo) → sin derivas
        Runnable applyScale = () -> {
            double availW = viewportLayer.getWidth();
            double availH = viewportLayer.getHeight();
            int vw = com.layla.core.VideoSettings.get().getVirtualWidth();
            int vh = com.layla.core.VideoSettings.get().getVirtualHeight();
            if (availW <= 0 || availH <= 0 || vw <= 0 || vh <= 0) return;

            double scale = Math.min(availW / vw, availH / vh);
            gameArea.setScaleX(scale);
            gameArea.setScaleY(scale);
            // NADA de translate/layout sobre gameArea: el StackPane ya centra por nosotros
        };

        viewportLayer.widthProperty().addListener((o, ow, nw) -> applyScale.run());
        viewportLayer.heightProperty().addListener((o, oh, nh) -> applyScale.run());
        com.layla.core.VideoSettings.get().virtualWidthProperty().addListener((o, a, b) -> applyScale.run());
        com.layla.core.VideoSettings.get().virtualHeightProperty().addListener((o, a, b) -> applyScale.run());
        javafx.application.Platform.runLater(applyScale);

        viewportLayer.setOnMouseClicked(e -> gameArea.requestFocus());

        viewportConfigured = true;
    }

    private void removeLegacyViewportCodeIfAny() {
        if (gameArea == null) return;
        gameArea.setScaleX(1);
        gameArea.setScaleY(1);
        if (gameArea.getClip() != null) {
            gameArea.setClip(null);
        }
    }

    private enum BorderRegion {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT,
        CENTER,
        NONE
    }

}
