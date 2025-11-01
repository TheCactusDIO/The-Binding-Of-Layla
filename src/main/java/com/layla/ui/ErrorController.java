package com.layla.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class ErrorController {
    @FXML
    private Label messageLabel;

    private static String lastError = "An unknown error occurred.";

    public static void setLastError(String msg) {
        lastError = msg;
    }

    @FXML
    private void initialize() {
        if (messageLabel != null) {
            messageLabel.setText(lastError);
        }
    }

    @FXML
    private void onExit() {
        // try to close the window
        Stage stage = (Stage) messageLabel.getScene().getWindow();
        stage.close();
        System.exit(1);
    }
}
