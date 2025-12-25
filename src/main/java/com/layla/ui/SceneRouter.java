package com.layla.ui;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.layla.core.AssetsManager;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Enrutador central de escenas (FXML).
 * - Permite cambiar de escena con o sin fade.
 * - Llama automáticamente a onExit()/onEnter() si el controlador implementa ViewLifecycle.
 * - Aplica CSS global (y CSS del menú si procede).
 * - Aplica política de música para escenas de menú.
 *
 * Nota: No usa i18n ni ResourceBundle. Todo el texto del juego va hardcodeado en español.
 */
public final class SceneRouter {

    // =========================
    // Configuración / estado
    // =========================

    private static Stage primaryStage;

    /** Duración por defecto para transiciones fade. */
    private static final Duration FADE_DURATION = Duration.millis(350);

    /**
     * Controlador actual. WeakReference para evitar retener controladores por accidente.
     * (Si el controlador implementa ViewLifecycle, se llamará onExit/onEnter automáticamente.)
     */
    private static WeakReference<Object> currentController = new WeakReference<>(null);

    /** Activa/desactiva logs del router. */
    private static final boolean DEBUG_LOGS = true;

    // =========================
    // Música de menú
    // =========================

    private static final String MENU_MUSIC_FILE = "menu.mp3";

    /**
     * Escenas consideradas “menú” (asegura música de menú sin reiniciar la pista).
     * Importante: NO incluye escenas de gameplay.
     */
    private static final Set<String> MENU_MUSIC_SCENES = Set.of(
            "profile_select.fxml",
            "main_menu.fxml",
            "achievements.fxml",
            "bestiary.fxml",
            "collection.fxml",
            "items.fxml",
            "ranking.fxml",
            "run_setup.fxml",
            "settings.fxml",
            "stats_panel.fxml",
            "exit_confirm.fxml",
            "error.fxml"
    );

    // =========================
    // Hooks para tests / sincronización
    // =========================

    /** Listeners que se ejecutan cuando la escena está “lista” (típicamente tras el fade-in). */
    private static final CopyOnWriteArrayList<Runnable> SCENE_READY_LISTENERS = new CopyOnWriteArrayList<>();

    /** Clase utilitaria: no instanciable. */
    private SceneRouter() {}

    // =========================
    // API pública
    // =========================

    /**
     * Inicializa el router con el Stage principal.
     * Debe llamarse una vez al arrancar la app.
     */
    public static void init(Stage stage) {
        primaryStage = stage;
        primaryStage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        primaryStage.setFullScreenExitHint("");
    }

    /**
     * Cambia de escena SIN animación, fijando tamaño explícito.
     *
     * @param fxmlPath ruta del FXML (ej: "ui/main_menu.fxml")
     * @param width ancho de la ventana
     * @param height alto de la ventana
     */
    public static void go(String fxmlPath, int width, int height) {
        ensureInit();

        try {
            logInfo("Intentando cargar escena: " + fxmlPath);

            LoadedFXML loaded = loadFXML(fxmlPath);
            boolean wasFullScreen = primaryStage.isFullScreen();

            // 1) Salir de la vista anterior (si aplica)
            callExitOnPrevious();

            // 2) Construir escena + CSS
            Scene scene = new Scene(loaded.root(), width, height);
            applyBaseStyles(scene, loaded.root());

            // 3) Mostrar escena
            setSceneAndShow(scene, wasFullScreen);

            // 4) Política de audio (antes de onEnter)
            applySceneAudioPolicy(fxmlPath);

            // 5) Entrar en la nueva vista (si aplica)
            rememberAndEnter(loaded.controller());

            // 6) Notificar escena lista
            notifySceneReady();

            logInfo("Escena mostrada: " + fxmlPath);
        } catch (Exception e) {
            logError("ERROR cargando FXML: " + fxmlPath, e);
        }
    }

