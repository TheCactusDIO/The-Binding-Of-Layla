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
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.text.Font;
import javafx.util.Duration;

public class MainMenuController implements ViewLifecycle {

    @FXML private StackPane root;
    @FXML private VBox menuBox;
    @FXML private Label titleLabel;
    @FXML private Button playBtn, profilesBtn, collectionBtn, optionsBtn, exitBtn;
    @FXML private MediaView backgroundVideo;

    private MediaPlayer videoPlayer;
    private boolean retriedVideoOnce = false;
    private Node settingsOverlay;
    private Node exitOverlay;

    @FXML
    private void initialize() {
        System.out.println("[MainMenu] initialize()");

        try {
            Font.loadFont(getClass().getResourceAsStream("/assets/fonts/main_font.ttf"), 16);
        } catch (Exception e) {
            System.err.println("[MainMenu] Font not found: " + e.getMessage());
        }

        // --- (Opcional) Forzar textos ES-ES aquí si tu FXML está en inglés ---
        if (titleLabel != null) titleLabel.setText("THE BINDING OF LAYLA"); // si quieres traducirlo luego, lo cambiamos
        if (playBtn != null) playBtn.setText("Jugar");
        if (profilesBtn != null) profilesBtn.setText("Perfiles");
        if (collectionBtn != null) collectionBtn.setText("Colección");
        if (optionsBtn != null) optionsBtn.setText("Opciones");
        if (exitBtn != null) exitBtn.setText("Salir");

        // --- Layout responsive botones ---
        menuBox.prefWidthProperty().unbind();
        menuBox.setMinWidth(300);
        menuBox.setMaxWidth(640);
        menuBox.prefWidthProperty().bind(min(640.0, max(300.0, root.widthProperty().multiply(0.5))));
        menuBox.setFillWidth(false);

        var btnWidthBinding = min(360.0, max(220.0, menuBox.widthProperty().multiply(0.55)));
        for (var b : new Button[]{playBtn, profilesBtn, collectionBtn, optionsBtn, exitBtn}) {
            if (b != null) {
                b.setMaxWidth(Region.USE_PREF_SIZE);
                b.prefWidthProperty().bind(btnWidthBinding);
            }
        }

        titleLabel.setWrapText(true);
        titleLabel.maxWidthProperty().bind(menuBox.widthProperty());

        // --- Vídeo (solo si no estamos en headless/tests) ---
        if (TestEnv.disableMedia()) {
            hideVideoNode();
        } else {
            setupBackgroundVideoNode();
            Platform.runLater(() -> {
                PauseTransition delay = new PauseTransition(Duration.millis(120));
                delay.setOnFinished(e -> startBackgroundVideoSafely());
                delay.play();
            });
        }

        // ✅ CLAVE: no reiniciar si ya está sonando (ProfileSelect -> MainMenu sin corte)
        AssetsManager.ensureMusic("menu.mp3", true);

        root.opacityProperty().set(1.0);
    }

    @Override
    public void onEnter() {
        // Por si vuelves al menú desde el juego, garantizamos música de menú
        AssetsManager.ensureMusic("menu.mp3", true);
    }

    @Override
    public void onExit() {
        // Salimos del main menu: liberamos VIDEO, pero NO paramos la música del menú
        cleanupVideoOnly();
    }

    private void hideVideoNode() {
        if (backgroundVideo != null) {
            backgroundVideo.setVisible(false);
            backgroundVideo.setManaged(false);
            backgroundVideo.setMediaPlayer(null);
        }
    }

    private void setupBackgroundVideoNode() {
        backgroundVideo.setPreserveRatio(true);
        backgroundVideo.fitWidthProperty().bind(root.widthProperty());
        backgroundVideo.fitHeightProperty().bind(root.heightProperty());
        backgroundVideo.setOpacity(0);
    }

