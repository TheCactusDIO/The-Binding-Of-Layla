package com.layla.ui;

import com.layla.AppContext;
import com.layla.db.DatabaseService;
import com.layla.db.DatabaseService.LeaderboardEntry;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.util.concurrent.CompletableFuture;

public class RankingController implements ViewLifecycle {

    @FXML private VBox rootBox;
    @FXML private TableView<LeaderboardEntry> rankingTable;
    @FXML private TableColumn<LeaderboardEntry, Integer> rankCol;
    @FXML private TableColumn<LeaderboardEntry, String> nameCol;
    @FXML private TableColumn<LeaderboardEntry, Integer> scoreCol;
    @FXML private TableColumn<LeaderboardEntry, String> floorCol;
    @FXML private TableColumn<LeaderboardEntry, String> dateCol;

    @FXML
    private void initialize() {
        // Configurar columnas de la tabla
        rankCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().rank()));
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().playerName()));
        scoreCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().score()));

        // Formatear piso y victoria
        floorCol.setCellValueFactory(data -> {
            LeaderboardEntry e = data.getValue();
            String res = "F" + e.floor();
            if (e.isWin()) res += " (WIN)";
            return new SimpleStringProperty(res);
        });

        // Formatear fecha (solo la parte de fecha, quitar hora para limpieza si se desea)
        dateCol.setCellValueFactory(data -> {
            String raw = data.getValue().date();
            // raw suele ser "YYYY-MM-DD HH:MM:SS", tomamos lo que nos sirva
            return new SimpleStringProperty(raw);
        });

        // Placeholder si no hay datos
        Label placeholder = new Label("No runs recorded yet.");
        placeholder.setStyle("-fx-text-fill: #666; -fx-font-size: 16px;");
        rankingTable.setPlaceholder(placeholder);
    }

    @Override
    public void onEnter() {
        refreshData();
    }

    private void refreshData() {
        rankingTable.getItems().clear();

        // Cargar datos en hilo secundario
        CompletableFuture.supplyAsync(() -> AppContext.db().getLeaderboard(20))
            .thenAccept(list -> Platform.runLater(() -> {
                rankingTable.getItems().setAll(list);
            }));
    }
}
