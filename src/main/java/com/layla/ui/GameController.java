// GameController.java (añadir cuando crees la clase del juego)
package com.layla.ui;

import javafx.fxml.FXML;

public class GameController {

    // TODO: inyecta aquí tu root cuando tengas el layout (por ejemplo AnchorPane o StackPane)
    // @FXML private StackPane root;

    /**
     * Muestra una confirmación para volver al menú principal sin cerrar la aplicación.
     * Usa el mismo ExitConfirm overlay que el MainMenuController, pero sin detener la música global.
     */
    @FXML
    private void onBackToMenuClicked() {
        // Cuando ya tengas el root, descomenta esto:
        /*
        OverlayRouter.showOverlay(root, "ui/exit_confirm.fxml", controller -> {
            if (controller instanceof ExitConfirmController ec) {
                ec.setOnCancel(() -> OverlayRouter.closeOverlay(root, overlay));
                ec.setOnConfirm(() -> {
                    OverlayRouter.closeOverlay(root, overlay);
                    // Volver al menú principal conservando el tamaño de ventana
                    javafx.application.Platform.runLater(() ->
                        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml")
                    );
                });
            }
        });
        */
    }
}
