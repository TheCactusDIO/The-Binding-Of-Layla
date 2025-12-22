package com.layla.ui;

import java.net.URL;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Overlay de vídeo de intro.
 *
 * Responsabilidad:
 * - Preparar el layout (fondo negro + MediaView a pantalla completa).
 * - Reproducir un vídeo desde /assets/videos/<fileName>.
 *
 * NO decide cuándo cerrar el overlay: eso lo hace quien lo muestra,
 * aunque puedes engancharte con callbacks (onFinished/onError).
 */
public class IntroVideoController {

    private static final Logger LOG = Logger.getLogger(IntroVideoController.class.getName());

    /** Duración usada si MediaPlayer no puede reportar el total (ms). */
    private static final double FALLBACK_DURATION_MS = 1500.0;

    /** Para evitar valores absurdos si el medio reporta algo raro. */
    private static final double MAX_REASONABLE_DURATION_MS = 60_000.0;

    // =========================
    // FXML
    // =========================

    @FXML private StackPane root;
    @FXML private MediaView mediaView;
    @FXML private Rectangle bg;

    // =========================
    // Estado
    // =========================

    private MediaPlayer player;

    /** Duración (ms) del último vídeo cargado (o fallback). */
    private double durationMs = FALLBACK_DURATION_MS;

    // =========================
    // Callbacks opcionales
    // =========================

    private Runnable onReady = () -> {};
    private Runnable onFinished = () -> {};
    private Runnable onError = () -> {};

    /**
     * Se ejecuta cuando el MediaPlayer llega a READY.
     * Útil si quieres esperar a tener duración real antes de programar el cierre.
     */
    public void setOnReady(Runnable r) {
        this.onReady = (r != null) ? r : () -> {};
    }

    /**
     * Se ejecuta cuando termina el vídeo (EndOfMedia).
     */
    public void setOnFinished(Runnable r) {
        this.onFinished = (r != null) ? r : () -> {};
    }

    /**
     * Se ejecuta si hay error cargando o reproduciendo el vídeo.
     * Normalmente usarás esto para saltar la intro y continuar.
     */
    public void setOnError(Runnable r) {
        this.onError = (r != null) ? r : () -> {};
    }

    /**
     * Inicializa bindings defensivos para que el overlay cubra siempre todo.
     */
    @FXML
    private void initialize() {
        // Bloquea input hacia debajo (overlay sólido)
        if (root != null) {
            root.setPickOnBounds(true);
            root.setMouseTransparent(false);
        }

        // Fondo negro siempre a tamaño completo
        if (bg != null && root != null) {
            bg.widthProperty().bind(root.widthProperty());
            bg.heightProperty().bind(root.heightProperty());
        }

        // MediaView full-screen
        if (mediaView != null && root != null) {
            mediaView.setPreserveRatio(true);
            mediaView.fitWidthProperty().bind(root.widthProperty());
            mediaView.fitHeightProperty().bind(root.heightProperty());
        }
    }

    /**
     * Reproduce /assets/videos/<fileName>.
     *
     * - Si ya hay un vídeo reproduciéndose, se para y libera antes.
     * - Devuelve el MediaPlayer creado (o null si falla).
     */
    public MediaPlayer play(String fileName) {
        Objects.requireNonNull(fileName, "fileName");

        // JavaFX Media debe crearse/operarse en el FX thread
        if (!Platform.isFxApplicationThread()) {
            LOG.warning("[IntroVideo] play() llamado fuera del FX thread. Usa Platform.runLater.");
            return null;
        }

        stopAndDispose();

        URL url = getClass().getResource("/assets/videos/" + fileName);
        if (url == null) {
            LOG.warning("[IntroVideo] No se encontró el vídeo: /assets/videos/" + fileName);
            onError.run();
            return null;
        }

        try {
            Media media = new Media(url.toExternalForm());
            MediaPlayer mp = new MediaPlayer(media);

            this.player = mp;
            this.durationMs = FALLBACK_DURATION_MS;

            if (mediaView != null) {
                mediaView.setMediaPlayer(mp);
            }

            // Config base
            mp.setCycleCount(1);
            mp.setMute(true); // el audio lo gestionas tú fuera del vídeo

            // Errores (Media y MediaPlayer)
            media.setOnError(() -> {
                LOG.log(Level.SEVERE, "[IntroVideo] Media error: " + media.getError(), media.getError());
                onError.run();
            });

            mp.setOnError(() -> {
                LOG.log(Level.SEVERE, "[IntroVideo] MediaPlayer error: " + mp.getError(), mp.getError());
                onError.run();
            });

            // Ready: leer duración real
            mp.setOnReady(() -> {
                durationMs = computeDurationMillis(mp.getTotalDuration(), FALLBACK_DURATION_MS);
                onReady.run();
            });

            // Fin del vídeo
            mp.setOnEndOfMedia(() -> onFinished.run());

            mp.play();
            return mp;

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[IntroVideo] Error creando MediaPlayer para: " + fileName, e);
            onError.run();
            return null;
        }
    }

    /**
     * Para y libera el MediaPlayer actual (si existe).
     * Seguro llamar varias veces.
     */
    public void stopAndDispose() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::stopAndDispose);
            return;
        }

        try {
            if (player != null) {
                try { player.stop(); } catch (Exception ignore) {}
                try { player.dispose(); } catch (Exception ignore) {}
            }
        } finally {
            player = null;
            if (mediaView != null) {
                mediaView.setMediaPlayer(null);
            }
        }
    }

    /**
     * Devuelve la duración (ms) del último vídeo cargado.
     * Si aún no estaba READY, será el fallback.
     */
    public double getDurationMillis() {
        return durationMs;
    }

    // =========================
    // Helpers privados
    // =========================

    /**
     * Calcula duración en ms con validación básica.
     */
    private static double computeDurationMillis(Duration total, double fallbackMs) {
        try {
            double ms = (total != null) ? total.toMillis() : -1;
            if (ms > 0 && ms <= MAX_REASONABLE_DURATION_MS) return ms;
        } catch (Exception ignore) {}
        return fallbackMs;
    }
}
