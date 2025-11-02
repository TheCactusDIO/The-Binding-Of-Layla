package com.layla.ui;

import javafx.animation.FadeTransition;
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

    private javafx.scene.Node pauseOverlay;

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

    // ===========================================================
    // ==================== CICLO DE VIDA ========================
    // ===========================================================

    @Override
    public void onEnter() {
        System.out.println("[GameController] onEnter()");

        // Fade-in del HUD
        if (hudBar != null) {
            hudBar.setOpacity(0.0);
            FadeTransition ft = new FadeTransition(Duration.millis(400), hudBar);
            ft.setFromValue(0.0);
            ft.setToValue(1.0);
            ft.play();
        }

        // Aseguramos que la escena y el foco estén listos después del render inicial
        javafx.application.Platform.runLater(() -> {
            var scene = gameArea.getScene();
            if (scene == null) {
                System.err.println("[GameController] WARNING: scene is null in onEnter()");
                return;
            }

            // Listener para tecla ESC
            scene.setOnKeyPressed(e -> {
                switch (e.getCode()) {
                    case ESCAPE -> onPausePressed();
                    default -> {}
                }
            });

            // Garantizar foco de teclado
            gameArea.setFocusTraversable(true);
            gameArea.requestFocus();
            System.out.println("[GameController] Focus requested for gameArea");
        });
    }


        // Si en el futuro tienes input, pide foco aquí
        // if (gameArea != null) gameArea.requestFocus();

    @Override
    public void onExit() {
        System.out.println("[GameController] onExit()");
        // Aquí podrías pausar animaciones, threads, música del juego, etc.
    }

    // ===========================================================
    // ====================== HUD ================================
    // ===========================================================

    public void setHUD(int score, int floor, int health) {
        if (scoreLabel != null) scoreLabel.setText("Score: " + score);
        if (floorLabel != null) floorLabel.setText("Floor: " + floor);
        if (healthLabel != null) healthLabel.setText("HP: " + health);
    }

    private void resetHUD() {
        setHUD(0, 1, 100);
    }

    // ===========================================================
    // ================== NAVEGACIÓN =============================
    // ===========================================================

    /** Vuelve al menú principal conservando tamaño (para usar desde overlay de pausa). */
    public void backToMenu() {
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }

    // ===========================================================
    // ==================== FUTURO: PAUSA ========================
    // ===========================================================

    // TODO: Integrar OverlayRouter con exit_confirm.fxml para overlay de pausa / volver al menú.
}
