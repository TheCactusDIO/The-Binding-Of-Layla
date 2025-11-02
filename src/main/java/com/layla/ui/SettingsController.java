package com.layla.ui;

import static javafx.beans.binding.Bindings.max;
import static javafx.beans.binding.Bindings.min;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class SettingsController {
    @FXML private StackPane root;
    @FXML private VBox contentBox;

    @FXML
    private void initialize() {
        contentBox.prefWidthProperty().bind(
            min(520.0, max(300.0, root.widthProperty().multiply(0.5)))
        );
    }

    @FXML
    private void onBack() {
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }
}
