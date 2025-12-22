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

/**
 * Controlador de la pantalla de selección de partida (ranuras 1–3).
 *
 * Funciones:
 * - Mostrar resumen de cada ranura.
 * - Crear partida nueva si la ranura está vacía.
 * - Cargar partida existente si la ranura tiene nombre.
 * - Renombrar / Borrar ranura.
 */
public final class ProfileSelectController implements ViewLifecycle {

    // ---------- Textos visibles (hardcodeado en español) ----------
    private static final String MUSIC_MENU = "menu.mp3";

    private static final String TXT_RANURA_VACIA_TITULO = "RANURA VACÍA";
    private static final String TXT_RANURA_VACIA_DESC = "Pulsa para crear\nuna nueva partida";

    private static final String DLG_CREAR_TITULO = "Crear nueva partida";
    private static final String DLG_CREAR_HEADER = "Introduce tu nombre:";

    private static final String DLG_RENOMBRAR_TITULO = "Renombrar partida";
    private static final String DLG_RENOMBRAR_HEADER = "Introduce el nuevo nombre:";

    private static final String DLG_BORRAR_TITULO = "Borrar partida";
    private static final String DLG_BORRAR_HEADER_FMT = "¿Seguro que quieres borrar la ranura %d?";
    private static final String DLG_BORRAR_CONTENT = "Se perderán para siempre los progresos, desbloqueos y estadísticas.";

    private static final String LABEL_NOMBRE = "Nombre:";

    // ---------- FXML ----------
    @FXML private Label lblName1, lblStats1;
    @FXML private Label lblName2, lblStats2;
    @FXML private Label lblName3, lblStats3;

    @FXML private HBox controls1, controls2, controls3;

    /**
     * Se ejecuta al entrar a esta vista (SceneRouter llama automáticamente si implementa ViewLifecycle).
     * Aquí aseguramos música de menú y refrescamos las ranuras.
     */
    @Override
    public void onEnter() {
        // Música del menú: se asegura aquí y no se reinicia innecesariamente.
        AssetsManager.ensureMusic(MUSIC_MENU, true);

        refreshSlots();
    }

    /**
     * Refresca las 3 ranuras leyendo el estado desde la base de datos.
     */
    private void refreshSlots() {
        // Seguridad: si por algún motivo el FXML no inyecta bien, evitamos NPE.
        if (lblName1 == null || lblStats1 == null || controls1 == null) return;
        if (lblName2 == null || lblStats2 == null || controls2 == null) return;
        if (lblName3 == null || lblStats3 == null || controls3 == null) return;

        updateSlot(1, lblName1, lblStats1, controls1);
        updateSlot(2, lblName2, lblStats2, controls2);
        updateSlot(3, lblName3, lblStats3, controls3);
    }

    /**
     * Actualiza una ranura concreta: nombre, stats y visibilidad de controles.
     */
    private void updateSlot(int id, Label nameLbl, Label statsLbl, HBox controls) {
        var summary = AppContext.db().getProfileSummary(id);

        String name = summary.name();
        boolean empty = (name == null || name.isBlank());

        if (empty) {
            // Ranura vacía
            nameLbl.setText(TXT_RANURA_VACIA_TITULO);
            nameLbl.setStyle("-fx-text-fill: #666; -fx-font-weight: bold; -fx-font-size: 22px;");

            statsLbl.setText(TXT_RANURA_VACIA_DESC);

            // Visible/managed: si solo ocultas visible, te deja hueco.
            controls.setVisible(false);
            controls.setManaged(false);
        } else {
            // Ranura ocupada
            nameLbl.setText(name.toUpperCase());
            nameLbl.setStyle("-fx-text-fill: #ffd54f; -fx-font-weight: bold; -fx-font-size: 22px;");

            statsLbl.setText(String.format(
                    "Partidas: %d\nVictorias: %d\nDerrotas: %d\nRacha: %d",
                    summary.runs(), summary.wins(), summary.deaths(), summary.streak()
            ));

            controls.setVisible(true);
            controls.setManaged(true);
        }
    }

    // -------------------------
    // ACCIONES PRINCIPALES
    // -------------------------

    /** Click en ranura 1. */
    @FXML private void onSlot1() { handleSlotClick(1); }

    /** Click en ranura 2. */
    @FXML private void onSlot2() { handleSlotClick(2); }

    /** Click en ranura 3. */
    @FXML private void onSlot3() { handleSlotClick(3); }

    /**
     * Si la ranura está vacía -> pide nombre y crea.
     * Si ya existe -> carga el perfil.
     */
    private void handleSlotClick(int id) {
        var summary = AppContext.db().getProfileSummary(id);
        String name = summary.name();

        if (name == null || name.isBlank()) {
            // Crear nuevo perfil
            promptForName(id, DLG_CREAR_TITULO, DLG_CREAR_HEADER, /*autoEnter*/ true, "Layla");
        } else {
            // Cargar perfil existente
            loadProfile(id);
        }
    }

    /**
     * Selecciona el perfil en AppContext y pasa al menú principal.
     * Recarga el estado de logros para el perfil seleccionado.
     */
    private void loadProfile(int id) {
        AppContext.setProfileId(id);
        AppContext.achievements().reloadForCurrentProfile();
        System.out.println("[Profile] Perfil seleccionado: " + id);
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }

    // -------------------------
    // RENOMBRAR
    // -------------------------

    /** Renombrar ranura 1. */
    @FXML private void onRenameSlot1() { renameSlot(1); }

    /** Renombrar ranura 2. */
    @FXML private void onRenameSlot2() { renameSlot(2); }

    /** Renombrar ranura 3. */
    @FXML private void onRenameSlot3() { renameSlot(3); }

    /**
     * Renombra una ranura (si está vacía, realmente crea el nombre).
     */
    private void renameSlot(int id) {
        var summary = AppContext.db().getProfileSummary(id);
        String currentName = (summary.name() == null || summary.name().isBlank()) ? "Layla" : summary.name().trim();

        promptForName(id, DLG_RENOMBRAR_TITULO, DLG_RENOMBRAR_HEADER, /*autoEnter*/ false, currentName);
    }

    /**
     * Diálogo para pedir un nombre, guardar en DB y refrescar.
     * Si autoEnter=true, también carga el perfil tras guardarlo.
     */
    private void promptForName(int id, String title, String header, boolean autoEnter, String initialValue) {
        TextInputDialog dialog = new TextInputDialog(initialValue);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(LABEL_NOMBRE);

        // Estilo oscuro básico para el diálogo
        UIStyles.applyDialogStyle(dialog.getDialogPane());

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                AppContext.db().setProfileName(id, trimmed);
                refreshSlots();
                if (autoEnter) loadProfile(id);
            }
        });
    }

    // -------------------------
    // BORRAR
    // -------------------------

    /** Borrar ranura 1. */
    @FXML private void onDeleteSlot1() { confirmDelete(1); }

    /** Borrar ranura 2. */
    @FXML private void onDeleteSlot2() { confirmDelete(2); }

    /** Borrar ranura 3. */
    @FXML private void onDeleteSlot3() { confirmDelete(3); }

    /**
     * Pide confirmación y, si acepta, borra completamente la ranura.
     */
    private void confirmDelete(int id) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(DLG_BORRAR_TITULO);
        alert.setHeaderText(String.format(DLG_BORRAR_HEADER_FMT, id));
        alert.setContentText(DLG_BORRAR_CONTENT);

        UIStyles.applyDialogStyle(alert.getDialogPane());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            AppContext.db().resetProfile(id);
            refreshSlots();
        }
    }
}
