package com.layla.ui;

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
 */
public class ItemPickupOverlayController {

    @FXML private StackPane root;
    @FXML private ImageView itemImageView;
    @FXML private Label itemNameLabel;
    @FXML private Label itemDescLabel;

    private Runnable onClose = () -> {};

    public void setOnClose(Runnable r) {
        this.onClose = (r != null ? r : () -> {});
    }

    public void setItem(String name, String description, Image icon) {
        itemNameLabel.setText(name != null ? name : "");
        itemDescLabel.setText(description != null ? description : "");
        if (icon != null) {
            itemImageView.setImage(icon);
        }
    }

    @FXML
    private void initialize() {
        if (root != null) {
            root.setFocusTraversable(true);
            root.requestFocus();

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
}
