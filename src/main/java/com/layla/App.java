package com.layla;

import com.layla.ui.SceneRouter;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * Punto de entrada JavaFX del juego.
 *
 * Responsabilidades:
 * - Cargar la configuración de usuario al iniciar.
 * - Aplicar ajustes que afectan al runtime (fullscreen, volúmenes, etc.).
 * - Inicializar el enrutador de escenas y lanzar la primera pantalla (loading).
 */
public class App extends Application {

    /**
     * Método llamado por JavaFX al arrancar la aplicación.
     * Aquí aplicamos los ajustes guardados ANTES de cargar escenas, para que el juego
     * ya arranque con los valores correctos sin necesidad de pulsar "Save & Apply".
     */
    @Override
    public void start(Stage stage) {
        var config = AppContext.config();

        // Aplica volúmenes (idempotente)
        config.applyConfig();

        stage.setMinWidth(800);
        stage.setMinHeight(480);
        stage.setTitle("The Binding Of Layla");

        // 1) Monta escena inicial y muestra Stage
        SceneRouter.init(stage);
        SceneRouter.go("ui/loading.fxml", 1280, 720);

        // 2) Fullscreen: aplicarlo DESPUÉS de tener Scene + Stage mostrado
        if (config.isFullscreen()) {
            Platform.runLater(() -> {
                try {
                    stage.setFullScreen(true);
                } catch (Exception e) {
                    System.err.println("[App] Error applying fullscreen: " + e.getMessage());
                }
            });
        }
    }

    /** Entry point estándar. */
    public static void main(String[] args) {
        launch(args);
    }
}
