package com.layla;

import com.layla.ui.SceneRouter;

import javafx.application.Application;
import javafx.stage.Stage;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        // Cargar configuración de usuario (Volumen, Fullscreen)
        var config = AppContext.config(); // Esto dispara el load() en el constructor

        stage.setMinWidth(800);
        stage.setMinHeight(480);
        stage.setTitle("The Binding of Layla");

        // Aplicar fullscreen si estaba guardado
        if (config.isFullscreen()) {
            stage.setFullScreen(true);
        }

        SceneRouter.init(stage);
        SceneRouter.go("ui/loading.fxml", 1280, 720);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
