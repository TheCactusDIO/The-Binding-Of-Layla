package com.layla.ui;

import com.layla.core.AssetsManager;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import static javafx.beans.binding.Bindings.max;
import static javafx.beans.binding.Bindings.min;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.text.Font;
import javafx.util.Duration;

public class MainMenuController {


    @FXML private StackPane root;
    @FXML private VBox menuBox;
    @FXML private Label titleLabel;
    @FXML private Button playBtn, optionsBtn, exitBtn;
    @FXML private MediaView backgroundVideo;

    private MediaPlayer videoPlayer;
    private boolean retriedVideoOnce = false;
    private Node settingsOverlay;

    @FXML
    private void initialize() {
        System.out.println("[MainMenu] initialize()");

        // ---------- Fuente personalizada ----------
        try {
            Font.loadFont(getClass().getResourceAsStream("/assets/fonts/main_font.ttf"), 16);
            System.out.println("[MainMenu] Custom font loaded successfully");
        } catch (Exception e) {
            System.err.println("[MainMenu] Font not found: " + e.getMessage());
        }

        // ---------- Tamaños responsivos ----------
        menuBox.prefWidthProperty().unbind();
        menuBox.setMinWidth(300);
        menuBox.setMaxWidth(640);
        menuBox.prefWidthProperty().bind(min(640.0, max(300.0, root.widthProperty().multiply(0.5))));
        menuBox.setFillWidth(false);

        // Botones responsivos
        var btnWidthBinding = min(360.0, max(220.0, menuBox.widthProperty().multiply(0.55)));
        for (var b : new Button[]{playBtn, optionsBtn, exitBtn}) {
            b.setMaxWidth(Region.USE_PREF_SIZE);
            b.prefWidthProperty().bind(btnWidthBinding);
        }

        // Título adaptable
        titleLabel.setWrapText(true);
        titleLabel.maxWidthProperty().bind(menuBox.widthProperty());

        // ---------- Fondo de vídeo ----------
        backgroundVideo.setPreserveRatio(true);
        backgroundVideo.fitWidthProperty().bind(root.widthProperty());
        backgroundVideo.fitHeightProperty().bind(root.heightProperty());
        backgroundVideo.setOpacity(0);

        // Esperar un instante tras cargar la escena para iniciar el vídeo (evita errores intermitentes)
        Platform.runLater(() -> {
            PauseTransition delay = new PauseTransition(Duration.millis(120));
            delay.setOnFinished(e -> startBackgroundVideoSafely());
            delay.play();
        });

        // ---------- Música ----------
        AssetsManager.playMusic("menu.mp3", true);
        root.opacityProperty().set(1.0);
    }

    // ===========================================================
    // ============    VÍDEO DE FONDO CON RECUPERACIÓN   =========
    // ===========================================================
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
                System.out.println("[MainMenu] Background video ready → play()");
                FadeTransition ft = new FadeTransition(Duration.seconds(1.0), backgroundVideo);
                ft.setFromValue(0);
                ft.setToValue(1);
                ft.play();
            });

            videoPlayer.setOnEndOfMedia(() -> {
                videoPlayer.seek(Duration.ZERO);
                videoPlayer.play();
            });

            System.out.println("[MainMenu] Background video setup complete");
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
                videoPlayer = null;
            }
        } catch (Exception ignore) {}

        PauseTransition wait = new PauseTransition(Duration.millis(150));
        wait.setOnFinished(e -> startBackgroundVideoSafely());
        wait.play();
    }

    // ===========================================================
    // ======================   BOTONES   ========================
    // ===========================================================
    @FXML
    private void onPlayClicked() {
        System.out.println("[MainMenu] Play clicked");
        cleanupMedia();
        SceneRouter.goWithFade("ui/game.fxml", 1280, 720);
    }

    @FXML
    private void onOptionsClicked() {
        if (settingsOverlay != null) return; // ya abierto
        settingsOverlay = OverlayRouter.showOverlay(root, "ui/settings.fxml", controller -> {
            if (controller instanceof SettingsController sc) {
                sc.setOnClose(() -> {
                    OverlayRouter.closeOverlay(root, settingsOverlay);
                    settingsOverlay = null;
                });
            }
        });
    }

    @FXML
    private void onExitClicked() {
        System.out.println("[MainMenu] Exit clicked");
        var confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to exit?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.setTitle("Confirm Exit");

        SceneRouter.fadeOutCurrent(0.18, 200);
        UIStyles.applyDialogStyle(confirm.getDialogPane());

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                cleanupMedia();
                Platform.exit();
                System.exit(0);
            } else {
                SceneRouter.fadeInCurrent(0.18, 200);
            }
        });
    }

    // ===========================================================
    // ===================   LIMPIEZA DE MEDIA   =================
    // ===========================================================
    private void cleanupMedia() {
        try {
            if (backgroundVideo != null) {
            backgroundVideo.setMediaPlayer(null);
            }
            if (videoPlayer != null) {
                videoPlayer.stop();
                try {
                    videoPlayer.dispose();
                } catch (Exception ignore) {}
            }
        } catch (Exception ignore) {}
        videoPlayer = null;
        AssetsManager.stopMusic();
    }
}
