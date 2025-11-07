package com.layla.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;

/**
 * Controlador del overlay de Game Over.
 * Expone callbacks configurables desde GameController:
 *   - setOnRetry(Runnable)
 *   - setOnBackToMenu(Runnable)
 */
public class GameOverController {

    @FXML private StackPane root;
    @FXML private Button retryButton;
    @FXML private Button menuButton;
    @FXML private Text titleText;

    private Runnable onRetry = () -> {};
    private Runnable onBackToMenu = () -> {};

    public void setOnRetry(Runnable r) {
        this.onRetry = (r != null) ? r : () -> {};
    }

    public void setOnBackToMenu(Runnable r) {
        this.onBackToMenu = (r != null) ? r : () -> {};
    }

    @FXML
    private void initialize() {
        // Foco para recibir teclas
        if (root != null) {
            root.setFocusTraversable(true);
            root.requestFocus();

            root.setOnKeyPressed(e -> {
                KeyCode code = e.getCode();
                if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
                    onRetry.run();
                    e.consume();
                } else if (code == KeyCode.ESCAPE) {
                    onBackToMenu.run();
                    e.consume();
                }
            });
        }
    }

    // Handlers FXML
    @FXML
    private void onRetry() {
        onRetry.run();
    }

    @FXML
    private void onMenu() {
        onBackToMenu.run();
    }
}
