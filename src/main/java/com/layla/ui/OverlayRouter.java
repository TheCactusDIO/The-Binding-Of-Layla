package com.layla.ui;

import java.io.IOException;
import java.util.function.Consumer;

import com.layla.App;

import javafx.animation.FadeTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public final class OverlayRouter {
    private OverlayRouter() {}

    public static Node showOverlay(StackPane hostRoot, String fxmlPath) {
        return showOverlay(hostRoot, fxmlPath, null);
    }

    public static Node showOverlay(StackPane hostRoot, String fxmlPath, double backdropOpacity, java.util.function.Consumer<Object> controllerHook) {
        try {
            String normalized = (fxmlPath.startsWith("/")) ? fxmlPath : "/" + fxmlPath;
            var url = java.util.Objects.requireNonNull(
                App.class.getResource(normalized),
                "FXML not found on classpath: " + normalized + " (did you move/rename it?)"
            );
            FXMLLoader loader = new FXMLLoader(url);
            Parent content = loader.load();
            if (controllerHook != null) controllerHook.accept(loader.getController());

            StackPane backdrop = new StackPane();
            backdrop.setPickOnBounds(true);
            backdrop.setStyle("-fx-background-color: rgba(0,0,0," + String.format(java.util.Locale.US, "%.3f", backdropOpacity) + ");");
            backdrop.setOpacity(0);
            backdrop.getChildren().add(content);
            StackPane.setAlignment(content, javafx.geometry.Pos.CENTER);

            hostRoot.getChildren().add(backdrop);

            FadeTransition ft = new FadeTransition(Duration.millis(180), backdrop);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();

            return backdrop; // return so caller can close it later
        } catch (IOException e) {
            throw new RuntimeException("Cannot load overlay: " + fxmlPath, e);
        }
    }

    public static Node showOverlay(StackPane hostRoot, String fxmlPath, Consumer<Object> controllerHook) {
        try {
            // Asegura "/" al inicio para cargar desde el classpath raíz (src/main/resources)
            String normalized = (fxmlPath.startsWith("/")) ? fxmlPath : "/" + fxmlPath;
            var url = java.util.Objects.requireNonNull(
                App.class.getResource(normalized),
                "FXML not found on classpath: " + normalized + " (did you move/rename it?)"
            );
            FXMLLoader loader = new FXMLLoader(url);
            Parent content = loader.load();
            if (controllerHook != null) controllerHook.accept(loader.getController());

            StackPane backdrop = new StackPane();
            backdrop.setPickOnBounds(true);
            backdrop.setStyle("-fx-background-color: rgba(0,0,0,0.85);");
            backdrop.setOpacity(0);
            backdrop.getChildren().add(content);
            StackPane.setAlignment(content, javafx.geometry.Pos.CENTER);

            hostRoot.getChildren().add(backdrop);
            backdrop.setFocusTraversable(true);
            backdrop.requestFocus();

            FadeTransition ft = new FadeTransition(Duration.millis(180), backdrop);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();

            return backdrop; // return so caller can close it later
        } catch (IOException e) {
            throw new RuntimeException("Cannot load overlay: " + fxmlPath, e);
        }
    }

    public static void closeOverlay(StackPane hostRoot, Node overlay) {
        if (overlay == null) return;
        FadeTransition ft = new FadeTransition(Duration.millis(140), overlay);
        ft.setFromValue(overlay.getOpacity());
        ft.setToValue(0);
        ft.setOnFinished(ev -> ((Pane) hostRoot).getChildren().remove(overlay));
        ft.play();
    }
}
