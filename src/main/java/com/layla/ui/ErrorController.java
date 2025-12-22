package com.layla.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;

/**
 * Pantalla de error fatal (fallback).
 *
 * Se usa cuando la app no puede continuar (por ejemplo, fallo cargando assets).
 * El mensaje se inyecta antes de cargar el FXML mediante {@link #setLastError(String)}.
 */
public class ErrorController {

    // =========================
    // FXML
    // =========================

    @FXML private Label messageLabel;
    @FXML private Button exitButton;

    // =========================
    // Estado (simple y compatible)
    // =========================

    private static final String DEFAULT_ERROR = "Ha ocurrido un error desconocido.";
    private static String lastError = DEFAULT_ERROR;

    /**
     * Define el mensaje a mostrar en la pantalla de error.
     * Pensado para llamarse antes de cargar el FXML.
     */
    public static void setLastError(String msg) {
        lastError = normalizeMessage(msg);
    }

    /**
     * Inicializa la vista:
     * - pinta el último error
     * - habilita ESC/ENTER/SPACE para salir
     */
    @FXML
    private void initialize() {
        if (messageLabel != null) {
            messageLabel.setText(lastError);
        }

        if (exitButton != null) {
            exitButton.setDefaultButton(true);
            exitButton.setFocusTraversable(true);
            exitButton.requestFocus();

            // Teclas rápidas: ESC / ENTER / SPACE => salir
            exitButton.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                KeyCode code = e.getCode();
                if (code == KeyCode.ESCAPE || code == KeyCode.ENTER || code == KeyCode.SPACE) {
                    onExit();
                    e.consume();
                }
            });
        }
    }

    /**
     * Handler del botón "Exit".
     * Cierra la ventana (si existe) y termina la app con código 1.
     */
    @FXML
    private void onExit() {
        closeWindowSafely();

        // Detiene JavaFX y termina proceso
        try { Platform.exit(); } catch (Exception ignore) {}
        System.exit(1);
    }

    // =========================
    // Helpers
    // =========================

    /** Normaliza el mensaje para evitar null/strings vacíos. */
    private static String normalizeMessage(String msg) {
        if (msg == null) return DEFAULT_ERROR;
        String trimmed = msg.trim();
        return trimmed.isEmpty() ? DEFAULT_ERROR : trimmed;
    }

    /** Intenta cerrar la ventana actual sin lanzar excepciones. */
    private void closeWindowSafely() {
        try {
            Window w = (messageLabel != null && messageLabel.getScene() != null)
                    ? messageLabel.getScene().getWindow()
                    : (exitButton != null && exitButton.getScene() != null)
                        ? exitButton.getScene().getWindow()
                        : null;

            if (w != null) w.hide();
        } catch (Exception ignore) {
            // si falla, igualmente hacemos exit(1)
        }
    }
}
