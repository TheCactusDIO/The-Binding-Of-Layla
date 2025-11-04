package com.layla.ui;

import javafx.fxml.FXML;

public class PauseOverlayController {
    private Runnable onResume = () -> {};
    private Runnable onBackToMenu = () -> {};
    private Runnable onSettings = () -> {};

    public void setOnResume(Runnable r) { this.onResume = (r != null ? r : () -> {}); }
    public void setOnBackToMenu(Runnable r) { this.onBackToMenu = (r != null ? r : () -> {}); }
    public void setOnSettings(Runnable r) { this.onSettings = (r != null ? r : () -> {}); }

    @FXML private void onResume()     { onResume.run(); }
    @FXML private void onBackToMenu() { onBackToMenu.run(); }
    @FXML private void onSettings()   { onSettings.run(); }
}
