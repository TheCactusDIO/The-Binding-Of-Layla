package com.layla.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;

/**
 * Controlador del overlay de confirmación de salida.
 *
 * Este controller no decide qué hace "Confirmar" o "Cancelar":
 * solo ejecuta callbacks inyectados por quien abre el overlay.
 *
 * Extra:
 * - ENTER / SPACE => Confirmar
 * - ESC => Cancelar
 * - Botones default/cancel para comportamiento estándar JavaFX
 */
public class ExitConfirmController {

    // =========================
    // FXML
    // =========================

    @FXML private VBox root;
    @FXML private Button cancelBtn;
    @FXML private Button confirmBtn;

    // =========================
    // Callbacks
    // =========================

    private static final Runnable NO_OP = () -> {};

    private Runnable onConfirm = NO_OP;
    private Runnable onCancel  = NO_OP;

    /**
     * Inicializa el overlay:
     * - configura botones default/cancel
     * - fuerza foco para capturar teclas
     * - soporta ENTER/ESC/SPACE
     */
    @FXML
    private void initialize() {
        if (confirmBtn != null) confirmBtn.setDefaultButton(true);
        if (cancelBtn  != null) cancelBtn.setCancelButton(true);

        if (root != null) {
            root.setFocusTraversable(true);
            root.requestFocus();

            root.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                KeyCode code = e.getCode();

                if (code == KeyCode.ESCAPE) {
                    onCancel.run();
                    e.consume();
                    return;
                }

                if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
                    onConfirm.run();
                    e.consume();
                }
            });
        }
    }

    /**
     * Define la acción al confirmar.
     * Si llega null, se reemplaza por NO_OP para evitar NPE.
     */
    public void setOnConfirm(Runnable r) {
        this.onConfirm = (r != null) ? r : NO_OP;
    }

    /**
     * Define la acción al cancelar.
     * Si llega null, se reemplaza por NO_OP para evitar NPE.
     */
    public void setOnCancel(Runnable r) {
        this.onCancel = (r != null) ? r : NO_OP;
    }

    /**
     * Handler FXML del botón "Exit".
     */
    @FXML
    private void onConfirm() {
        onConfirm.run();
    }

    /**
     * Handler FXML del botón "Cancel".
     */
    @FXML
    private void onCancel() {
        onCancel.run();
    }
}
