package com.layla.ui;

import com.layla.core.AssetsManager;
import com.layla.core.TestEnv;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import static javafx.beans.binding.Bindings.max;
import static javafx.beans.binding.Bindings.min;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.text.Font;
import javafx.util.Duration;

/**
 * Controlador del menú principal.
 *
 * Responsabilidades:
 * - Configurar el layout responsive del menú.
 * - Gestionar el vídeo de fondo (si está disponible).
 * - Abrir escenas (Run Setup / Profiles / Collection).
 * - Mostrar overlays (Settings / Exit confirm).
 * - Liberar recursos de vídeo al salir del menú.
 */
public final class MainMenuController implements ViewLifecycle {

    // ---------- Recursos ----------
    private static final String MENU_MUSIC_FILE = "menu.mp3";
    private static final String MENU_VIDEO_FILE = "menu.mp4";
    private static final String MAIN_FONT_PATH  = "/assets/fonts/main_font.ttf";

    // ---------- Navegación ----------
    private static final String FXML_RUN_SETUP     = "ui/run_setup.fxml";
    private static final String FXML_PROFILES      = "ui/profile_select.fxml";
    private static final String FXML_COLLECTION    = "ui/collection.fxml";
    private static final String FXML_MAIN_MENU     = "ui/main_menu.fxml";
    private static final String FXML_SETTINGS      = "ui/settings.fxml";
    private static final String FXML_EXIT_CONFIRM  = "ui/exit_confirm.fxml";

    // ---------- FXML ----------
    @FXML private StackPane root;
    @FXML private VBox menuBox;
    @FXML private Label titleLabel;
    @FXML private Button playBtn, profilesBtn, collectionBtn, optionsBtn, exitBtn;
    @FXML private MediaView backgroundVideo;

    // ---------- Estado ----------
    private MediaPlayer videoPlayer;
    private boolean retriedVideoOnce = false;

    // Overlays (evita abrir el mismo overlay dos veces)
    private Node settingsOverlay;
    private Node exitOverlay;

    /**
     * Inicializa el menú: fuentes, layout, vídeo (si procede) y música.
     * OJO: initialize() se ejecuta cuando se carga el FXML (antes de onEnter()).
     */
    @FXML
    private void initialize() {
        log("initialize()");

        loadMainFontSafely();
        setupResponsiveLayout();

        // Vídeo: en tests/headless se desactiva por completo.
        if (TestEnv.disableMedia()) {
            hideVideoNode();
        } else {
            setupBackgroundVideoNode();
            // Delay pequeño para evitar “glitches” al crear el MediaPlayer justo al cargar escena.
            Platform.runLater(() -> {
                PauseTransition delay = new PauseTransition(Duration.millis(120));
                delay.setOnFinished(e -> startBackgroundVideoSafely());
                delay.play();
            });
        }

        // Música del menú sin reiniciarla si ya estaba sonando
        ensureMenuMusic();
    }

    /**
     * Se llama automáticamente al entrar en la vista (SceneRouter + ViewLifecycle).
     * Aquí aseguramos música de menú, por si venimos de otra escena y queremos reanudarla.
     */
    @Override
    public void onEnter() {
        ensureMenuMusic();
    }

    /**
     * Se llama automáticamente al salir de la vista.
     * Liberamos sólo el vídeo (la música del menú se controla desde SceneRouter / escenas).
     */
    @Override
    public void onExit() {
        cleanupVideoOnly();
    }

    // -----------------------------
    // Configuración
    // -----------------------------

    /** Carga la fuente principal si existe (no es crítico si falla). */
    private void loadMainFontSafely() {
        try {
            Font.loadFont(getClass().getResourceAsStream(MAIN_FONT_PATH), 16);
        } catch (Exception e) {
            System.err.println("[MainMenu] Fuente no encontrada: " + e.getMessage());
        }
    }

    /** Ajusta anchos del menú/botones según el tamaño de ventana. */
    private void setupResponsiveLayout() {
        if (root == null || menuBox == null) return;

        // Caja del menú: 300..640 y ~50% del ancho de la ventana
        menuBox.prefWidthProperty().unbind();
        menuBox.setMinWidth(300);
        menuBox.setMaxWidth(640);
        menuBox.prefWidthProperty().bind(min(640.0, max(300.0, root.widthProperty().multiply(0.5))));
        menuBox.setFillWidth(false);

        // Botones: 220..360 y ~55% del ancho del menú
        var btnWidthBinding = min(360.0, max(220.0, menuBox.widthProperty().multiply(0.55)));
        for (Button b : new Button[]{playBtn, profilesBtn, collectionBtn, optionsBtn, exitBtn}) {
            if (b == null) continue;
            b.setMaxWidth(Region.USE_PREF_SIZE);
            b.prefWidthProperty().bind(btnWidthBinding);
        }

        // Título: wrap y ancho máximo igual al del menú
        if (titleLabel != null) {
            titleLabel.setWrapText(true);
            titleLabel.maxWidthProperty().bind(menuBox.widthProperty());
        }
    }

    /** Asegura música del menú sin reiniciar si ya está sonando. */
    private void ensureMenuMusic() {
        AssetsManager.ensureMusic(MENU_MUSIC_FILE, true);
    }

    // -----------------------------
    // Vídeo de fondo
    // -----------------------------

    /** Oculta completamente el MediaView (ideal para tests/headless o si falla el player). */
    private void hideVideoNode() {
        if (backgroundVideo == null) return;

        backgroundVideo.setVisible(false);
        backgroundVideo.setManaged(false);
        backgroundVideo.setMediaPlayer(null);
    }

