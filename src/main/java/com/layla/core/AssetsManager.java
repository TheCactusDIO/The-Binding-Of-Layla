package com.layla.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;

import javafx.concurrent.Task;
import javafx.scene.image.Image;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.text.Font;

/**
 * Global asset manager for images, sounds, fonts, and media.
 * Provides preloading (from manifest) and reusable playback for music and videos.
 */
public class AssetsManager {

    private static final Map<String, Image> imageCache = new HashMap<>();
    private static final Map<String, AudioClip> soundCache = new HashMap<>();
    private static final Map<String, Font> fontCache = new HashMap<>();

    private static MediaPlayer backgroundMusic;
    private static MediaPlayer videoPlayer;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "assets-loader");
        t.setDaemon(true);
        return t;
    });

    // Manifest inner class
    public static class Manifest {
        public List<String> images = new ArrayList<>();
        public List<String> sounds = new ArrayList<>();
    }

    // Task to preload assets listed in manifest.json
    public Task<Void> createLoadTask() {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                System.out.println("[AssetsManager] Starting asset loading task");
                updateMessage("Reading manifest...");

                Manifest manifest = readManifest();
                if (manifest == null) {
                    throw new IllegalStateException("assets/manifest.json not found or invalid");
                }

                List<String> images = Optional.ofNullable(manifest.images).orElse(List.of());
                List<String> sounds = Optional.ofNullable(manifest.sounds).orElse(List.of());
                int total = Math.max(1, images.size() + sounds.size());

                int done = 0;

                for (String path : images) {
                    loadImage(path);
                    done++;
                    updateProgress(done, total);
                    updateMessage("Loaded image: " + path);
                }

                for (String path : sounds) {
                    loadSound(path);
                    done++;
                    updateProgress(done, total);
                    updateMessage("Loaded sound: " + path);
                }

                updateMessage("All assets loaded.");
                updateProgress(1, 1);
                shutdownExecutor();
                return null;
            }

            @Override
            protected void failed() {
                super.failed();
                shutdownExecutor();
            }

            @Override
            protected void cancelled() {
                super.cancelled();
                shutdownExecutor();
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                shutdownExecutor();
            }
        };
    }

    private Manifest readManifest() {
        System.out.println("[AssetsManager] Reading manifest.json...");
        try (InputStream is = getClass().getResourceAsStream("/assets/manifest.json")) {
            if (is == null) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return new Gson().fromJson(reader, Manifest.class);
            }
        } catch (Exception e) {
            System.err.println("[AssetsManager] Error reading manifest: " + e.getMessage());
            return null;
        }
    }

    // GPT — BEGIN: make executor controls public
    public void submit(javafx.concurrent.Task<?> task) {
        executor.submit(task);
    }

    public void shutdownExecutor() {
        try {
            if (!executor.isShutdown()) {
                System.out.println("[AssetsManager] Shutting down executor");
                executor.shutdownNow();
            } else {
                System.out.println("[AssetsManager] Executor already shut down");
            }
        } catch (Exception e) {
            System.err.println("[AssetsManager] Error on shutdown: " + e.getMessage());
        }
    }
    // GPT — END

    // ======== IMAGE MANAGEMENT ========
    public static Image loadImage(String relativePath) {
        return imageCache.computeIfAbsent(relativePath, path -> {
            try {
                URL url = AssetsManager.class.getResource("/" + path);
                if (url == null) throw new Exception("Image not found: " + path);
                Image img = new Image(url.toExternalForm(), false);
                System.out.println("[AssetsManager] Cached image: " + path);
                return img;
            } catch (Exception e) {
                System.err.println("[AssetsManager] Error loading image: " + e.getMessage());
                return null;
            }
        });
    }

    // ======== SOUND MANAGEMENT ========
    public static AudioClip loadSound(String relativePath) {
        return soundCache.computeIfAbsent(relativePath, path -> {
            try {
                URL url = AssetsManager.class.getResource("/" + path);
                if (url == null) throw new Exception("Sound not found: " + path);
                System.out.println("[AssetsManager] Cached sound: " + path);
                return new AudioClip(url.toExternalForm());
            } catch (Exception e) {
                System.err.println("[AssetsManager] Error loading sound: " + e.getMessage());
                return null;
            }
        });
    }

    // ======== FONT MANAGEMENT ========
    public static Font loadFont(String fileName, double size) {
        String key = fileName + "@" + size;
        return fontCache.computeIfAbsent(key, k -> {
            try (InputStream is = AssetsManager.class.getResourceAsStream("/assets/fonts/" + fileName)) {
                if (is == null) throw new Exception("Font not found: " + fileName);
                Font f = Font.loadFont(is, size);
                System.out.println("[AssetsManager] Loaded font: " + fileName);
                return f;
            } catch (Exception e) {
                System.err.println("[AssetsManager] Error loading font: " + e.getMessage());
                return Font.getDefault();
            }
        });
    }

    // ======== MUSIC MANAGEMENT ========
    public static void playMusic(String fileName, boolean loop) {
        stopMusic();
        try {
            URL url = AssetsManager.class.getResource("/assets/music/" + fileName);
            if (url == null) throw new Exception("Music not found: " + fileName);
            backgroundMusic = new MediaPlayer(new Media(url.toExternalForm()));
            backgroundMusic.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
            backgroundMusic.setVolume(0.6);
            backgroundMusic.play();
            System.out.println("[AssetsManager] Playing music: " + fileName + " (loop=" + loop + ")");
        } catch (Exception e) {
            System.err.println("[AssetsManager] Error playing music: " + e.getMessage());
        }
    }

    public static void stopMusic() {
        if (backgroundMusic != null) {
            backgroundMusic.stop();
            backgroundMusic.dispose();
            backgroundMusic = null;
            System.out.println("[AssetsManager] Music stopped");
        }
    }

    // ======== VIDEO MANAGEMENT ========
    public static MediaPlayer playVideo(String fileName, boolean loop) {
        stopVideo();
        try {
            URL url = AssetsManager.class.getResource("/assets/videos/" + fileName);
            if (url == null) throw new Exception("Video not found: " + fileName);
            videoPlayer = new MediaPlayer(new Media(url.toExternalForm()));
            videoPlayer.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
            videoPlayer.setMute(true);
            videoPlayer.play();
            System.out.println("[AssetsManager] Playing video: " + fileName);
            return videoPlayer;
        } catch (Exception e) {
            System.err.println("[AssetsManager] Error playing video: " + e.getMessage());
            return null;
        }
    }

    public static void stopVideo() {
        if (videoPlayer != null) {
            videoPlayer.stop();
            videoPlayer.dispose();
            videoPlayer = null;
            System.out.println("[AssetsManager] Video stopped");
        }
    }
}
