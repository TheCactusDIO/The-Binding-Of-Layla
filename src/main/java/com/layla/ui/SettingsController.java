package com.layla.ui;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;

/**
 * Overlay de Settings.
 * Captura ESC a nivel de Scene en fase de "filter" (captura) y lo consume
 * para que NO llegue al handler global del GameController.
 * Limpia el filter al cerrar para no dejar escuchas colgando.
 */
public class SettingsController {
    private Runnable onClose = () -> {};
    public void setOnClose(Runnable r) { this.onClose = (r != null ? r : () -> {}); }

    @FXML private StackPane root;

    // Guardamos el filter para poder quitarlo al cerrar
    private EventHandler<KeyEvent> escFilter;

    @FXML
    private void initialize() {
        // Cuando el overlay tenga Scene, añadimos un FILTER en la Scene (fase de captura)
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null && escFilter != null) {
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, escFilter);
            }
            if (newScene != null) {
                escFilter = evt -> {
                    if (evt.getCode() == KeyCode.ESCAPE) {
                        evt.consume();   // BLOQUEA propagación al GameController
                        closeSelf(newScene);
                    }
                };
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, escFilter);
            }
        });

        // Foco visual al cargar
        Platform.runLater(() -> {
            root.setFocusTraversable(true);
            root.requestFocus();
        });
    }

    @FXML
    private void onBack() {
        Scene scene = root.getScene();
        closeSelf(scene);
    }

    /** Cierre centralizado: quita el filter y dispara onClose() para que el host quite el overlay. */
    private void closeSelf(Scene scene) {
        try {
            if (scene != null && escFilter != null) {
                scene.removeEventFilter(KeyEvent.KEY_PRESSED, escFilter);
            }
        } catch (Exception ignore) {}
        onClose.run();
    }
}
