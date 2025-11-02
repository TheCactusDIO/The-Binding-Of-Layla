package com.layla.ui;

import com.layla.core.AssetsManager;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Controller for the main game scene.
 */
public class GameController implements ViewLifecycle {

    // ---------- ÁREA PRINCIPAL ----------
    @FXML private Pane gameArea;

    // ---------- HUD ----------
    @FXML private HBox hudBar;
    @FXML private Label scoreLabel;
    @FXML private Label floorLabel;
    @FXML private Label healthLabel;
    @FXML private StackPane root;

    @FXML private StackPane overlayLayer;

    private javafx.scene.Node pauseOverlay;
    private String currentTrack = null;
    private boolean musicStarted = false;

    // (Mantengo el gate por si lo quieres a futuro, pero ya no es necesario con este flujo)
    private static volatile boolean INTRO_GATE = false;
    public static void setIntroGate(boolean v) { INTRO_GATE = v; }

    // ===================== CONTROLES PAUSA =====================
    @FXML
    private void onPausePressed() {
        System.out.println("[GameController] onPausePressed() called");
        if (pauseOverlay != null) return; // ya abierto

        pauseOverlay = OverlayRouter.showOverlay(root, "ui/pause_overlay.fxml", controller -> {
            if (controller instanceof PauseOverlayController poc) {
                poc.setOnResume(() -> {
                    OverlayRouter.closeOverlay(root, pauseOverlay);
                    pauseOverlay = null;
                });
                poc.setOnBackToMenu(() -> {
                    OverlayRouter.closeOverlay(root, pauseOverlay);
                    pauseOverlay = null;
                    backToMenu();
                });
            }
        });
    }

    @FXML
    private void initialize() {
        System.out.println("[GameController] initialize()");
        resetHUD();
    }

    // ==================== CICLO DE VIDA ========================
    @Override
    public void onEnter() {
        System.out.println("[GameController] onEnter()");

        // Fade-in del HUD
        if (hudBar != null) {
            hudBar.setOpacity(0.0);
            var ft = new FadeTransition(Duration.millis(400), hudBar);
            ft.setFromValue(0.0);
            ft.setToValue(1.0);
            ft.play();
        }

        // NO arrancamos música aquí. La iniciará el MainMenu tras la intro/SFX.

        // Asegurar foco e input
        Platform.runLater(() -> {
            var scene = gameArea.getScene();
            if (scene == null) {
                System.err.println("[GameController] WARNING: scene is null in onEnter()");
                return;
            }
            scene.setOnKeyPressed(e -> {
                switch (e.getCode()) {
                    case ESCAPE -> onPausePressed();
                    default -> {}
                }
            });
            gameArea.setFocusTraversable(true);
            gameArea.requestFocus();
            System.out.println("[GameController] Focus requested for gameArea");
        });
    }

    @Override
    public void onExit() {
        System.out.println("[GameController] onExit()");
    }

    public StackPane getOverlayLayer() { return overlayLayer; }

    // ====================== HUD ================================
    public void setHUD(int score, int floor, int health) {
        if (scoreLabel != null) scoreLabel.setText("Score: " + score);
        if (floorLabel != null) floorLabel.setText("Floor: " + floor);
        if (healthLabel != null) healthLabel.setText("HP: " + health);
    }

    private void resetHUD() {
        setHUD(0, 1, 100);
    }

    // ================== NAVEGACIÓN =============================
    public void backToMenu() {
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }

    // ================== MÚSICA DE PISO =========================
    /** Arranca la música del piso si aún no ha empezado. Llama a esto tras la intro/SFX. */
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
