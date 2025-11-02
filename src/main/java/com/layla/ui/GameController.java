package com.layla.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;

/**
 * Controller for the main game scene.
 */
public class GameController implements ViewLifecycle {

    @FXML
    private Pane gameArea;

    @FXML
    private Label scoreLabel;

    @FXML
    private Label floorLabel;

    @FXML
    private Label healthLabel;

    @FXML
    private void initialize() {
        System.out.println("[GameController] initialize()");
        resetHUD();
    }

    /**
     * Called when the scene becomes active.
     */

    public void onEnter() {
        System.out.println("[GameController] onEnter()");
    }

    /**
     * Called when the scene is no longer active.
     */
    public void onExit() {
        System.out.println("[GameController] onExit()");
    }

    public void setHUD(int score, int floor, int health) {
        if (scoreLabel != null) {
            scoreLabel.setText("Score: " + score);
        }
        if (floorLabel != null) {
            floorLabel.setText("Floor: " + floor);
        }
        if (healthLabel != null) {
            healthLabel.setText("HP: " + health);
        }
    }

    private void resetHUD() {
        setHUD(0, 1, 100);
    }

    // TODO: Integrate OverlayRouter with exit_confirm.fxml for pause/back-to-menu flow.

    // TODO: cuando tengas input configurado, habilita ESC para abrir overlay de pausa:
    // @FXML
    // private void initialize() {
    //     System.out.println("[GameController] initialize()");
    //     resetHUD();
    //
    //     // root.getScene().addEventHandler(KeyEvent.KEY_PRESSED, e -> {
    //     //     if (e.getCode() == KeyCode.ESCAPE) {
    //     //         // Mostrar overlay de pausa reutilizando exit_confirm.fxml
    //     //         // OverlayRouter.showOverlay(root, "ui/exit_confirm.fxml", c -> { ... });
    //     //     }
    //     // });
    // }

}
