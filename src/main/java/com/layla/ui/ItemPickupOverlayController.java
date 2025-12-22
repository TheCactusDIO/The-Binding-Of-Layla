package com.layla.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;

/**
 * Overlay estilo Isaac para mostrar el item recogido:
 * - Icono grande
 * - Nombre
 * - Descripción
 * - Se cierra con click o SPACE/ENTER/ESC.
 *
 * Nota: El oscurecido lo aporta OverlayRouter (backdrop).
 * Este root debe ser transparente.
 */
public final class ItemPickupOverlayController {

    @FXML private StackPane root;
    @FXML private ImageView itemImageView;
    @FXML private Label itemNameLabel;
    @FXML private Label itemDescLabel;

    private Runnable onClose = () -> {};

    public void setOnClose(Runnable r) {
        this.onClose = (r != null ? r : () -> {});
    }

    public void setItem(String name, String description, Image icon) {
        if (itemNameLabel != null) itemNameLabel.setText(name != null ? name : "");
        if (itemDescLabel != null) itemDescLabel.setText(description != null ? description : "");

        if (itemImageView != null) {
            itemImageView.setImage(icon); // si icon es null, limpia
        }
    }

    @FXML
    private void initialize() {
        if (root == null) return;

        root.setFocusTraversable(true);

        // Pedir foco cuando ya haya Scene (más fiable que requestFocus() directo)
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(root::requestFocus);
            }
        });

        root.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            KeyCode code = e.getCode();
            if (code == KeyCode.ENTER || code == KeyCode.SPACE || code == KeyCode.ESCAPE) {
                onClose.run();
                e.consume();
            }
        });

        root.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            onClose.run();
            e.consume();
        });
    }
}
