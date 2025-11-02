package com.layla.ui;

import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.shape.Rectangle;

public class IntroVideoController {
    @FXML private StackPane root;
    @FXML private MediaView mediaView;
    @FXML private Rectangle bg;

    private MediaPlayer mp;
    private double durationMs = 1500; // fallback

    @FXML
    private void initialize() {
        System.out.println("[IntroVideo] initialize()");
        // Bindeos defensivos
        if (root != null) {
            root.setPickOnBounds(true);     // bloquea input por defecto
            root.setMouseTransparent(false);
        }
        if (bg != null && root != null) {
            bg.widthProperty().bind(root.widthProperty());
            bg.heightProperty().bind(root.heightProperty());
        }
        if (mediaView != null && root != null) {
            mediaView.setPreserveRatio(true);
            mediaView.fitWidthProperty().bind(root.widthProperty());
            mediaView.fitHeightProperty().bind(root.heightProperty());
        }
    }

    /** Reproduce assets/videos/<fileName> y devuelve el MediaPlayer. */
    public MediaPlayer play(String fileName) {
        try {
            var url = getClass().getResource("/assets/videos/" + fileName);
            System.out.println("[IntroVideo] URL: " + url + " (file=" + fileName + ")");
            if (url == null) return null;

            var media = new Media(url.toExternalForm());
            mp = new MediaPlayer(media);
            mp.setCycleCount(1);
            mp.setMute(true); // el audio de la intro lo gestiona tu SFX, no el vídeo
            if (mediaView != null) mediaView.setMediaPlayer(mp);

            mp.setOnError(() -> System.err.println("[IntroVideo] Media error: " + mp.getError()));
            mp.setOnReady(() -> {
                try {
                    var d = mp.getTotalDuration();
                    double reported = (d != null) ? d.toMillis() : -1;
                    if (reported > 0 && reported < 60000) durationMs = reported;
                } catch (Exception ignore) {}
                System.out.println("[IntroVideo] onReady: " + fileName + " (" + durationMs + " ms)");
            });

            // El overlay lo cierra MainMenu; aquí solo reproducimos
            mp.play();
            return mp;
        } catch (Exception e) {
            System.err.println("[IntroVideo] Error: " + e.getMessage());
            return null;
        }
    }

    public void stopAndDispose() {
        try {
            if (mp != null) {
                mp.stop();
                mp.dispose();
                mp = null;
            }
        } catch (Exception ignore) {}
    }

    public double getDurationMillis() { return durationMs; }
}