    /**
     * Cambia de escena CON fade, fijando tamaño explícito.
     *
     * @param fxmlPath ruta del FXML (ej: "ui/profile_select.fxml")
     * @param width ancho de la ventana
     * @param height alto de la ventana
     */
    public static void goWithFade(String fxmlPath, double width, double height) {
        ensureInit();

        try {
            logInfo("Intentando cargar escena (fade): " + fxmlPath);

            LoadedFXML loaded = loadFXML(fxmlPath);
            boolean wasFullScreen = primaryStage.isFullScreen();

            // Salir de la vista anterior (si aplica)
            callExitOnPrevious();

            // Nueva escena + CSS
            Scene newScene = new Scene(loaded.root(), width, height);
            applyBaseStyles(newScene, loaded.root());

            Scene oldScene = primaryStage.getScene();

            // Si hay escena previa, fade-out -> swap -> fade-in
            if (oldScene != null && oldScene.getRoot() != null) {
                Parent oldRoot = oldScene.getRoot();

                FadeTransition fadeOut = new FadeTransition(FADE_DURATION, oldRoot);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.0);

                fadeOut.setOnFinished(e -> {
                    // Cambiar escena
                    setSceneAndShow(newScene, wasFullScreen);

                    // Preparar nuevo root para fade-in
                    loaded.root().setOpacity(0.0);

                    // Audio (antes de onEnter)
                    applySceneAudioPolicy(fxmlPath);

                    // onEnter antes del fade-in (útil si la vista inicializa cosas)
                    rememberAndEnter(loaded.controller());

                    FadeTransition fadeIn = new FadeTransition(FADE_DURATION, loaded.root());
                    fadeIn.setFromValue(0.0);
                    fadeIn.setToValue(1.0);
                    fadeIn.setOnFinished(ev -> {
                        safeRequestFocus();
                        notifySceneReady();
                    });
                    fadeIn.play();
                });

                fadeOut.play();
            } else {
                // No había escena previa: fade-in directo
                setSceneAndShow(newScene, wasFullScreen);

                loaded.root().setOpacity(0.0);

                applySceneAudioPolicy(fxmlPath);
                rememberAndEnter(loaded.controller());

                FadeTransition fadeIn = new FadeTransition(FADE_DURATION, loaded.root());
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.setOnFinished(ev -> {
                    safeRequestFocus();
                    notifySceneReady();
                });
                fadeIn.play();
            }

            logInfo("Escena puesta con fade: " + fxmlPath);
        } catch (Exception e) {
            logError("ERROR cargando FXML (fade): " + fxmlPath, e);
        }
    }

    /**
     * Igual que goWithFade, pero mantiene el tamaño actual del Stage.
     *
     * @param fxmlPath ruta del FXML
     */
    public static void goWithFadeKeepSize(String fxmlPath) {
        Stage stage = getStage();
        Scene s = stage.getScene();

        if (s == null) {
            goWithFade(fxmlPath, 1280, 720);
            return;
        }

        goWithFade(fxmlPath, s.getWidth(), s.getHeight());
    }

    /**
     * Ejecuta un fade-out sobre la escena actual.
     *
     * @param toOpacity opacidad destino (0..1)
     * @param ms duración en milisegundos
     */
    public static void fadeOutCurrent(double toOpacity, int ms) {
        Stage stage = getStage();
        if (stage.getScene() == null) return;

        Parent root = stage.getScene().getRoot();
        if (root == null) return;

        FadeTransition ft = new FadeTransition(Duration.millis(ms), root);
        ft.setFromValue(root.getOpacity());
        ft.setToValue(toOpacity);
        ft.play();
    }

    /**
     * Ejecuta un fade-in sobre la escena actual.
     *
     * @param toOpacity opacidad destino (0..1)
     * @param ms duración en milisegundos
     */
    public static void fadeInCurrent(double toOpacity, int ms) {
        Stage stage = getStage();
        if (stage.getScene() == null) return;

        Parent root = stage.getScene().getRoot();
        if (root == null) return;

        FadeTransition ft = new FadeTransition(Duration.millis(ms), root);
        ft.setFromValue(root.getOpacity());
        ft.setToValue(toOpacity);
        ft.play();
    }

    /**
     * Devuelve el Stage principal (debe haberse llamado init()).
     */
    public static Stage getStage() {
        ensureInit();
        return primaryStage;
    }

    /**
     * Devuelve el root de la escena actual.
     */
    public static Parent getRoot() {
        Scene s = getStage().getScene();
        if (s == null) throw new IllegalStateException("No hay escena asignada en el Stage principal.");
        return s.getRoot();
    }

    /**
     * Devuelve el controlador de la escena actual.
     */
    public static Object getCurrentController() {
        return currentController != null ? currentController.get() : null;
    }

    /**
     * Registra un listener que se ejecuta cuando la escena está lista (normalmente tras el fade-in).
     */
    public static void onSceneReady(Runnable r) {
        if (r != null) SCENE_READY_LISTENERS.add(r);
    }

    /**
     * Ejecuta 'action' cuando el controlador actual sea instancia de 'type' (con timeout).
     * Útil para tests o para enganchar acciones cuando la transición termina.
     */
    public static <T> void whenControllerIs(Class<T> type, java.util.function.Consumer<T> action) {
        final long deadline = System.nanoTime() + 1_500_000_000L; // ~1500 ms

        AnimationTimer timer = new AnimationTimer() {
            @Override public void handle(long now) {
                Object c = getCurrentController();
                if (type.isInstance(c)) {
                    stop();
                    action.accept(type.cast(c));
                } else if (now > deadline) {
                    stop();
                    System.err.println("[SceneRouter] Timeout esperando controlador: " + type.getSimpleName());
                }
            }
        };
        timer.start();
    }

    // =========================
    // Internos
    // =========================

    /** Verifica que el router está inicializado (init(stage)). */
    private static void ensureInit() {
        if (primaryStage == null) {
            throw new IllegalStateException("SceneRouter no inicializado. Llama a SceneRouter.init(stage) primero.");
        }
    }

    /** Llama a los listeners de escena lista y limpia la lista. */
    private static void notifySceneReady() {
        for (Runnable r : SCENE_READY_LISTENERS) {
            try { r.run(); } catch (Throwable ignore) {}
        }
        SCENE_READY_LISTENERS.clear();
    }

    /** Pide foco al stage de forma segura. */
    private static void safeRequestFocus() {
        try { primaryStage.requestFocus(); } catch (Exception ignore) {}
    }

    /** Aplica una escena al stage y la muestra. */
    private static void setSceneAndShow(Scene scene, boolean restoreFullscreen) {
        boolean wasShowing = primaryStage.isShowing();
        primaryStage.setScene(scene);
        if (!wasShowing) {
            primaryStage.show();
        } else if (!primaryStage.isFullScreen()) {
            primaryStage.sizeToScene();
        }
        restoreFullscreenIfNeeded(restoreFullscreen);
        safeRequestFocus();
    }

    private static void restoreFullscreenIfNeeded(boolean restore) {
        if (!restore) return;
        Platform.runLater(() -> {
            try {
                if (!primaryStage.isFullScreen()) {
                    primaryStage.setFullScreen(true);
                }
            } catch (Exception ignore) {}
        });
    }

    /**
     * Carga un FXML y devuelve su root y su controller.
     * Usa resolución robusta de rutas para evitar bloqueos en loading.
     */
    private static LoadedFXML loadFXML(String fxmlPath) throws Exception {
        URL url = resolveResourceOrThrow(fxmlPath, "FXML");
        FXMLLoader loader = new FXMLLoader(url);
        Parent root = loader.load();
        Object controller = loader.getController();
        return new LoadedFXML(root, controller);
    }

    /**
     * Política de audio:
     * - Si es escena de menú, asegura la música del menú sin reiniciarla.
     */
    private static void applySceneAudioPolicy(String fxmlPath) {
        String base = basename(fxmlPath);
        if (MENU_MUSIC_SCENES.contains(base)) {
            AssetsManager.ensureMusic(MENU_MUSIC_FILE, true);
        }
    }

    /**
     * Aplica estilos:
     * - global.css siempre
     * - menu.css solo si root tiene id "main-menu-root"
     */
    private static void applyBaseStyles(Scene scene, Parent root) {
        String global = css("ui/css/global.css");
        if (global != null && !scene.getStylesheets().contains(global)) {
            scene.getStylesheets().add(global);
            logInfo("Aplicado CSS global: ui/css/global.css");
        }

        boolean isMainMenu = root != null && "main-menu-root".equals(root.getId());
        if (isMainMenu) {
            String menu = css("ui/css/menu.css");
            if (menu != null && !scene.getStylesheets().contains(menu)) {
                scene.getStylesheets().add(menu);
                logInfo("Aplicado CSS menú: ui/css/menu.css");
            }
        }

        logInfo("Stylesheets activos: " + scene.getStylesheets());
    }

    /**
     * Si el controlador anterior implementa ViewLifecycle, llama a onExit().
     */
    private static void callExitOnPrevious() {
        Object prev = currentController.get();
        if (prev instanceof ViewLifecycle vl) {
            try { vl.onExit(); } catch (Exception ignore) {}
        }
    }

    /**
     * Guarda referencia al controlador actual y, si implementa ViewLifecycle, llama a onEnter().
     */
    private static void rememberAndEnter(Object controller) {
        currentController = new WeakReference<>(controller);
        if (controller instanceof ViewLifecycle vl) {
            try { vl.onEnter(); } catch (Exception ignore) {}
        }
    }

    /**
     * Resuelve recursos de forma robusta:
     * - /ui/xxx.fxml (ideal)
     * - ui/xxx.fxml (relativo al paquete)
     * - /com/layla/ui/ui/xxx.fxml (recursos “metidos” bajo el paquete)
     */
    private static URL resolveResource(String path) {
        if (path == null || path.isBlank()) return null;

        String p = path.replace('\\', '/');
        String noSlash = p.startsWith("/") ? p.substring(1) : p;

        String c1 = "/" + noSlash;
        String c2 = noSlash;
        String c3 = "/com/layla/ui/" + noSlash;

        URL url = SceneRouter.class.getResource(c1);
        if (url != null) return url;

        url = SceneRouter.class.getResource(c2);
        if (url != null) return url;

        url = SceneRouter.class.getResource(c3);
        if (url != null) return url;

        return null;
    }

    /**
     * Igual que resolveResource, pero lanza excepción detallada si no se encuentra.
     */
    private static URL resolveResourceOrThrow(String path, String kind) {
        URL url = resolveResource(path);
        if (url == null) {
            String p = path.replace('\\', '/');
            String noSlash = p.startsWith("/") ? p.substring(1) : p;

            throw new IllegalArgumentException(
                    kind + " no encontrado en classpath. Pedido: '" + path + "' "
                            + "(probado: '/" + noSlash + "', '" + noSlash + "', '/com/layla/ui/" + noSlash + "')"
            );
        }
        return url;
    }

    /**
     * Devuelve la parte final del path (nombre del archivo).
     */
    private static String basename(String path) {
        if (path == null) return "";
        String p = path.replace('\\', '/');
        int i = p.lastIndexOf('/');
        return (i >= 0) ? p.substring(i + 1) : p;
    }

    /**
     * Devuelve url.toExternalForm() para un CSS si existe; si no, loguea y devuelve null.
     */
    private static String css(String path) {
        URL url = resolveResource(path);
        if (url == null) {
            System.err.println("[SceneRouter] CSS no encontrado: " + path);
            return null;
        }
        return url.toExternalForm();
    }

    /** Log informativo (controlado por DEBUG_LOGS). */
    private static void logInfo(String msg) {
        if (DEBUG_LOGS) System.out.println("[SceneRouter] " + msg);
    }

    /** Log de error. */
    private static void logError(String msg, Throwable t) {
        System.err.println("[SceneRouter] " + msg);
        if (t != null) t.printStackTrace();
    }

    /**
     * Contenedor inmutable del resultado de cargar un FXML.
     */
    private record LoadedFXML(Parent root, Object controller) {}
}
