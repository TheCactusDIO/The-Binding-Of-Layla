package com.layla.ui;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import com.layla.App;

import javafx.animation.FadeTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Router de overlays (capas modales) para JavaFX.
 *
 * <p>Carga un FXML, lo coloca centrado dentro de un backdrop semitransparente y lo añade
 * al {@link StackPane} host. Devuelve el nodo overlay para poder cerrarlo más tarde con
 * {@link #closeOverlay(StackPane, Node)}.</p>
 *
 * <p><b>Importante:</b> estas operaciones deben ejecutarse en el hilo de JavaFX
 * (JavaFX Application Thread).</p>
 */
public final class OverlayRouter {

    /** Opacidad por defecto del fondo (backdrop) cuando se usa la sobrecarga simple. */
    private static final double DEFAULT_BACKDROP_OPACITY = 0.85;

    private OverlayRouter() {
        // Utility class
    }

    /**
     * Muestra un overlay con opacidad de fondo por defecto ({@value #DEFAULT_BACKDROP_OPACITY})
     * y sin hook de controlador.
     *
     * @param hostRoot contenedor raíz donde se insertará el overlay (no null)
     * @param fxmlPath ruta del FXML (por ejemplo: {@code "ui/shop_overlay.fxml"}) (no null)
     * @return nodo overlay añadido al host (útil para cerrarlo luego)
     */
    public static Node showOverlay(StackPane hostRoot, String fxmlPath) {
        return showOverlay(hostRoot, fxmlPath, DEFAULT_BACKDROP_OPACITY, null, true);
    }

    /**
     * Muestra un overlay con opacidad de fondo configurable y un hook opcional al controlador.
     *
     * <p>El {@code controllerHook} se ejecuta tras cargar el FXML y recibe el controlador
     * devuelto por {@link FXMLLoader#getController()} (el caller normalmente hará cast).</p>
     *
     * @param hostRoot contenedor raíz donde se insertará el overlay (no null)
     * @param fxmlPath ruta del FXML (no null)
     * @param backdropOpacity opacidad del fondo (0..1 recomendado)
     * @param controllerHook callback opcional para configurar el controlador (puede ser null)
     * @return nodo overlay añadido al host (útil para cerrarlo luego)
     */
    public static Node showOverlay(
            StackPane hostRoot,
            String fxmlPath,
            double backdropOpacity,
            Consumer<Object> controllerHook
    ) {
        // Mantengo comportamiento cercano al original:
        // - aquí NO forzamos foco por defecto (antes tampoco).
        return showOverlay(hostRoot, fxmlPath, backdropOpacity, controllerHook, false);
    }

    /**
     * Muestra un overlay con opacidad de fondo por defecto y un hook opcional al controlador.
     *
     * @param hostRoot contenedor raíz donde se insertará el overlay (no null)
     * @param fxmlPath ruta del FXML (no null)
     * @param controllerHook callback opcional para configurar el controlador (puede ser null)
     * @return nodo overlay añadido al host (útil para cerrarlo luego)
     */
    public static Node showOverlay(StackPane hostRoot, String fxmlPath, Consumer<Object> controllerHook) {
        return showOverlay(hostRoot, fxmlPath, DEFAULT_BACKDROP_OPACITY, controllerHook, true);
    }

    /**
     * Cierra un overlay con animación de fade-out y lo elimina del host cuando termina.
     *
     * @param hostRoot host donde está insertado el overlay (no null)
     * @param overlay nodo overlay previamente devuelto por {@link #showOverlay(StackPane, String)} (puede ser null)
     */
    public static void closeOverlay(StackPane hostRoot, Node overlay) {
        Objects.requireNonNull(hostRoot, "hostRoot");
        if (overlay == null) return;

        FadeTransition ft = new FadeTransition(Duration.millis(140), overlay);
        ft.setFromValue(overlay.getOpacity());
        ft.setToValue(0);
        ft.setOnFinished(ev -> hostRoot.getChildren().remove(overlay));
        ft.play();
    }

    /**
     * Carga el FXML, crea el backdrop y lo inserta en el host con fade-in.
     *
     * @param hostRoot host donde se insertará el overlay
     * @param fxmlPath ruta del FXML
     * @param backdropOpacity opacidad del fondo
     * @param controllerHook hook opcional al controlador
     * @param requestFocus si true, el overlay intentará capturar el foco
     * @return nodo overlay listo para ser cerrado más tarde
     */
    private static Node showOverlay(
            StackPane hostRoot,
            String fxmlPath,
            double backdropOpacity,
            Consumer<Object> controllerHook,
            boolean requestFocus
    ) {
        Objects.requireNonNull(hostRoot, "hostRoot");
        Objects.requireNonNull(fxmlPath, "fxmlPath");

        try {
            FXMLLoader loader = createLoader(fxmlPath);
            Parent content = loader.load();

            if (controllerHook != null) {
                controllerHook.accept(loader.getController());
            }

            StackPane backdrop = createBackdrop(content, backdropOpacity);
            hostRoot.getChildren().add(backdrop);

            if (requestFocus) {
                backdrop.setFocusTraversable(true);
                backdrop.requestFocus();
            }

            playFadeIn(backdrop, 180);

            return backdrop;
        } catch (IOException e) {
            throw new RuntimeException("Cannot load overlay: " + fxmlPath, e);
        }
    }

    /**
     * Crea un {@link FXMLLoader} a partir de la ruta del FXML, asegurando formato y existencia
     * dentro del classpath.
     */
    private static FXMLLoader createLoader(String fxmlPath) {
        String normalized = fxmlPath.startsWith("/") ? fxmlPath : "/" + fxmlPath;

        var url = Objects.requireNonNull(
                App.class.getResource(normalized),
                "FXML not found on classpath: " + normalized + " (did you move/rename it?)"
        );

        return new FXMLLoader(url);
    }

    /**
     * Crea el contenedor de overlay con fondo semitransparente y el contenido centrado.
     */
    private static StackPane createBackdrop(Parent content, double backdropOpacity) {
        StackPane backdrop = new StackPane();
        backdrop.setPickOnBounds(true);
        backdrop.setOpacity(0);

        String alpha = String.format(Locale.US, "%.3f", backdropOpacity);
        backdrop.setStyle("-fx-background-color: rgba(0,0,0," + alpha + ");");

        backdrop.getChildren().add(content);
        StackPane.setAlignment(content, javafx.geometry.Pos.CENTER);

        return backdrop;
    }

    /**
     * Ejecuta el fade-in del overlay.
     */
    private static void playFadeIn(Node node, int millis) {
        FadeTransition ft = new FadeTransition(Duration.millis(millis), node);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }
}
