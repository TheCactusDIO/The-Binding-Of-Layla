package com.layla.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;

public class MainMenuController {

    @FXML
    private void onPlayClicked() {
        System.out.println("MainMenuController: Play clicked");
        SceneRouter.go("ui/game.fxml", 1280, 720);
    }

    @FXML
    private void onOptionsClicked() {
        System.out.println("MainMenuController: Options clicked");
        SceneRouter.go("ui/settings.fxml", 900, 600);
    }

    @FXML
    private void onExitClicked() {
        System.out.println("MainMenuController: Exit clicked");
        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Exit");
        confirm.setHeaderText(null);
        confirm.setContentText("Are you sure you want to exit?");

        if (confirm.showAndWait().filter(response -> response == ButtonType.OK).isPresent()) {
            Platform.exit();
            System.exit(0);
        }
    }
}
