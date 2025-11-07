package com.layla.ui;

import com.layla.services.StatsService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

public class SettingsController {

    @FXML private Button btnCancel;
    @FXML private Button btnApply;
    @FXML private Button btnOpenStats;

    private Runnable onClose = () -> {};
    private StatsService statsService = com.layla.AppContext.stats();

    // Host donde se montan los overlays (lo setean los que abren este Settings)
    private StackPane overlayHost;

    public void setOnClose(Runnable r) {
        this.onClose = (r != null) ? r : () -> {};
    }

    public void setStatsService(StatsService s) {
        if (s != null) this.statsService = s;
    }

    /** Debe llamarse al abrir este Settings para que onOpenStats sepa dónde montar el panel. */
    public void setOverlayHost(StackPane host) {
        this.overlayHost = host;
    }

    @FXML
    private void initialize() {
        // Si prefieres, deja el estilo en el FXML y elimina estas líneas
        if (btnCancel != null)    btnCancel.getStyleClass().addAll("menu-button","btn-secondary");
        if (btnApply != null)     btnApply.getStyleClass().addAll("menu-button","btn-primary");
        if (btnOpenStats != null) btnOpenStats.getStyleClass().addAll("menu-button","btn-accent");
    }

    @FXML
    private void onCancel() {
        onClose.run();
    }

    @FXML
    private void onApply() {
        // aplica opciones generales de Settings si las hay
        onClose.run();
    }

    @FXML
    private void onOpenStats() {
        if (overlayHost == null) {
            System.err.println("[SettingsController] overlayHost es null; no puedo abrir stats_panel.fxml");
            return;
        }
        final javafx.scene.Node[] statsNode = new javafx.scene.Node[1];
        statsNode[0] = OverlayRouter.showOverlay(
            overlayHost,
            "ui/stats_panel.fxml",
            0.90,
            controller -> {
                if (controller instanceof StatsPanelController sp) {
                    sp.setStatsService(statsService);
                    sp.setOnClose(() -> OverlayRouter.closeOverlay(overlayHost, statsNode[0]));
                    sp.onShow();
                }
            }
        );
    }
}
