package com.layla.ui;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public final class NotificationService {

    private final VBox container;

    public NotificationService() {
        container = new VBox(10);
        container.setPrefWidth(250);
        container.setMaxWidth(250);
        container.setManaged(true); // Permitir que se posicione y gestione
        container.setOpacity(0.0);
        container.setAlignment(javafx.geometry.Pos.TOP_RIGHT);

        // [FIX] Importante: Permitir que los clics pasen a través del área vacía del contenedor
        // De lo contrario, bloquea los botones del menú de pausa/game over que estén debajo.
        container.setPickOnBounds(false);
    }

    public VBox getView() {
        return container;
    }

    public void showNotification(String title, String message, double duration) {
        Platform.runLater(() -> {
            Label titleLabel = new Label(title);
            titleLabel.setStyle("-fx-text-fill: #ffd54f; -fx-font-weight: bold; -fx-font-size: 14px;");

            Label msgLabel = new Label(message);
            msgLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");

            VBox content = new VBox(5, titleLabel, msgLabel);
            content.setStyle("-fx-background-color: rgba(0, 0, 0, 0.8); -fx-background-radius: 8; -fx-padding: 10; -fx-border-color: #ffd54f; -fx-border-width: 2; -fx-border-radius: 8;");
            content.setMaxWidth(250);

            // [FIX] Hacer que la notificación en sí tampoco bloquee el ratón (opcional, pero recomendable para toasts)
            content.setMouseTransparent(true);

            container.getChildren().add(0, content);

            // Animación de aparición
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), content);
            fadeIn.setToValue(1.0);
            fadeIn.play();

            // Animación de desaparición
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), content);
            fadeOut.setDelay(Duration.seconds(duration));
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(e -> container.getChildren().remove(content));
            fadeOut.play();

            // Asegurar que el contenedor sea visible brevemente si estaba oculto
            if (container.getOpacity() < 1.0) {
                 FadeTransition containerFade = new FadeTransition(Duration.millis(100), container);
                 containerFade.setToValue(1.0);
                 containerFade.play();
            }
        });
    }
}
