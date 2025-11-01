package com.layla.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;

import javafx.concurrent.Task;
import javafx.scene.image.Image;
import javafx.scene.media.AudioClip;

public class AssetsManager {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "assets-loader");
        t.setDaemon(true);
        return t;
    });

    public static class Manifest {
        public List<String> images = new ArrayList<>();
        public List<String> sounds = new ArrayList<>();
    }

    public Task<Void> createLoadTask() {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                System.out.println("[AssetsManager] Starting asset loading task");
                updateMessage("Reading manifest...");

                Manifest manifest = readManifest();
                if (manifest == null) {
                    String error = "assets/manifest.json not found or is invalid";
                    System.err.println("[AssetsManager] " + error);
                    throw new IllegalStateException(error);
                }

                // GPT START — listas null-safe y progreso inicial
                List<String> images = (manifest.images == null) ? List.of() : manifest.images;
                List<String> sounds = (manifest.sounds == null) ? List.of() : manifest.sounds;

                int total = images.size() + sounds.size();
                if (total == 0) total = 1; // evita división por cero
                updateProgress(0, total);
                // GPT END

                System.out.println("[AssetsManager] Found " + images.size() + " images and " + sounds.size() + " sounds to load");

                if (images.isEmpty() && sounds.isEmpty()) {
                    System.out.println("[AssetsManager] No assets to load");
                    updateMessage("No assets to load");
                    updateProgress(1, 1);
                    return null;
                }

                int done = 0;

                // load images
                updateMessage("Loading images...");
                for (String path : images) {
                    if (isCancelled()) {
                        System.out.println("[AssetsManager] Task cancelled while loading images");
                        break;
                    }

                    System.out.println("[AssetsManager] Loading image: " + path);
                    updateMessage("Loading: " + path);

                    try {
                        URL url = getClass().getResource("/" + path);
                        if (url == null)
                            throw new Exception("Image file not found: " + path);

                        Image img = new Image(url.toExternalForm(), false);
                        if (img.isError()) {
                            Throwable cause = img.getException();
                            String msg = (cause != null && cause.getMessage() != null)
                                    ? cause.getMessage()
                                    : "unknown error";
                            throw new Exception("Failed to decode image: " + msg);
                        }
                        System.out.println("[AssetsManager] Successfully loaded image: " + path);
                    } catch (Exception e) {
                        System.err.println("[AssetsManager] Error loading image " + path + ": " + e.getMessage());
                        throw new Exception("Failed to load image " + path + ": " + e.getMessage());
                    }

                    done++;
                    updateProgress(done, total);

                }

                // load sounds
                updateMessage("Loading sounds...");
                for (String path : sounds) {
                    if (isCancelled()) {
                        System.out.println("[AssetsManager] Task cancelled while loading sounds");
                        break;
                    }

                    System.out.println("[AssetsManager] Loading sound: " + path);
                    updateMessage("Loading: " + path);

                    URL url = getClass().getResource("/" + path);
                    if (url == null)
                        throw new Exception("Sound file not found: " + path);

                    try {
                        new AudioClip(url.toExternalForm());
                        System.out.println("[AssetsManager] Successfully loaded sound: " + path);
                    } catch (Exception e) {
                        System.err.println("[AssetsManager] Failed to load sound " + path + ": " + e.getMessage());
                        throw new Exception("Failed to load sound " + path + ": " + e.getMessage());
                    }

                    done++;
                    updateProgress(done, total);
                    
                }

                return null;
            }

            @Override
            protected void cancelled() {
                super.cancelled();
                shutdownExecutor();
            }

            @Override
            protected void failed() {
                super.failed();
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
        System.out.println("[AssetsManager] Reading assets/manifest.json");
        try (InputStream is = getClass().getResourceAsStream("/assets/manifest.json")) {
            if (is == null) {
                System.err.println("[AssetsManager] manifest.json not found in resources");
                return null;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                Gson gson = new Gson();
                Manifest m = gson.fromJson(r, Manifest.class);
                if (m == null) {
                    System.err.println("[AssetsManager] Failed to parse manifest.json: Empty or invalid JSON");
                    return null;
                }
                if (m.images == null) m.images = new ArrayList<>();
                if (m.sounds == null) m.sounds = new ArrayList<>();

                System.out.println("[AssetsManager] Successfully read manifest with "
                        + m.images.size() + " images and " + m.sounds.size() + " sounds");
                return m;
            }
        } catch (Exception e) {
            System.err.println("[AssetsManager] Error reading manifest: " + e.getMessage());
            return null;
        }
    }

    public void submit(Task<Void> task) {
        executor.submit(task);
    }

    // GPT START — mejor cierre con logs
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
    // GPT END
}
