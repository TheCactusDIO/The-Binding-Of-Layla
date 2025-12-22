package com.layla.ui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.layla.AppContext;
import com.layla.db.DatabaseService.LeaderboardEntry;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

public class RankingController implements ViewLifecycle {

    // =========================
    // FXML
    // =========================

    @FXML private VBox rootBox;

    @FXML private TableView<LeaderboardEntry> rankingTable;
    @FXML private TableColumn<LeaderboardEntry, Integer> rankCol;
    @FXML private TableColumn<LeaderboardEntry, String> nameCol;
    @FXML private TableColumn<LeaderboardEntry, Integer> scoreCol;
    @FXML private TableColumn<LeaderboardEntry, String> floorCol;
    @FXML private TableColumn<LeaderboardEntry, String> dateCol;

    // =========================
    // CONFIG
    // =========================

    private static final int LIMIT = 20;

    /** Formato objetivo que queremos mostrar (sin hora, limpio). */
    private static final DateTimeFormatter OUT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Posibles formatos que podrían venir de DB. Ajusta si tu DB devuelve otro patrón. */
    private static final DateTimeFormatter IN_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // =========================
    // LIFECYCLE
    // =========================

    /**
     * Inicializa la tabla:
     * - Configura cellValueFactory de columnas.
     * - Configura placeholder si no hay runs.
     */
    @FXML
    private void initialize() {
        configureColumns();
        configurePlaceholder();
    }

    /**
     * Hook de entrada de la vista:
     * - Recarga datos cada vez que el usuario entra a la pestaña.
     */
    @Override
    public void onEnter() {
        refreshDataAsync();
    }

    // =========================
    // TABLE SETUP
    // =========================

    /**
     * Configura el binding de datos de cada columna de la tabla.
     * Usamos wrappers "ReadOnly*" que son más limpios para TableView.
     */
    private void configureColumns() {
        if (rankCol != null) {
            rankCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(safeRank(data.getValue())));
        }

        if (nameCol != null) {
            nameCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(safeString(data.getValue() != null ? data.getValue().playerName() : null)));
        }

        if (scoreCol != null) {
            scoreCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(safeScore(data.getValue())));
        }

        if (floorCol != null) {
            floorCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(formatFloor(data.getValue())));
        }

        if (dateCol != null) {
            dateCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(formatDate(data.getValue())));
        }
    }

    /**
     * Placeholder visible cuando la tabla no tiene filas.
     * Evita un TableView "vacío" feo.
     */
    private void configurePlaceholder() {
        if (rankingTable == null) return;

        Label placeholder = new Label("No runs recorded yet.");
        placeholder.getStyleClass().add("table-placeholder"); // preparado para CSS luego
        placeholder.setStyle("-fx-text-fill: #666; -fx-font-size: 16px;");
        rankingTable.setPlaceholder(placeholder);
    }

    // =========================
    // DATA LOADING
    // =========================

    /**
     * Limpia la tabla y carga el leaderboard en segundo plano.
     * Luego aplica resultados en el hilo JavaFX.
     */
    private void refreshDataAsync() {
        if (rankingTable == null) return;

        rankingTable.getItems().clear();

        CompletableFuture
            .supplyAsync(this::loadLeaderboard)
            .thenAccept(entries -> Platform.runLater(() -> applyLeaderboard(entries)))
            .exceptionally(ex -> {
                Platform.runLater(() -> showLoadError(ex));
                return null;
            });
    }

    /**
     * Lee el leaderboard desde la DB.
     * Este método se ejecuta fuera del hilo JavaFX.
     */
    private List<LeaderboardEntry> loadLeaderboard() {
        return AppContext.db().getLeaderboard(LIMIT);
    }

    /**
     * Inserta las filas en la tabla.
     * Este método debe ejecutarse en el hilo JavaFX.
     */
    private void applyLeaderboard(List<LeaderboardEntry> entries) {
        if (rankingTable == null) return;
        if (entries == null) return;

        rankingTable.getItems().setAll(entries);
    }

    /**
     * Maneja un error de carga:
     * - Mantiene la UI estable.
     * - Muestra placeholder informativo.
     */
    private void showLoadError(Throwable ex) {
        if (rankingTable == null) return;

        Label placeholder = new Label("Error loading ranking.");
        placeholder.setStyle("-fx-text-fill: #aa0000; -fx-font-size: 16px;");
        rankingTable.setPlaceholder(placeholder);

        System.err.println("[Ranking] Error loading leaderboard: " + (ex != null ? ex.getMessage() : "unknown"));
        if (ex != null) ex.printStackTrace();
    }

    // =========================
    // FORMATTERS / HELPERS
    // =========================

    /**
     * Formatea la columna FLOOR:
     * "F3" o "F3 (WIN)".
     */
    private String formatFloor(LeaderboardEntry e) {
        if (e == null) return "-";
        String text = "F" + e.floor();
        if (e.isWin()) text += " (WIN)";
        return text;
    }

    /**
     * Formatea la fecha:
     * - Si viene "yyyy-MM-dd HH:mm:ss" -> "yyyy-MM-dd"
     * - Si viene ya limpia -> se deja tal cual
     * - Si viene null/raruna -> "-"
     */
    private String formatDate(LeaderboardEntry e) {
        if (e == null) return "-";

        String raw = safeString(e.date());
        if (raw.isBlank()) return "-";

        // Caso típico: "YYYY-MM-DD HH:MM:SS"
        try {
            LocalDateTime dt = LocalDateTime.parse(raw, IN_DATE_TIME);
            return OUT_DATE.format(dt);
        } catch (DateTimeParseException ignore) {
            // Si no parsea, intentamos algo simple:
            // si contiene espacio, nos quedamos con la parte de la fecha
            int idx = raw.indexOf(' ');
            if (idx > 0) return raw.substring(0, idx);
            return raw;
        }
    }

    /** Evita NPE en Strings. */
    private static String safeString(String s) {
        return (s == null) ? "" : s;
    }

    /** Evita NPE en rank. */
    private static int safeRank(LeaderboardEntry e) {
        return (e == null) ? 0 : e.rank();
    }

    /** Evita NPE en score. */
    private static int safeScore(LeaderboardEntry e) {
        return (e == null) ? 0 : e.score();
    }
}
