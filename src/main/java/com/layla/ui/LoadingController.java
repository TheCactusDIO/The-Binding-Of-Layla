package com.layla.ui;

import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

import com.layla.core.AssetsManager;

import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.util.Duration;

/**
 * Controlador de la pantalla de carga.
 *
 * Responsabilidades:
 * - Lanzar la carga de assets en segundo plano.
 * - Reflejar el progreso y el estado en la UI (progress bar + texto).
 * - Garantizar un tiempo mínimo de splash (evita “parpadeos” si carga muy rápido).
 * - Transicionar a la siguiente pantalla cuando termina (selección de perfil).
 */
public final class LoadingController implements Initializable {

    // ---------- Textos visibles (hardcodeados en español) ----------
    private static final String TXT_TITULO = "Cargando recursos...";
    private static final String TXT_PREPARANDO = "Preparando...";
    private static final String TXT_COMPLETADO = "¡Carga completada!";
    private static final String TXT_ERROR_DESCONOCIDO = "Error desconocido cargando recursos";

    // ---------- Configuración ----------
    private static final long MIN_SPLASH_MS = 800;

    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;

    private final AssetsManager assetsManager = new AssetsManager();
    private long startTimeNs;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("[LoadingController] Inicializando pantalla de carga");
        startTimeNs = System.nanoTime();

        // Seguridad: si el FXML no inyecta bien, mejor fallar con log claro.
        if (progressBar == null || statusLabel == null) {
            String msg = "FXML mal configurado: progressBar/statusLabel no inyectados.";
            System.err.println("[LoadingController] " + msg);
            ErrorController.setLastError(msg);
            SceneRouter.go("ui/error.fxml", 800, 400);
            return;
        }

        // Crea la tarea que carga assets (debe reportar message/progress).
        Task<Void> loadTask = Objects.requireNonNull(
                assetsManager.createLoadTask(),
                "AssetsManager.createLoadTask() devolvió null"
        );

        // Estado inicial UI
        statusLabel.setText(TXT_PREPARANDO);
        statusLabel.textProperty().bind(loadTask.messageProperty());

        progressBar.setProgress(0);
        progressBar.progressProperty().bind(loadTask.progressProperty());

        // Cuando termina OK: respetar splash mínimo y saltar a selección de perfil.
        loadTask.setOnSucceeded(evt -> onLoadSucceeded());

        // Cuando falla: mostrar error y enviar a pantalla de error.
        loadTask.setOnFailed(evt -> onLoadFailed(loadTask.getException()));

        // Ejecuta la tarea (AssetsManager gestiona su executor).
        assetsManager.submit(loadTask);
    }

    /**
     * Handler cuando la carga termina correctamente.
     * - Desvincula bindings.
     * - Espera el tiempo mínimo de splash.
     * - Transiciona a profile_select.
     */
    private void onLoadSucceeded() {
        System.out.println("[LoadingController] Recursos cargados correctamente");

        long elapsedMs = (System.nanoTime() - startTimeNs) / 1_000_000;
        long remainingMs = Math.max(100, MIN_SPLASH_MS - elapsedMs);

        // UI: desbloquear bindings y mostrar mensaje final
        statusLabel.textProperty().unbind();
        statusLabel.setText(TXT_COMPLETADO);

        progressBar.progressProperty().unbind();
        progressBar.setProgress(1.0);

        PauseTransition pt = new PauseTransition(Duration.millis(remainingMs));
        pt.setOnFinished(e -> goToNextScene());
        pt.play();
    }

    /**
     * Handler cuando la carga falla.
     * Guarda el error y lleva al usuario a la pantalla de error.
     */
    private void onLoadFailed(Throwable ex) {
        String msg = (ex == null || ex.getMessage() == null || ex.getMessage().isBlank())
                ? TXT_ERROR_DESCONOCIDO
                : ex.getMessage();

        System.err.println("[LoadingController] Falló la carga de recursos: " + msg);
        if (ex != null) ex.printStackTrace();

        ErrorController.setLastError(msg);
        SceneRouter.go("ui/error.fxml", 800, 400);
    }

    /**
     * Transiciona a la siguiente escena del flujo inicial.
     * Si algo falla, cae a error.fxml con el motivo.
     */
    private void goToNextScene() {
        try {
            System.out.println("[LoadingController] Transicionando a selección de perfil");
            SceneRouter.goWithFade("ui/profile_select.fxml", 1280, 720);
        } catch (Exception ex) {
            String msg = "No se pudo cargar la siguiente escena: " + ex.getMessage();
            System.err.println("[LoadingController] " + msg);
            ErrorController.setLastError(msg);
            SceneRouter.goWithFade("ui/error.fxml", 800, 400);
        }
    }
}
