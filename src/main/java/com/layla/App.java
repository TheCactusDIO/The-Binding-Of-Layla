package com.layla;

import com.layla.ui.SceneRouter;

import javafx.application.Application;
import javafx.stage.Stage;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        stage.setMinWidth(800);
        stage.setMinHeight(480);
        stage.setTitle("The Binding of Layla");
        SceneRouter.init(stage);
        // Start at the loading screen which will navigate to main menu when assets are ready
        SceneRouter.go("ui/loading.fxml", 1280, 720);
    }

    public static void main(String[] args) {
        // launch() and launch(args) are both fine; using args is conventional
        launch(args);
    }
}
