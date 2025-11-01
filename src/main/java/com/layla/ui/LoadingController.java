package com.layla.ui;

import java.net.URL;
import java.util.ResourceBundle;

import com.layla.core.AssetsManager;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
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
    private final StringProperty currentAsset = new SimpleStringProperty("");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("[LoadingController] Initializing loading screen");
        startTimeNs = System.nanoTime();

        // GPT START — enlazar directamente statusLabel con task.message
        statusLabel.textProperty().bind(currentAsset);
        // GPT END

        Task<Void> loadTask = assetsManager.createLoadTask();

        // GPT START — escucha de mensajes y progreso
        loadTask.messageProperty().addListener((obs, old, message) -> {
            System.out.println("[LoadingController] Task message: " + message);
            currentAsset.set(message);
        });

        progressBar.progressProperty().bind(loadTask.progressProperty());
        loadTask.progressProperty().addListener((obs, old, newValue) -> {
            System.out.println("[LoadingController] Progress updated: " +
                    Math.round(newValue.doubleValue() * 100) + "%");
        });
        // GPT END

        loadTask.setOnSucceeded(evt -> {
            System.out.println("[LoadingController] Assets loaded successfully");
            long elapsedMs = (System.nanoTime() - startTimeNs) / 1_000_000;
            long remaining = MIN_SPLASH_MS - elapsedMs;

            remaining = Math.max(100, remaining);
            currentAsset.set("Loading complete!");

            PauseTransition pt = new PauseTransition(Duration.millis(remaining));
            pt.setOnFinished(e -> {
                try {
                    System.out.println("[LoadingController] Transitioning to main menu");
                    assetsManager.shutdownExecutor(); // GPT: asegurar cierre explícito aquí también
                    SceneRouter.go("ui/main_menu.fxml", 1280, 720);
                } catch (Exception ex) {
                    System.err.println("[LoadingController] Failed to load main menu: " + ex.getMessage());
                    ErrorController.setLastError("Failed to load main menu: " + ex.getMessage());
                    SceneRouter.go("ui/error.fxml", 800, 400);
                }
            });
            pt.play();
        });

        loadTask.setOnFailed(evt -> {
            Throwable ex = loadTask.getException();
            String msg = ex == null ? "Unknown error loading assets" : ex.getMessage();
            System.err.println("[LoadingController] Asset loading failed: " + msg);
            ErrorController.setLastError(msg);
            assetsManager.shutdownExecutor(); // GPT: cerrar también en fallo
            Platform.runLater(() -> SceneRouter.go("ui/error.fxml", 800, 400));
        });

        System.out.println("[LoadingController] Submitting load task to executor");
        assetsManager.submit(loadTask);
    }
}
