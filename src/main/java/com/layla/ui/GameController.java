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
import com.layla.entities.DummyEntity;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

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
    private Label timeLabel;          // etiqueta para el tiempo en el HUD

    // Estado
    private boolean gameStarted = false;
    private boolean paused = false;

    // Game loop
    private GameLoop gameLoop;
    private boolean demoEntitiesAdded = false;

    // Settings desde pausa: evitar “doble ESC”
    private boolean settingsOpen = false;

    // (Gate reservado)
    @SuppressWarnings("unused")
    private static volatile boolean INTRO_GATE = false;
    public static void setIntroGate(boolean v) { INTRO_GATE = v; }

    // ===================== CONTROLES PAUSA =====================
    /**
     * Llamado al pulsar ESC (o botón de pausa).
     * Abre overlay de pausa y detiene el GameLoop. Resume lo vuelve a arrancar.
     */
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

    // RESUME: cierra overlay, reanuda loop/timer y limpia flag
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

    // mm:ss
    private static String formatMMSS(long totalSeconds) {
        long shown = Math.max(0, totalSeconds);
        long mm = shown / 60;
        long ss = shown % 60;
        return String.format("%02d:%02d", mm, ss);
    }

    // Actualiza HUD (score, piso, vida, tiempo)
    private void updateHudLabels() {
        if (scoreLabel != null) scoreLabel.setText("Score: " + score);
        if (floorLabel != null) floorLabel.setText("Floor: 1");
        if (timeLabel  != null) timeLabel.setText("Time: " + formatMMSS(elapsedSeconds));
        if (healthLabel != null) healthLabel.setText("HP: 100");
    }

    // Arranca el timer del HUD (score ↓1/s hasta 0)
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

        // Fade-in HUD
        if (hudBar != null) {
            hudBar.setOpacity(0.0);
            var ft = new FadeTransition(Duration.millis(400), hudBar);
            ft.setFromValue(0.0);
            ft.setToValue(1.0);
            ft.play();
        }

        // Asegurar input ESC
        Platform.runLater(() -> {
            var scene = gameArea.getScene();
            if (scene == null) {
                System.err.println("[GameController] WARNING: scene is null in onEnter()");
                return;
            }
            scene.setOnKeyPressed(e -> {
                switch (e.getCode()) {
                    case ESCAPE -> {
                        if (settingsOpen) return; // Settings consumirá ESC y se cerrará
                        if (pauseOverlay != null || paused) {
                            resumeFromPause();
                        } else {
                            onPausePressed();
                        }
                    }
                    default -> {}
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

        // Timer HUD
        startHudTimerIfNeeded();
        updateHudLabels();

        // Entidades de demo: añadir cuando haya ancho disponible
        if (!demoEntitiesAdded) {
            gameArea.widthProperty().addListener((obs, oldW, newW) -> {
                if (!demoEntitiesAdded && newW.doubleValue() > 0) {
                    double maxX = newW.doubleValue();
                    var d1 = new DummyEntity(50, 80, 0, maxX);
                    var d2 = new DummyEntity(200, 120, 0, maxX);
                    gameLoop.addEntity(d1);
                    gameLoop.addEntity(d2);
                    if (!gameLoop.isRunning()) gameLoop.start();
                    demoEntitiesAdded = true;
                    System.out.println("[GameController] Demo entities added & GameLoop started (via width listener)");
                }
            });

            // Si ya hay ancho, iniciar de inmediato
            if (gameArea.getWidth() > 0) {
                double maxX = gameArea.getWidth();
                var d1 = new DummyEntity(50, 80, 0, maxX);
                var d2 = new DummyEntity(200, 120, 0, maxX);
                gameLoop.addEntity(d1);
                gameLoop.addEntity(d2);
                if (!gameLoop.isRunning()) gameLoop.start();
                demoEntitiesAdded = true;
                System.out.println("[GameController] Demo entities added immediately & GameLoop started");
            }
        }
    }

    @Override
    public void onExit() {
        System.out.println("[GameController] onExit()");
        if (gameLoop != null && gameLoop.isRunning()) {
            gameLoop.stop();
            System.out.println("[GameController] GameLoop stopped on exit");
        }
        stopHudTimer();
        settingsOpen = false;
    }

    // ===== Arranque real del gameplay (sincronizado con el final del loader + game_start.wav) =====
    public void signalGameStart() {
        if (gameStarted) return;
        gameStarted = true;

        elapsedSeconds = 0;
        score = 500;
        updateHudLabels();
        startHudTimerIfNeeded();

        System.out.println("[GameController] signalGameStart(): gameplay started");
    }

    // ====================== HUD ================================
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

    // ================== NAVEGACIÓN =============================
    public void backToMenu() {
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }

    // ================== API usada por MainMenuController =======
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
}
