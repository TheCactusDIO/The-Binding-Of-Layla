package com.layla.ui;

import java.net.URL;
import java.util.ResourceBundle;

import com.layla.core.AssetsManager;

import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.util.Duration;

public class LoadingController implements Initializable {

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label statusLabel;

    private final AssetsManager assetsManager = new AssetsManager();
    private long startTimeNs;
    private static final long MIN_SPLASH_MS = 800;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("[LoadingController] Initializing loading screen");
        startTimeNs = System.nanoTime();

        Task<Void> loadTask = assetsManager.createLoadTask();

        statusLabel.setText("Preparing...");
        statusLabel.textProperty().bind(loadTask.messageProperty());
        progressBar.setProgress(0);
        progressBar.progressProperty().bind(loadTask.progressProperty());

        loadTask.setOnSucceeded(evt -> {
            System.out.println("[LoadingController] Assets loaded successfully");
            long elapsedMs = (System.nanoTime() - startTimeNs) / 1_000_000;
            long remaining = Math.max(100, MIN_SPLASH_MS - elapsedMs);

            statusLabel.textProperty().unbind();
            statusLabel.setText("Loading complete!");

            PauseTransition pt = new PauseTransition(Duration.millis(remaining));
            pt.setOnFinished(e -> {
                try {
                    System.out.println("[LoadingController] Transitioning to profile selection");
                    // CAMBIO: Ahora vamos a la selección de perfil
                    SceneRouter.goWithFade("ui/profile_select.fxml", 1280, 720);
                } catch (Exception ex) {
                    System.err.println("[LoadingController] Failed to load next scene: " + ex.getMessage());
                    ErrorController.setLastError("Failed to load scene: " + ex.getMessage());
                    SceneRouter.goWithFade("ui/error.fxml", 800, 400);
                }
            });
            pt.play();
        });

        loadTask.setOnFailed(evt -> {
            Throwable ex = loadTask.getException();
            String msg = ex == null ? "Unknown error loading assets" : ex.getMessage();
            System.err.println("[LoadingController] Asset loading failed: " + msg);
            ErrorController.setLastError(msg);
            SceneRouter.go("ui/error.fxml", 800, 400);
        });

        assetsManager.submit(loadTask);
    }
}
