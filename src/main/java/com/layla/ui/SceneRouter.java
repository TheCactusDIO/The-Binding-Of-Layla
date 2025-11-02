package com.layla.ui;

import javafx.animation.FadeTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

public final class SceneRouter {
    private static Stage primaryStage;
    private static final Duration FADE_DURATION = Duration.millis(350);

    public static void init(Stage stage) {
        primaryStage = stage;
    }

    // ---------- Public API ----------

    /** Cambia de escena sin animación, fijando tamaño explícito. */
    public static void go(String fxmlPath, int width, int height) {
        ensureInit();
        try {
            System.out.println("[SceneRouter] Attempting to load: " + fxmlPath);
            var url = SceneRouter.class.getResource("/" + fxmlPath);
            if (url == null) throw new IllegalArgumentException("FXML not found on classpath: /" + fxmlPath);

            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();

            Scene scene = new Scene(root, width, height);

            // ⬇️ APLICAR SIEMPRE CSS BASE ANTES DE show()
            applyBaseStyles(scene);

            primaryStage.setScene(scene);
            primaryStage.show();
            System.out.println("[SceneRouter] Scene set and displayed: " + fxmlPath);
        } catch (Exception e) {
            System.err.println("[SceneRouter] ERROR loading FXML: " + fxmlPath);
            e.printStackTrace();
        }
    }

    /** Cambia de escena con fade, fijando tamaño explícito. */
    public static void goWithFade(String fxmlPath, int width, int height) {
        ensureInit();
        try {
            System.out.println("[SceneRouter] Attempting to load (fade): " + fxmlPath);
            var url = SceneRouter.class.getResource("/" + fxmlPath);
            if (url == null) throw new IllegalArgumentException("FXML not found: /" + fxmlPath);

            FXMLLoader loader = new FXMLLoader(url);
            Parent newRoot = loader.load();

            Scene newScene = new Scene(newRoot, width, height);

            // ⬇️ APLICAR SIEMPRE CSS BASE ANTES DE setScene()/show()
            applyBaseStyles(newScene);

            Parent currentRoot = primaryStage.getScene() != null ? primaryStage.getScene().getRoot() : null;
            if (currentRoot != null) {
                FadeTransition fadeOut = new FadeTransition(FADE_DURATION, currentRoot);
                fadeOut.setFromValue(1);
                fadeOut.setToValue(0);
                fadeOut.setOnFinished(e -> {
                    primaryStage.setScene(newScene);
                    primaryStage.show();

                    FadeTransition fadeIn = new FadeTransition(FADE_DURATION, newRoot);
                    fadeIn.setFromValue(0);
                    fadeIn.setToValue(1);
                    fadeIn.play();
                });
                fadeOut.play();
            } else {
                primaryStage.setScene(newScene);
                primaryStage.show();
            }

            System.out.println("[SceneRouter] Scene set with fade: " + fxmlPath);
        } catch (Exception e) {
            System.err.println("[SceneRouter] ERROR loading FXML (fade): " + fxmlPath);
            e.printStackTrace();
        }
    }

    /** Igual que goWithFade, pero **mantiene** el tamaño actual del Stage. */
    public static void goWithFadeKeepSize(String fxmlPath) {
        var stage = getStage();
        if (stage.getScene() == null) {
            goWithFade(fxmlPath, 1280, 720);
            return;
        }
        int w = (int) stage.getScene().getWidth();
        int h = (int) stage.getScene().getHeight();
        goWithFade(fxmlPath, w, h);
    }

    // ---------- Reusable fades sobre la escena actual ----------
    public static void fadeOutCurrent(double toOpacity, int ms) {
        var stage = getStage();
        if (stage.getScene() == null) return;
        var root = stage.getScene().getRoot();
        var ft = new FadeTransition(Duration.millis(ms), root);
        ft.setFromValue(root.getOpacity());
        ft.setToValue(toOpacity);
        ft.play();
    }

    public static void fadeInCurrent(double toOpacity, int ms) {
        var stage = getStage();
        if (stage.getScene() == null) return;
        var root = stage.getScene().getRoot();
        var ft = new FadeTransition(Duration.millis(ms), root);
        ft.setFromValue(root.getOpacity());
        ft.setToValue(1.0);
        ft.play();
    }

    // ---------- Helpers ----------
    public static Stage getStage() {
        ensureInit();
        return primaryStage;
    }

    public static Parent getRoot() {
        var s = getStage().getScene();
        if (s == null) throw new IllegalStateException("No scene set on primary stage.");
        return s.getRoot();
    }

    private static void ensureInit() {
        if (primaryStage == null) {
            throw new IllegalStateException("SceneRouter not initialized. Call SceneRouter.init(stage) first.");
        }
    }

    // === CSS helpers ===
    private static String css(String path) {
        var url = SceneRouter.class.getResource(path);
        if (url == null) {
            System.err.println("[SceneRouter] CSS not found: " + path);
            return null;
        }
        return url.toExternalForm();
    }

    /** Aplica global.css y menu.css si no están ya presentes y loguea el resultado. */
    private static void applyBaseStyles(Scene scene) {
        var g = css("/ui/styles/global.css");
        var m = css("/ui/styles/menu.css");
        if (g != null && !scene.getStylesheets().contains(g)) {
            scene.getStylesheets().add(g);
            System.out.println("[SceneRouter] Applied /ui/styles/global.css");
        }
        if (m != null && !scene.getStylesheets().contains(m)) {
            scene.getStylesheets().add(m);
            System.out.println("[SceneRouter] Applied /ui/styles/menu.css");
        }
        System.out.println("[SceneRouter] Scene stylesheets now: " + scene.getStylesheets());
    }

    private SceneRouter() {}
}
