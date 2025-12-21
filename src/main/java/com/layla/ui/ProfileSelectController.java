package com.layla.ui;

import java.util.Optional;

import com.layla.AppContext;
import com.layla.core.AssetsManager;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;

public class ProfileSelectController implements ViewLifecycle {

    @FXML private Label lblName1, lblStats1;
    @FXML private Label lblName2, lblStats2;
    @FXML private Label lblName3, lblStats3;

    @FXML private HBox controls1, controls2, controls3;

    @Override
    public void onEnter() {
        // Música del menú: empieza aquí y no se reinicia al pasar a MainMenu
        AssetsManager.ensureMusic("menu.mp3", true);

        refreshSlots();
    }

    private void refreshSlots() {
        updateSlot(1, lblName1, lblStats1, controls1);
        updateSlot(2, lblName2, lblStats2, controls2);
        updateSlot(3, lblName3, lblStats3, controls3);
    }

    private void updateSlot(int id, Label nameLbl, Label statsLbl, HBox controls) {
        var summary = AppContext.db().getProfileSummary(id);

        // Si el nombre es nulo, consideramos el perfil vacío
        if (summary.name() == null || summary.name().isEmpty()) {
            nameLbl.setText("HUECO VACÍO");
            nameLbl.setStyle("-fx-text-fill: #666; -fx-font-weight: bold; -fx-font-size: 22px;");
            statsLbl.setText("Pulsa para crear\nuna nueva partida");
            controls.setVisible(false); // No mostrar borrar/renombrar si está vacío
        } else {
            nameLbl.setText(summary.name().toUpperCase());
            nameLbl.setStyle("-fx-text-fill: #ffd54f; -fx-font-weight: bold; -fx-font-size: 22px;");
            statsLbl.setText(String.format(
                "Partidas: %d\nVictorias: %d\nDerrotas: %d\nRacha: %d",
                summary.runs(), summary.wins(), summary.deaths(), summary.streak()
            ));
            controls.setVisible(true);
        }
    }

    // --- ACCIONES PRINCIPALES (CLICK EN SLOT) ---

    @FXML private void onSlot1() { handleSlotClick(1); }
    @FXML private void onSlot2() { handleSlotClick(2); }
    @FXML private void onSlot3() { handleSlotClick(3); }

    private void handleSlotClick(int id) {
        var summary = AppContext.db().getProfileSummary(id);
        if (summary.name() == null) {
            // Crear nuevo
            promptForName(id, "Crear nueva partida", "Introduce tu nombre:", true);
        } else {
            // Cargar existente
            loadProfile(id);
        }
    }

    private void loadProfile(int id) {
        AppContext.setProfileId(id);
        System.out.println("[Profile] Perfil seleccionado: " + id);
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }

    // --- RENOMBRAR ---

    @FXML private void onRenameSlot1() { promptForName(1, "Renombrar partida", "Introduce el nuevo nombre:", false); }
    @FXML private void onRenameSlot2() { promptForName(2, "Renombrar partida", "Introduce el nuevo nombre:", false); }
    @FXML private void onRenameSlot3() { promptForName(3, "Renombrar partida", "Introduce el nuevo nombre:", false); }

    private void promptForName(int id, String title, String header, boolean autoEnter) {
        TextInputDialog dialog = new TextInputDialog("Layla");
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText("Nombre:");

        // Estilo oscuro básico para el diálogo
        UIStyles.applyDialogStyle(dialog.getDialogPane());

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                AppContext.db().setProfileName(id, name.trim());
                refreshSlots();
                if (autoEnter) {
                    loadProfile(id);
                }
            }
        });
    }

    // --- BORRAR ---

    @FXML private void onDeleteSlot1() { confirmDelete(1); }
    @FXML private void onDeleteSlot2() { confirmDelete(2); }
    @FXML private void onDeleteSlot3() { confirmDelete(3); }

    private void confirmDelete(int id) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Borrar partida");
        alert.setHeaderText("¿Seguro que quieres borrar la ranura " + id + "?");
        alert.setContentText("Se perderán para siempre los progresos, desbloqueos y estadísticas.");

        UIStyles.applyDialogStyle(alert.getDialogPane());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            AppContext.db().resetProfile(id);
            refreshSlots();
        }
    }
}