    /** Deja el MediaView listo y “responsive”, pero todavía sin MediaPlayer asignado. */
    private void setupBackgroundVideoNode() {
        if (backgroundVideo == null || root == null) return;

        backgroundVideo.setPreserveRatio(true);
        backgroundVideo.fitWidthProperty().bind(root.widthProperty());
        backgroundVideo.fitHeightProperty().bind(root.heightProperty());
        backgroundVideo.setOpacity(0.0);
    }

    /**
     * Arranca el vídeo de fondo de manera robusta.
     * Usa AssetsManager.playVideo(...) para centralizar workarounds/paths.
     */
    private void startBackgroundVideoSafely() {
        try {
            if (backgroundVideo == null) return;

            backgroundVideo.setMediaPlayer(null);

            // Importante: el fix real está en AssetsManager.playVideo(...)
            videoPlayer = AssetsManager.playVideo(MENU_VIDEO_FILE, true);

            if (videoPlayer == null) {
                System.err.println("[MainMenu] No se pudo crear el MediaPlayer del vídeo.");
                hideVideoNode();
                return;
            }

            // Si falla, reintentar una vez. Si falla otra vez, se oculta el vídeo.
            videoPlayer.setOnError(() -> {
                System.err.println("[MainMenu] MediaPlayer ERROR: " + videoPlayer.getError());
                retryVideoPlayerOnce();
            });

            backgroundVideo.setMediaPlayer(videoPlayer);

            // Fade-in suave cuando el vídeo está listo
            videoPlayer.setOnReady(() -> {
                FadeTransition ft = new FadeTransition(Duration.seconds(1.0), backgroundVideo);
                ft.setFromValue(0.0);
                ft.setToValue(1.0);
                ft.play();
            });

        } catch (Exception e) {
            e.printStackTrace();
            hideVideoNode();
        }
    }

    /** Reintenta crear el MediaPlayer una sola vez para evitar loops de error. */
    private void retryVideoPlayerOnce() {
        if (retriedVideoOnce) {
            hideVideoNode();
            return;
        }
        retriedVideoOnce = true;

        cleanupVideoOnly();

        PauseTransition wait = new PauseTransition(Duration.millis(150));
        wait.setOnFinished(e -> startBackgroundVideoSafely());
        wait.play();
    }

    /**
     * Libera sólo el vídeo (MediaView + MediaPlayer).
     * La música se gestiona aparte.
     */
    private void cleanupVideoOnly() {
        try {
            if (backgroundVideo != null) backgroundVideo.setMediaPlayer(null);
        } catch (Exception ignore) {}

        // Centralizamos la liberación del vídeo en AssetsManager
        AssetsManager.stopVideo();
        videoPlayer = null;
    }

    // -----------------------------
    // Acciones de botones
    // -----------------------------

    /** Botón “Jugar”: abre la configuración de run. */
    @FXML
    private void onPlayClicked() {
        log("Jugar -> run_setup");
        SceneRouter.goWithFadeKeepSize(FXML_RUN_SETUP);
    }

    /** Botón “Perfiles”: vuelve a selección de perfil. */
    @FXML
    private void onProfilesClicked() {
        log("Perfiles -> profile_select");
        SceneRouter.goWithFadeKeepSize(FXML_PROFILES);
    }

    /** Botón “Colección”: abre la colección. */
    @FXML
    private void onCollectionClicked() {
        log("Colección -> collection");
        SceneRouter.goWithFadeKeepSize(FXML_COLLECTION);
    }

    /** Botón “Opciones”: abre overlay de settings. */
    @FXML
    private void onOptionsClicked() {
        if (settingsOverlay != null) return;
        if (root == null) return;

        settingsOverlay = OverlayRouter.showOverlay(root, FXML_SETTINGS, controller -> {
            if (controller instanceof SettingsController sc) {
                sc.setStatsService(com.layla.AppContext.stats());
                sc.setOverlayHost(root);
                sc.setOnClose(() -> {
                    OverlayRouter.closeOverlay(root, settingsOverlay);
                    settingsOverlay = null;
                });
            }
        });
    }

    /** Botón “Salir”: abre confirmación de salida. */
    @FXML
    private void onExitClicked() {
        if (exitOverlay != null) return;
        if (root == null) return;

        exitOverlay = OverlayRouter.showOverlay(root, FXML_EXIT_CONFIRM, controller -> {
            if (controller instanceof ExitConfirmController ec) {
                ec.setOnCancel(() -> {
                    OverlayRouter.closeOverlay(root, exitOverlay);
                    exitOverlay = null;
                });

                ec.setOnConfirm(() -> {
                    OverlayRouter.closeOverlay(root, exitOverlay);
                    exitOverlay = null;

                    // En TestFX a veces no interesa cerrar la JVM: se usa flag para salida segura.
                    boolean safeExit = Boolean.getBoolean("testfx.safeExit");
                    if (safeExit) {
                        safeCloseWindowOnly();
                        return;
                    }

                    // Al salir de la app: limpiamos vídeo y música.
                    cleanupVideoOnly();
                    AssetsManager.stopMusic();
                    Platform.exit();
                });
            }
        });
    }

    /**
     * Método auxiliar: vuelve al menú principal con fade manteniendo el tamaño actual.
     * Útil si otros controllers llaman a "volver al menú".
     */
    public void backToMenu() {
        SceneRouter.goWithFadeKeepSize(FXML_MAIN_MENU);
    }

    /** Cierra sólo la ventana (útil para tests). */
    private void safeCloseWindowOnly() {
        try {
            if (root != null && root.getScene() != null && root.getScene().getWindow() != null) {
                root.getScene().getWindow().hide();
            }
        } catch (Exception ignore) {}
    }

    private static void log(String msg) {
        System.out.println("[MainMenu] " + msg);
    }
}
