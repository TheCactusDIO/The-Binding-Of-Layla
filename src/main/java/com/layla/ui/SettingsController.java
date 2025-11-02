package com.layla.ui;

import javafx.fxml.FXML;

public class SettingsController {
    private Runnable onClose = () -> {};
    public void setOnClose(Runnable r) { this.onClose = (r != null ? r : () -> {}); }

    @FXML
    private void onBack() {
        onClose.run();
    }
}
