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

    // ---------- Tests / hooks ----------
    private static final java.util.concurrent.CopyOnWriteArrayList<Runnable> SCENE_READY_LISTENERS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Permite a tests esperar a que la escena esté visible y estable (tras el fade-in). */
    public static void onSceneReady(Runnable r) {
        if (r != null) SCENE_READY_LISTENERS.add(r);
    }

    /** Llama a los listeners y limpia la lista. */
    private static void notifySceneReady() {
        for (Runnable r : SCENE_READY_LISTENERS) {
            try { r.run(); } catch (Throwable ignore) {}
        }
        SCENE_READY_LISTENERS.clear();
    }

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

            // 1) salir de la vista previa (si implementa ViewLifecycle)
            callExitOnPrevious();

            Scene scene = new Scene(root, width, height);

            // 2) CSS base (global + menu condicional)
            applyBaseStyles(scene, root);

            primaryStage.setScene(scene);
            primaryStage.show();
            try { primaryStage.requestFocus(); } catch (Exception ignore) {}

            // 3) entrar en la vista nueva (con Scene ya montada)
            rememberAndEnter(controller);

            // 4) notificar a tests que la escena ya está lista
            notifySceneReady();

            System.out.println("[SceneRouter] Scene set and displayed: " + fxmlPath);
        } catch (Exception e) {
            System.err.println("[SceneRouter] ERROR loading FXML: " + fxmlPath);
            e.printStackTrace();
        }
    }

    /** Cambia de escena con fade, fijando tamaño explícito. */
    public static void goWithFade(String fxmlPath, double width, double height) {
        ensureInit();
        try {
            System.out.println("[SceneRouter] Attempting to load (fade): " + fxmlPath);
            var url = SceneRouter.class.getResource(normalize(fxmlPath));
            if (url == null) throw new IllegalArgumentException("FXML not found on classpath: " + normalize(fxmlPath));

            FXMLLoader loader = new FXMLLoader(url);
            Parent newRoot = loader.load();
            Object newController = loader.getController();

            // Salir de la vista previa (si implementa ViewLifecycle)
            callExitOnPrevious();

            Scene newScene = new Scene(newRoot, width, height);
            applyBaseStyles(newScene, newRoot);

            Scene oldScene = primaryStage.getScene();
            if (oldScene != null) {
                Parent currentRoot = oldScene.getRoot();

                // Fade-out sobre el root actual
                FadeTransition fadeOut = new FadeTransition(FADE_DURATION, currentRoot);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.0);
                fadeOut.setOnFinished(e -> {
                    // Cambiamos la escena
                    primaryStage.setScene(newScene);
                    primaryStage.show();

                    // Preparar el nuevo root para fade-in
                    newRoot.setOpacity(0.0);

                    // Recordar controller y notificar onEnter() ANTES del fade-in
                    rememberAndEnter(newController);

                    // Fade-in del nuevo root
                    FadeTransition fadeIn = new FadeTransition(FADE_DURATION, newRoot);
                    fadeIn.setFromValue(0.0);
                    fadeIn.setToValue(1.0);
                    fadeIn.setOnFinished(ev -> {
                        try { primaryStage.requestFocus(); } catch (Exception ignore2) {}
                        // Aviso para los tests: la escena ya es estable/visible
                        notifySceneReady();
                    });
                    fadeIn.play();
                });
                fadeOut.play();
            } else {
                // No había escena previa (primer arranque con fade)
                primaryStage.setScene(newScene);
                primaryStage.show();

                newRoot.setOpacity(0.0);
                rememberAndEnter(newController);

                FadeTransition fadeIn = new FadeTransition(FADE_DURATION, newRoot);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.setOnFinished(ev -> {
                    try { primaryStage.requestFocus(); } catch (Exception ignore2) {}
                    notifySceneReady();
                });
                fadeIn.play();
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
        Scene s = stage.getScene();
        if (s == null) {
            // Primer arranque: aplica un tamaño por defecto razonable
            goWithFade(fxmlPath, 1280, 720);
            return;
        }
        double w = s.getWidth();   // conserva el tamaño exacto
        double h = s.getHeight();
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

    public static Object getCurrentController() {
        return currentController != null ? currentController.get() : null;
    }

    // Ejecuta 'action' cuando el currentController sea instancia de 'type' (con timeout)
    public static <T> void whenControllerIs(Class<T> type, java.util.function.Consumer<T> action) {
        final long deadline = System.nanoTime() + 1_500_000_000L; // ~1500 ms
        final javafx.animation.AnimationTimer timer = new javafx.animation.AnimationTimer() {
            @Override public void handle(long now) {
                Object c = getCurrentController();
                if (type.isInstance(c)) {
                    stop();
                    action.accept(type.cast(c));
                } else if (now > deadline) {
                    stop();
                    System.err.println("[SceneRouter] whenControllerIs timeout for " + type.getSimpleName());
                }
            }
        };
        timer.start();
    }

    // ---------- Internos ----------

    // Normaliza rutas con o sin barra inicial
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

    /** Aplica global.css siempre. Añade menu.css SOLO si root tiene id "main-menu-root". */
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
