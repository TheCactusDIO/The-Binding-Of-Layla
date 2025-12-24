package com.layla.ui;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Servicio de notificaciones tipo “toast” para la interfaz.
 *
 * <p>Este servicio crea un contenedor {@link VBox} donde se van apilando notificaciones.
 * Cada notificación aparece con un fade-in, se mantiene visible durante un tiempo y
 * desaparece con fade-out, eliminándose del contenedor al finalizar.</p>
 *
 * <p><b>Interacción con ratón:</b> por defecto, el contenedor no captura clics en sus
 * zonas vacías ({@code setPickOnBounds(false)}). Además, el contenido de cada toast se
 * marca como {@code mouseTransparent} para no bloquear botones o overlays que estén
 * por debajo.</p>
 */
public final class NotificationService {

    /** Ancho fijo del contenedor y de cada notificación. */
    private static final double WIDTH = 250.0;

    /** Separación vertical entre notificaciones. */
    private static final double SPACING = 10.0;

    /** Duración (ms) de las animaciones de entrada/salida de cada toast. */
    private static final int TOAST_FADE_MS = 300;

    /** Duración (ms) del fade del contenedor cuando estaba oculto. */
    private static final int CONTAINER_FADE_MS = 100;

    private final VBox container;

    /**
     * Crea el servicio y configura el contenedor visual.
     *
     * <p>El contenedor se inicializa con opacidad 0 para que no “moleste” visualmente
     * hasta que se muestre la primera notificación.</p>
     */
    public NotificationService() {
        container = new VBox(SPACING);
        container.setPrefWidth(WIDTH);
        container.setMaxWidth(WIDTH);
        container.setManaged(true);
        container.setOpacity(0.0);
        container.setAlignment(Pos.TOP_RIGHT);

        // Importante: permitir que los clics pasen a través del área vacía del contenedor.
        // Si no, puede bloquear botones que estén debajo.
        container.setPickOnBounds(false);
    }

    /**
     * Devuelve el nodo JavaFX que debe insertarse en la escena.
     *
     * @return contenedor donde se dibujan las notificaciones
     */
    public VBox getView() {
        return container;
    }

    /**
     * Muestra una notificación temporal.
     *
     * <p>Se añade en la parte superior del contenedor (índice 0) para que lo más reciente
     * aparezca arriba. El contenido se marca como transparente al ratón para no bloquear
     * la UI que haya debajo.</p>
     *
     * @param title título de la notificación (no se valida; se usa tal cual)
     * @param message mensaje de la notificación (no se valida; se usa tal cual)
     * @param duration duración en segundos que permanecerá visible antes del fade-out
     */
    public void showNotification(String title, String message, double duration) {
        Platform.runLater(() -> {
            VBox content = buildToast(title, message);

            container.getChildren().add(0, content);

            playToastFadeIn(content);
            playToastFadeOutAndRemove(content, duration);
            ensureContainerVisible();
        });
    }

    /**
     * Construye el nodo visual de la notificación (título + mensaje) con estilos.
     */
    private VBox buildToast(String title, String message) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #ffd54f; -fx-font-weight: bold; -fx-font-size: 14px;");

        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");

        VBox content = new VBox(5, titleLabel, msgLabel);
        content.setStyle(
                "-fx-background-color: rgba(0, 0, 0, 0.8);"
                        + " -fx-background-radius: 8;"
                        + " -fx-padding: 10;"
                        + " -fx-border-color: #ffd54f;"
                        + " -fx-border-width: 2;"
                        + " -fx-border-radius: 8;"
        );
        content.setMaxWidth(WIDTH);

        // Recomendable para toasts: no bloquear el ratón.
        content.setMouseTransparent(true);

        // Garantiza que el fade-in vaya de 0 -> 1 (por si el nodo reutiliza opacidad).
        content.setOpacity(0.0);

        return content;
    }

    /**
     * Ejecuta el fade-in de la notificación.
     */
    private void playToastFadeIn(VBox content) {
        FadeTransition fadeIn = new FadeTransition(Duration.millis(TOAST_FADE_MS), content);
        fadeIn.setFromValue(content.getOpacity());
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }

    /**
     * Programa el fade-out tras {@code duration} segundos y elimina el nodo al terminar.
     */
    private void playToastFadeOutAndRemove(VBox content, double durationSeconds) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(TOAST_FADE_MS), content);
        fadeOut.setDelay(Duration.seconds(Math.max(0.0, durationSeconds)));
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> container.getChildren().remove(content));
        fadeOut.play();
    }

    /**
     * Asegura que el contenedor sea visible cuando llega una notificación.
     */
    private void ensureContainerVisible() {
        if (container.getOpacity() >= 1.0) return;

        FadeTransition containerFade = new FadeTransition(Duration.millis(CONTAINER_FADE_MS), container);
        containerFade.setFromValue(container.getOpacity());
        containerFade.setToValue(1.0);
        containerFade.play();
    }
}
