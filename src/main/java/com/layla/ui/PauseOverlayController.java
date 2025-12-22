package com.layla.ui;

import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;

/**
 * Controlador del overlay de pausa.
 * Se limita a exponer callbacks (reanudar, opciones, volver al menú).
 */
public final class PauseOverlayController {

    @FXML private StackPane root;

    private Runnable onResume = PauseOverlayController::noop;
    private Runnable onBackToMenu = PauseOverlayController::noop;
    private Runnable onSettings = PauseOverlayController::noop;

    /**
     * Inicialización JavaFX.
     * Deja el root enfocable para que los botones respondan bien a teclado (ENTER).
     */
    @FXML
    private void initialize() {
        if (root != null) {
            root.setFocusTraversable(true);
        }
    }

    /**
     * Define la acción a ejecutar al reanudar.
     */
    public void setOnResume(Runnable r) {
        this.onResume = orNoop(r);
    }

    /**
     * Define la acción a ejecutar al volver al menú.
     */
    public void setOnBackToMenu(Runnable r) {
        this.onBackToMenu = orNoop(r);
    }

    /**
     * Define la acción a ejecutar al abrir opciones.
     */
    public void setOnSettings(Runnable r) {
        this.onSettings = orNoop(r);
    }

    /**
     * Handler FXML: reanuda la partida.
     */
    @FXML
    private void onResume() {
        onResume.run();
    }

    /**
     * Handler FXML: vuelve al menú principal.
     */
    @FXML
    private void onBackToMenu() {
        onBackToMenu.run();
    }

    /**
     * Handler FXML: abre el overlay de opciones.
     */
    @FXML
    private void onSettings() {
        onSettings.run();
    }

    /**
     * Devuelve un Runnable seguro (nunca null).
     */
    private static Runnable orNoop(Runnable r) {
        return (r != null) ? r : PauseOverlayController::noop;
    }


    // Runnable vacío reutilizable. (Intencionadamente vacío)
    private static void noop() {
    }
}
