package com.layla.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;

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

    // Método nuevo para cambiar el texto (Ej: "VICTORY!")
    public void setTitle(String text) {
        if (titleText != null) {
            titleText.setText(text);
            // Opcional: Cambiar color si es victoria
            if ("VICTORY!".equals(text)) {
                titleText.setStyle("-fx-fill: #ffd700; -fx-font-size: 32px; -fx-font-weight: bold;");
            }
        }
    }

    @FXML
    private void initialize() {
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

    @FXML private void onRetry() { onRetry.run(); }
    @FXML private void onMenu() { onBackToMenu.run(); }
}
