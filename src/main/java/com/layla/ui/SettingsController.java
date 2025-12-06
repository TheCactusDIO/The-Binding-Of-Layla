package com.layla.ui;

import java.util.function.IntConsumer;

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

    // 🔹 Monedas
    private int initialCoins;
    private IntConsumer onCoinsChanged;

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

    // 🔹 Recibimos las monedas actuales desde GameController
    public void setInitialCoins(int coins) {
        this.initialCoins = Math.max(0, coins);
    }

    // 🔹 Callback para avisar al GameController cuando cambien las monedas en el panel
    public void setOnCoinsChanged(IntConsumer cb) {
        this.onCoinsChanged = cb;
    }

    @FXML
    private void initialize() {
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

                    // 🔹 Monedas iniciales al panel
                    sp.setInitialCoins(initialCoins);

                    // 🔹 Callback cuando cambien monedas en el panel
                    sp.setOnCoinsChanged(newCoins -> {
                        // Actualizamos el valor local en Settings
                        initialCoins = Math.max(0, newCoins);
                        // Avisamos al GameController si nos dio callback
                        if (onCoinsChanged != null) {
                            onCoinsChanged.accept(initialCoins);
                        }
                    });

                    sp.setOnClose(() -> OverlayRouter.closeOverlay(overlayHost, statsNode[0]));
                    sp.setOnStatsChanged(() -> {
                        // aquí podrías hacer algo si quieres cuando cambien stats
                    });

                    sp.onShow();
                }
            }
        );
    }
}
