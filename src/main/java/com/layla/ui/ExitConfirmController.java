package com.layla.ui;

import javafx.fxml.FXML;

/**
 * Controlador del overlay de confirmación de salida.
 *
 * Este controller no decide qué hace "confirmar" o "cancelar":
 * simplemente ejecuta callbacks que le inyecta quien abre el overlay.
 */
public class ExitConfirmController {

    /** Runnable vacío para evitar null checks y no crear lambdas repetidas. */
    private static final Runnable NO_OP = () -> {};

    /** Acción a ejecutar cuando el usuario confirma. */
    private Runnable onConfirm = NO_OP;

    /** Acción a ejecutar cuando el usuario cancela. */
    private Runnable onCancel = NO_OP;

    /**
     * Define la acción al confirmar.
     * Si llega null, se reemplaza por una acción vacía.
     */
    public void setOnConfirm(Runnable r) {
        this.onConfirm = (r != null) ? r : NO_OP;
    }

    /**
     * Define la acción al cancelar.
     * Si llega null, se reemplaza por una acción vacía.
     */
    public void setOnCancel(Runnable r) {
        this.onCancel = (r != null) ? r : NO_OP;
    }

    /** Handler FXML del botón "Confirmar". */
    @FXML
    private void onConfirm() {
        onConfirm.run();
    }

    /** Handler FXML del botón "Cancelar". */
    @FXML
    private void onCancel() {
        onCancel.run();
    }
}
