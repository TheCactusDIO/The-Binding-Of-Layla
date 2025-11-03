package com.layla.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;

public class PauseOverlayController {

    @FXML private Button resumeBtn;
    @FXML private Button menuBtn;

    private Runnable onResume;
    private Runnable onBackToMenu;

    @FXML
    private void initialize() {
        resumeBtn.setOnAction(e -> {
            if (onResume != null) onResume.run();
        });
        menuBtn.setOnAction(e -> {
            if (onBackToMenu != null) onBackToMenu.run();
        });
    }

    // setters para callbacks
    public void setOnResume(Runnable onResume) { this.onResume = onResume; }
    public void setOnBackToMenu(Runnable onBackToMenu) { this.onBackToMenu = onBackToMenu; }

    // accesores para test / inspección
    public Button getResumeBtn() { return resumeBtn; }
    public Button getMenuBtn()   { return menuBtn; }
}
