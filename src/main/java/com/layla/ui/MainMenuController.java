package com.layla.ui;

import com.layla.core.AssetsManager;

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
import com.layla.core.TestEnv;
import com.layla.services.StatsService;

public class MainMenuController {

    @FXML private StackPane root;
    @FXML private VBox menuBox;
    @FXML private Label titleLabel;
    @FXML private Button playBtn, optionsBtn, exitBtn;
    @FXML private MediaView backgroundVideo;

    private MediaPlayer videoPlayer;
    private boolean retriedVideoOnce = false;
    private Node settingsOverlay;
    private Node exitOverlay;
    private final StatsService statsService = com.layla.AppContext.stats();

    @FXML
    private void initialize() {
        System.out.println("[MainMenu] initialize()");

        // Fuente
        try {
            Font.loadFont(getClass().getResourceAsStream("/assets/fonts/main_font.ttf"), 16);
        } catch (Exception e) {
            System.err.println("[MainMenu] Font not found: " + e.getMessage());
        }

        // Vídeo (omitido en tests/headless)
        if (TestEnv.disableMedia()) {
            if (backgroundVideo != null) {
                backgroundVideo.setVisible(false);
                backgroundVideo.setManaged(false);
                backgroundVideo.setMediaPlayer(null);
            }
        } else {
            backgroundVideo.setPreserveRatio(true);
            backgroundVideo.fitWidthProperty().bind(root.widthProperty());
            backgroundVideo.fitHeightProperty().bind(root.heightProperty());
            backgroundVideo.setOpacity(0);

            Platform.runLater(() -> {
                PauseTransition delay = new PauseTransition(Duration.millis(120));
                delay.setOnFinished(e -> startBackgroundVideoSafely());
                delay.play();
            });
        }

        // Layout responsive
        menuBox.prefWidthProperty().unbind();
        menuBox.setMinWidth(300);
        menuBox.setMaxWidth(640);
        menuBox.prefWidthProperty().bind(min(640.0, max(300.0, root.widthProperty().multiply(0.5))));
        menuBox.setFillWidth(false);

        var btnWidthBinding = min(360.0, max(220.0, menuBox.widthProperty().multiply(0.55)));
        for (var b : new Button[]{playBtn, optionsBtn, exitBtn}) {
            b.setMaxWidth(Region.USE_PREF_SIZE);
            b.prefWidthProperty().bind(btnWidthBinding);
        }

        titleLabel.setWrapText(true);
        titleLabel.maxWidthProperty().bind(menuBox.widthProperty());

        // Música del menú
        AssetsManager.playMusic("menu.mp3", true);
        root.opacityProperty().set(1.0);
    }

    // ===================== VÍDEO FONDO =====================
    private void startBackgroundVideoSafely() {
        try {
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
        try {
            if (backgroundVideo != null) backgroundVideo.setMediaPlayer(null);
            if (videoPlayer != null) {
                videoPlayer.stop();
                try { videoPlayer.dispose(); } catch (Exception ignore) {}
            }
        } catch (Exception ignore) {}
        videoPlayer = null;
        PauseTransition wait = new PauseTransition(Duration.millis(150));
        wait.setOnFinished(e -> startBackgroundVideoSafely());
        wait.play();
    }

    // ====================== BOTONES ======================
    @FXML
    private void onPlayClicked() {
        System.out.println("[MainMenu] Play clicked");
        cleanupMedia();
        SceneRouter.goWithFadeKeepSize("ui/game.fxml");

        // Intro rápida + arranque música de piso
        SceneRouter.whenControllerIs(GameController.class, gc -> {
            var overlayLayer = gc.getOverlayLayer();
            if (overlayLayer == null) {
                gc.startFloorMusicIfNeeded();
                return;
            }
            var overlay = new javafx.scene.layout.StackPane();
            overlay.setStyle("-fx-background-color: black;");
            overlay.setOpacity(1.0);
            var rootPane = overlayLayer.getScene().getRoot();
            if (rootPane instanceof javafx.scene.layout.Region r) {
                overlay.prefWidthProperty().bind(r.widthProperty());
                overlay.prefHeightProperty().bind(r.heightProperty());
            }
            var vbox = new javafx.scene.layout.VBox(8);
            vbox.setAlignment(javafx.geometry.Pos.CENTER);
            vbox.setMouseTransparent(true);
            String[] INTRO_TITLES = {
                "Basement I","Te amo Maria","Apruebame pls",
                "Cargando partida...","Prepared to die?","Por nuestra futura Layla"
            };
            int idx = java.util.concurrent.ThreadLocalRandom.current().nextInt(INTRO_TITLES.length);
            var title = new javafx.scene.control.Label(INTRO_TITLES[idx]);
            title.getStyleClass().add("intro-title");
            var subtitle = new javafx.scene.control.Label("The Binding of Layla");
            subtitle.getStyleClass().add("intro-subtitle");
            vbox.getChildren().addAll(title, subtitle);
            overlay.getChildren().add(vbox);
            overlayLayer.getChildren().add(overlay);

            var fadeIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(350), vbox);
            fadeIn.setFromValue(0); fadeIn.setToValue(1);
            var hold   = new javafx.animation.PauseTransition(javafx.util.Duration.millis(1600));
            var fadeOut= new javafx.animation.FadeTransition(javafx.util.Duration.millis(300), vbox);
            fadeOut.setFromValue(1); fadeOut.setToValue(0);
            new javafx.animation.SequentialTransition(fadeIn, hold, fadeOut).play();

            final int SFX_MS = 5000;
            com.layla.core.AssetsManager.setSfxVolume(1.0);
            com.layla.core.AssetsManager.playSfx("game_start.wav");
            var finish = new javafx.animation.PauseTransition(javafx.util.Duration.millis(SFX_MS));
            finish.setOnFinished(ev -> {
                overlayLayer.getChildren().remove(overlay);
                gc.signalGameStart();
                gc.startFloorMusicIfNeeded();
            });
            finish.play();
        });
    }

    @FXML
    private void onOptionsClicked() {
        if (settingsOverlay != null) return; // ya abierto
        settingsOverlay = OverlayRouter.showOverlay(root, "ui/settings.fxml", controller -> {
            if (controller instanceof SettingsController sc) {
                sc.setStatsService(com.layla.AppContext.stats());
                sc.setOverlayHost(root); // <<<<< IMPORTANTE: host donde abrirá el Stats Panel
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
                    cleanupMedia();
                    javafx.application.Platform.exit();
                });
            }
        });
    }

    /** Vuelve al menú principal conservando tamaño. */
    public void backToMenu() {
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }

    private void cleanupMedia() {
        try {
            if (backgroundVideo != null) backgroundVideo.setMediaPlayer(null);
            if (videoPlayer != null) {
                videoPlayer.stop();
                try { videoPlayer.dispose(); } catch (Exception ignore) {}
            }
        } catch (Exception ignore) {}
        videoPlayer = null;
        AssetsManager.stopMusic();
    }
}
