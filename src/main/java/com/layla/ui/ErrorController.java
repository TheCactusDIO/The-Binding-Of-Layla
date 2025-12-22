package com.layla.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Window;

public class ErrorController {

    @FXML private Label messageLabel;

    /**
     * Último mensaje de error mostrado en la pantalla.
     * Se mantiene como estado estático simple para poder setearlo antes de cargar el FXML.
     */
    private static String lastError = "Ha ocurrido un error desconocido.";

    /**
     * Define el mensaje de error a mostrar.
     * Si llega null o vacío, usa un texto por defecto.
     */
    public static void setLastError(String msg) {
        if (msg == null || msg.trim().isEmpty()) {
            lastError = "Ha ocurrido un error desconocido.";
        } else {
            lastError = msg.trim();
        }
    }

    @FXML
    private void initialize() {
        // Al cargar la vista, pintamos el mensaje más reciente.
        if (messageLabel != null) {
            messageLabel.setText(lastError);
        }
    }

    @FXML
    private void onExit() {
        // Cierra la ventana de error si existe y termina la aplicación con código 1.
        // (Mantengo System.exit(1) porque tú lo estabas usando para forzar salida).
        try {
            Window w = null;

            if (messageLabel != null && messageLabel.getScene() != null) {
                w = messageLabel.getScene().getWindow();
            }

            if (w instanceof Stage stage) {
                stage.close();
            } else if (w != null) {
                w.hide();
            }
        } catch (Exception ignore) {
            // Si falla el cierre de ventana, igualmente salimos.
        }

        // Aseguramos salida de JavaFX y luego del proceso.
        try { Platform.exit(); } catch (Exception ignore) {}
        System.exit(1);
    }
}