    private void startBackgroundVideoSafely() {
        try {
            if (backgroundVideo == null) return;

            backgroundVideo.setMediaPlayer(null);
            var url = getClass().getResource("/assets/videos/menu.mp4");
            if (url == null) {
                System.err.println("[MainMenu] Video not found at /assets/videos/menu.mp4");
                return;
            }

            var media = new Media(url.toExternalForm());
            videoPlayer = new MediaPlayer(media);
            videoPlayer.setMute(true);
            videoPlayer.setCycleCount(MediaPlayer.INDEFINITE);

            media.errorProperty().addListener((o, ov, nv) -> {
                if (nv != null) System.err.println("[MainMenu] Media ERROR: " + nv.getMessage());
            });
            videoPlayer.errorProperty().addListener((o, ov, nv) -> {
                if (nv != null) {
                    System.err.println("[MainMenu] MediaPlayer ERROR: " + nv.getMessage());
                    retryVideoPlayerOnce();
                }
            });

            backgroundVideo.setMediaPlayer(videoPlayer);
            videoPlayer.setOnReady(() -> {
                videoPlayer.play();
                FadeTransition ft = new FadeTransition(Duration.seconds(1.0), backgroundVideo);
                ft.setFromValue(0);
                ft.setToValue(1);
                ft.play();
            });
            videoPlayer.setOnEndOfMedia(() -> {
                videoPlayer.seek(Duration.ZERO);
                videoPlayer.play();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void retryVideoPlayerOnce() {
        if (retriedVideoOnce) return;
        retriedVideoOnce = true;

        cleanupVideoOnly();

        PauseTransition wait = new PauseTransition(Duration.millis(150));
        wait.setOnFinished(e -> startBackgroundVideoSafely());
        wait.play();
    }

    @FXML
    private void onPlayClicked() {
        System.out.println("[MainMenu] Play clicked -> Going to Run Setup");
        // El vídeo lo limpia onExit() vía SceneRouter, al cambiar de escena.
        SceneRouter.goWithFadeKeepSize("ui/run_setup.fxml");
    }

    @FXML
    private void onProfilesClicked() {
        System.out.println("[MainMenu] Profiles clicked");
        // NO paramos música. El vídeo se limpia en onExit().
        SceneRouter.goWithFadeKeepSize("ui/profile_select.fxml");
    }

    @FXML
    private void onCollectionClicked() {
        System.out.println("[MainMenu] Collection clicked");
        // NO paramos música. El vídeo se limpia en onExit().
        SceneRouter.goWithFadeKeepSize("ui/collection.fxml");
    }

    @FXML
    private void onOptionsClicked() {
        if (settingsOverlay != null) return;
        settingsOverlay = OverlayRouter.showOverlay(root, "ui/settings.fxml", controller -> {
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

    @FXML
    private void onExitClicked() {
        if (exitOverlay != null) return;
        exitOverlay = OverlayRouter.showOverlay(root, "ui/exit_confirm.fxml", controller -> {
            if (controller instanceof ExitConfirmController ec) {
                ec.setOnCancel(() -> {
                    OverlayRouter.closeOverlay(root, exitOverlay);
                    exitOverlay = null;
                });
                ec.setOnConfirm(() -> {
                    OverlayRouter.closeOverlay(root, exitOverlay);
                    exitOverlay = null;

                    boolean safeExit = Boolean.getBoolean("testfx.safeExit");
                    if (safeExit) {
                        try {
                            if (root != null && root.getScene() != null && root.getScene().getWindow() != null) {
                                root.getScene().getWindow().hide();
                            }
                        } catch (Exception ignore) {}
                        return;
                    }

                    // Al salir de la app, sí limpiamos TODO.
                    cleanupVideoOnly();
                    AssetsManager.stopMusic();

                    Platform.exit();
                });
            }
        });
    }

    public void backToMenu() {
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }

    private void cleanupVideoOnly() {
        try {
            if (backgroundVideo != null) backgroundVideo.setMediaPlayer(null);
            if (videoPlayer != null) {
                videoPlayer.stop();
                try { videoPlayer.dispose(); } catch (Exception ignore) {}
            }
        } catch (Exception ignore) {}
        videoPlayer = null;
    }
}
