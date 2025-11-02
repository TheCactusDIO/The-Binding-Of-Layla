package com.layla.ui;

import javafx.fxml.FXML;

public class ExitConfirmController {
    private Runnable onConfirm = () -> {};
    private Runnable onCancel  = () -> {};

    public void setOnConfirm(Runnable r) { this.onConfirm = (r != null ? r : () -> {}); }
    public void setOnCancel (Runnable r) { this.onCancel  = (r != null ? r : () -> {}); }

    @FXML private void onConfirm() { onConfirm.run(); }
    @FXML private void onCancel()  { onCancel.run();  }
}
