package com.layla.ui;

import java.net.URL;

import javafx.scene.control.DialogPane;

/**
 * Utilidades para aplicar estilos CSS en la UI.
 *
 * <p>Centraliza rutas de hojas de estilo y ofrece helpers para aplicarlas
 * de forma consistente en distintas pantallas y diálogos.</p>
 */
public final class UIStyles {

    private UIStyles() {
        // Clase de utilidades: no instanciable
    }

    /**
     * Devuelve la hoja de estilos CSS del HUD.
     *
     * @return URL externa del recurso {@code /ui/styles/hud.css}
     */
    public static String hud() {
        return UIStyles.class.getResource("/ui/styles/hud.css").toExternalForm();
    }

    /**
     * Devuelve la hoja de estilos CSS de la vista de juego.
     *
     * @return URL externa del recurso {@code /ui/styles/game.css}
     */
    public static String game() {
        return UIStyles.class.getResource("/ui/styles/game.css").toExternalForm();
    }

    /**
     * Devuelve la hoja de estilos CSS global.
     *
     * @return URL externa del recurso {@code /ui/styles/global.css}
     */
    public static String global() {
        return UIStyles.class.getResource("/ui/styles/global.css").toExternalForm();
    }

    /**
     * Aplica una configuración de estilos estándar para un {@link DialogPane}.
     *
     * <p>Comportamiento:</p>
     * <ul>
     *   <li>Añade la clase CSS base {@code game-dialog}.</li>
     *   <li>Añade las hojas {@code global.css} y {@code dialogs.css} al diálogo.</li>
     * </ul>
     *
     * @param dp panel del diálogo a estilizar
     */
    public static void applyDialogStyle(DialogPane dp) {
        if (dp == null) return;

        dp.getStyleClass().add("game-dialog");
        addStylesheet(dp, "/ui/styles/global.css");
        addStylesheet(dp, "/ui/styles/dialogs.css");
    }

    /**
     * Añade una hoja de estilos al {@link DialogPane} si el recurso existe.
     *
     * <p>Si no se encuentra el recurso, se registra un mensaje en stderr.</p>
     *
     * @param dp panel del diálogo
     * @param resourcePath ruta del recurso (por ejemplo {@code "/ui/styles/global.css"})
     */
    private static void addStylesheet(DialogPane dp, String resourcePath) {
        URL url = UIStyles.class.getResource(resourcePath);
        if (url != null) {
            dp.getStylesheets().add(url.toExternalForm());
        } else {
            System.err.println("[UIStyles] Stylesheet not found: " + resourcePath);
        }
    }
}
