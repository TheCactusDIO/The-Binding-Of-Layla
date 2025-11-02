package com.layla.ui;

import java.lang.ref.WeakReference;

import javafx.animation.FadeTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

public final class SceneRouter {
    private static Stage primaryStage;
    private static final Duration FADE_DURATION = Duration.millis(350);

    // Controlador actual (para onExit/onEnter automáticos)
    private static WeakReference<Object> currentController = new WeakReference<>(null);

    private SceneRouter() {}

    // ---------- Bootstrap ----------
    public static void init(Stage stage) {
        primaryStage = stage;
    }

    private static void ensureInit() {
        if (primaryStage == null) {
            throw new IllegalStateException("SceneRouter not initialized. Call SceneRouter.init(stage) first.");
        }
    }

    // ---------- Public API ----------

    /** Cambia de escena sin animación, fijando tamaño explícito. */
    public static void go(String fxmlPath, int width, int height) {
        ensureInit();
        try {
            System.out.println("[SceneRouter] Attempting to load: " + fxmlPath);
            var url = SceneRouter.class.getResource(normalize(fxmlPath));
            if (url == null) throw new IllegalArgumentException("FXML not found on classpath: " + normalize(fxmlPath));

            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            Object controller = loader.getController();

            // 1) salir de la vista previa (si la hay)
            callExitOnPrevious();

            Scene scene = new Scene(root, width, height);

            // 2) CSS base (global.css + menu.css sólo si root es el main menu)
            applyBaseStyles(scene, root);

            primaryStage.setScene(scene);
            primaryStage.show();

            // 3) entrar en la vista nueva (con Scene ya montada)
            rememberAndEnter(controller);

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
            var url = SceneRouter.class.getResource(normalize(fxmlPath));
            if (url == null) throw new IllegalArgumentException("FXML not found on classpath: " + normalize(fxmlPath));

            FXMLLoader loader = new FXMLLoader(url);
            Parent newRoot = loader.load();
            Object newController = loader.getController();
            Scene newScene = new Scene(newRoot, width, height);

            // CSS base antes de colocar la escena
            applyBaseStyles(newScene, newRoot);

            Parent currentRoot = primaryStage.getScene() != null ? primaryStage.getScene().getRoot() : null;
            if (currentRoot != null) {
                // 1) salir de la vista previa
                callExitOnPrevious();

                FadeTransition fadeOut = new FadeTransition(FADE_DURATION, currentRoot);
                fadeOut.setFromValue(1);
                fadeOut.setToValue(0);
                fadeOut.setOnFinished(e -> {
                    primaryStage.setScene(newScene);
                    primaryStage.show();

                    // 2) entrar en la vista nueva (con Scene ya montada)
                    rememberAndEnter(newController);

                    FadeTransition fadeIn = new FadeTransition(FADE_DURATION, newRoot);
                    fadeIn.setFromValue(0);
                    fadeIn.setToValue(1);
                    fadeIn.play();
                });
                fadeOut.play();
            } else {
                // No había escena previa
                primaryStage.setScene(newScene);
                primaryStage.show();
                rememberAndEnter(newController); // entrar tras montar Scene
            }

            System.out.println("[SceneRouter] Scene set with fade: " + fxmlPath);
        } catch (Exception e) {
            System.err.println("[SceneRouter] ERROR loading FXML (fade): " + fxmlPath);
            e.printStackTrace();
        }
    }

    /** Igual que goWithFade, pero mantiene el tamaño actual del Stage. */
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

    // ---------- Helpers públicos ----------
    public static Stage getStage() {
        ensureInit();
        return primaryStage;
    }

    public static Parent getRoot() {
        var s = getStage().getScene();
        if (s == null) throw new IllegalStateException("No scene set on primary stage.");
        return s.getRoot();
    }

    // ---------- Internos ----------

    private static String normalize(String fxmlPath) {
        return fxmlPath.startsWith("/") ? fxmlPath : "/" + fxmlPath;
    }

    /** Devuelve url.toExternalForm() si existe; si no, null y loguea. */
    private static String css(String path) {
        var url = SceneRouter.class.getResource(path);
        if (url == null) {
            System.err.println("[SceneRouter] CSS not found: " + path);
            return null;
        }
        return url.toExternalForm();
    }

    /**
     * Aplica global.css siempre. Añade menu.css SOLO si el root actual es el main menu
     * (heurística: id == "main-menu-root").
     */
    private static void applyBaseStyles(Scene scene, Parent root) {
        var global = css("/ui/styles/global.css");
        if (global != null && !scene.getStylesheets().contains(global)) {
            scene.getStylesheets().add(global);
            System.out.println("[SceneRouter] Applied /ui/styles/global.css");
        }

        boolean isMainMenu = root != null && "main-menu-root".equals(root.getId());
        if (isMainMenu) {
            var menu = css("/ui/styles/menu.css");
            if (menu != null && !scene.getStylesheets().contains(menu)) {
                scene.getStylesheets().add(menu);
                System.out.println("[SceneRouter] Applied /ui/styles/menu.css");
            }
        }

        System.out.println("[SceneRouter] Scene stylesheets now: " + scene.getStylesheets());
    }

    private static void callExitOnPrevious() {
        Object prev = currentController.get();
        if (prev instanceof ViewLifecycle vl) {
            try { vl.onExit(); } catch (Exception ignore) {}
        }
    }

    private static void rememberAndEnter(Object controller) {
        currentController = new WeakReference<>(controller);
        if (controller instanceof ViewLifecycle vl) {
            try { vl.onEnter(); } catch (Exception ignore) {}
        }
    }
}
