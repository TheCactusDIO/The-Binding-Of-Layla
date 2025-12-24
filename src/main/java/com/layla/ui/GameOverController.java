package com.layla.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;

/**
 * Controlador del overlay de fin de partida (derrota / victoria).
 *
 * <p>Este overlay se reutiliza tanto para derrota como para victoria. La vista muestra un título
 * grande y dos botones:</p>
 * <ul>
 *   <li><b>Reintentar</b>: reinicia la partida.</li>
 *   <li><b>Menú</b>: vuelve al menú principal.</li>
 * </ul>
 *
 * <p>Además incluye atajos de teclado:</p>
 * <ul>
 *   <li><b>ENTER</b> o <b>ESPACIO</b>: reintentar.</li>
 *   <li><b>ESC</b>: volver al menú.</li>
 * </ul>
 *
 * <p><b>Nota:</b> textos hardcodeados en español (sin i18n).</p>
 */
public final class GameOverController {

    // -------------------------
    // Textos UI (ES)
    // -------------------------
    private static final String TITLE_DEFEAT = "FIN DE LA PARTIDA";
    private static final String TITLE_VICTORY = "¡VICTORIA!";

    // -------------------------
    // Estilos (Text usa -fx-fill, no -fx-text-fill)
    // -------------------------
    private static final String STYLE_DEFEAT =
            "-fx-fill: white; -fx-font-size: 28px; -fx-font-weight: bold;";

    private static final String STYLE_VICTORY =
            "-fx-fill: #ffd700; -fx-font-size: 32px; -fx-font-weight: bold;";

    // -------------------------
    // FXML
    // -------------------------
    @FXML private StackPane root;
    @FXML private Button retryButton;
    @FXML private Button menuButton;
    @FXML private Text titleText;

    // -------------------------
    // Callbacks (no-op para evitar NPE)
    // -------------------------
    private Runnable onRetry = GameOverController::noop;
    private Runnable onBackToMenu = GameOverController::noop;

    /**
     * Asigna la acción a ejecutar cuando el jugador pulsa "Reintentar".
     *
     * @param action acción a ejecutar; si es {@code null}, se asigna una acción vacía
     */
    public void setOnRetry(Runnable action) {
        this.onRetry = (action != null) ? action : GameOverController::noop;
    }

    /**
     * Asigna la acción a ejecutar cuando el jugador pulsa "Menú".
     *
     * @param action acción a ejecutar; si es {@code null}, se asigna una acción vacía
     */
    public void setOnBackToMenu(Runnable action) {
        this.onBackToMenu = (action != null) ? action : GameOverController::noop;
    }

    /**
     * Configura si el overlay se muestra como victoria o derrota.
     *
     * @param victory {@code true} para victoria; {@code false} para derrota
     */
    public void setVictory(boolean victory) {
        if (titleText == null) return;

        titleText.setText(victory ? TITLE_VICTORY : TITLE_DEFEAT);
        titleText.setStyle(victory ? STYLE_VICTORY : STYLE_DEFEAT);
    }

    /**
     * (Opcional) Permite fijar el título manualmente desde fuera.
     * <p>Si lo usas, solo cambia el texto. El estilo se mantiene tal cual esté actualmente.</p>
     *
     * @param title texto a mostrar; si es {@code null} o vacío, no hace nada
     */
    public void setTitle(String title) {
        if (titleText == null) return;
        if (title == null || title.isBlank()) return;
        titleText.setText(title);
    }

    /**
     * Inicialización del controlador al cargar el FXML.
     *
     * <ul>
     *   <li>Marca "Reintentar" como botón por defecto (ENTER).</li>
     *   <li>Marca "Menú" como botón de cancelación (ESC, cuando procede).</li>
     *   <li>Configura atajos de teclado sobre el root.</li>
     * </ul>
     */
    @FXML
    private void initialize() {
        if (retryButton != null) retryButton.setDefaultButton(true);
        if (menuButton != null) menuButton.setCancelButton(true);

        if (root != null) {
            root.setFocusTraversable(true);

            // A veces el overlay se inserta después: pedir foco en el siguiente tick ayuda.
            Platform.runLater(root::requestFocus);

            root.setOnKeyPressed(e -> {
                KeyCode code = e.getCode();

                if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
                    onRetry.run();
                    e.consume();
                    return;
                }

                if (code == KeyCode.ESCAPE) {
                    onBackToMenu.run();
                    e.consume();
                }
            });
        }

        // Estado por defecto si nadie llama a setVictory(...) desde fuera.
        setVictory(false);
    }

    /**
     * Handler FXML del botón "Reintentar".
     */
    @FXML
    private void onRetry() {
        onRetry.run();
    }

    /**
     * Handler FXML del botón "Menú".
     */
    @FXML
    private void onMenu() {
        onBackToMenu.run();
    }

    private static void noop() { }
}
