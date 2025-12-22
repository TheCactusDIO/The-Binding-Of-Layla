package com.layla.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;

public class GameOverController {

    // -------------------------
    // Constantes de UI (ES)
    // -------------------------
    private static final String TITLE_DEFEAT_ES = "FIN DE LA PARTIDA";
    private static final String TITLE_VICTORY_ES = "¡VICTORIA!";

    // Compatibilidad con textos antiguos (si algún sitio aún pasa inglés)
    private static final String TITLE_DEFEAT_EN = "GAME OVER";
    private static final String TITLE_VICTORY_EN = "VICTORY!";

    // Estilos (Text usa -fx-fill, no -fx-text-fill)
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
    // Callbacks (seguro por defecto)
    // -------------------------
    private Runnable onRetry = () -> {};
    private Runnable onBackToMenu = () -> {};

    /**
     * Asigna la acción a ejecutar cuando el jugador pulsa "Reintentar".
     * Si llega null, se deja una acción vacía para evitar NullPointerException.
     */
    public void setOnRetry(Runnable r) {
        this.onRetry = (r != null) ? r : () -> {};
    }

    /**
     * Asigna la acción a ejecutar cuando el jugador vuelve al menú.
     * Si llega null, se deja una acción vacía para evitar NullPointerException.
     */
    public void setOnBackToMenu(Runnable r) {
        this.onBackToMenu = (r != null) ? r : () -> {};
    }

    /**
     * Cambia el título del overlay.
     * - El juego va hardcodeado en español.
     * - Si por compatibilidad aún te llega "GAME OVER" o "VICTORY!", se convierte aquí.
     */
    public void setTitle(String text) {
        if (titleText == null) return;

        String normalized = normalizeTitleToSpanish(text);
        titleText.setText(normalized);

        // Ajusta estilo según sea victoria o derrota
        boolean victory = TITLE_VICTORY_ES.equalsIgnoreCase(normalized);
        titleText.setStyle(victory ? STYLE_VICTORY : STYLE_DEFEAT);
    }

    /**
     * Inicialización del overlay:
     * - Configura foco para que ENTER/ESC funcionen siempre.
     * - Marca botones por defecto (ENTER) y cancelación (ESC).
     * - Añade soporte extra: SPACE también reintenta.
     */
    @FXML
    private void initialize() {
        // Botones por defecto/cancelación (JavaFX maneja ENTER/ESC en muchos casos)
        if (retryButton != null) retryButton.setDefaultButton(true);
        if (menuButton != null) menuButton.setCancelButton(true);

        // Forzar foco al root para capturar teclas aunque ningún botón tenga foco
        if (root != null) {
            root.setFocusTraversable(true);
            root.requestFocus();

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

        // Si el texto del FXML estaba en inglés por accidente, lo normalizamos
        if (titleText != null) {
            setTitle(titleText.getText());
        }
    }

    /**
     * Handler del botón "Reintentar" (FXML onAction).
     */
    @FXML
    private void onRetry() {
        onRetry.run();
    }

    /**
     * Handler del botón "Menú" (FXML onAction).
     */
    @FXML
    private void onMenu() {
        onBackToMenu.run();
    }

    /**
     * Convierte títulos antiguos/variantes a español.
     * No es i18n ni ResourceBundle: es hardcodeado para dejar el juego en ES
     * sin obligarte a revisar ahora todos los setTitle() del proyecto.
     */
    private static String normalizeTitleToSpanish(String raw) {
        if (raw == null || raw.isBlank()) return TITLE_DEFEAT_ES;

        String t = raw.trim();

        if (TITLE_VICTORY_EN.equalsIgnoreCase(t)) return TITLE_VICTORY_ES;
        if (TITLE_DEFEAT_EN.equalsIgnoreCase(t)) return TITLE_DEFEAT_ES;

        // Si ya viene en español (o quieres pasar uno distinto), lo respetamos
        return t;
    }
}

