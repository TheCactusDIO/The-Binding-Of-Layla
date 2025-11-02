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
    private static final long MIN_SPLASH_MS = 800; // ms mínimos visibles del splash

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("[LoadingController] Initializing loading screen");
        startTimeNs = System.nanoTime();

        // 1) Crear la tarea de carga
        Task<Void> loadTask = assetsManager.createLoadTask();

        // 2) UI bindings: label y barra
        statusLabel.setText("Preparing...");                 // feedback inmediato
        statusLabel.textProperty().bind(loadTask.messageProperty());
        progressBar.setProgress(0);                          // arranca vacía
        progressBar.progressProperty().bind(loadTask.progressProperty());

        // 3) Logs de depuración (opcionales)
        loadTask.messageProperty().addListener((obs, old, msg) ->
            System.out.println("[LoadingController] Task message: " + msg)
        );
        loadTask.progressProperty().addListener((obs, old, p) ->
            System.out.println("[LoadingController] Progress updated: " +
                Math.round(p.doubleValue() * 100) + "%")
        );

        // 4) Éxito → respetar splash mínimo y navegar a main menu
        loadTask.setOnSucceeded(evt -> {
            System.out.println("[LoadingController] Assets loaded successfully");
            long elapsedMs = (System.nanoTime() - startTimeNs) / 1_000_000;
            long remaining = Math.max(100, MIN_SPLASH_MS - elapsedMs); // al menos 100ms

            // Desbind para poder poner un texto final fijo si quieres
            statusLabel.textProperty().unbind();
            statusLabel.setText("Loading complete!");

            PauseTransition pt = new PauseTransition(Duration.millis(remaining));
            pt.setOnFinished(e -> {
                try {
                    System.out.println("[LoadingController] Transitioning to main menu");
                    // ¡OJO! No llamamos a shutdown aquí: el Task ya lo hace internamente.
                    SceneRouter.goWithFade("ui/main_menu.fxml", 1280, 720);
                } catch (Exception ex) {
                    System.err.println("[LoadingController] Failed to load main menu: " + ex.getMessage());
                    ErrorController.setLastError("Failed to load main menu: " + ex.getMessage());
                    SceneRouter.goWithFade("ui/error.fxml", 800, 400);
                }
            });
            pt.play();
        });

        // 5) Error → mandar a pantalla de error
        loadTask.setOnFailed(evt -> {
            Throwable ex = loadTask.getException();
            String msg = ex == null ? "Unknown error loading assets" : ex.getMessage();
            System.err.println("[LoadingController] Asset loading failed: " + msg);
            ErrorController.setLastError(msg);
            SceneRouter.go("ui/error.fxml", 800, 400);
            // Tampoco cerramos executor aquí: el Task ya lo hace en failed()
        });

        // 6) Lanzar en el executor del AssetsManager
        System.out.println("[LoadingController] Submitting load task to executor");
        assetsManager.submit(loadTask);
    }
}
