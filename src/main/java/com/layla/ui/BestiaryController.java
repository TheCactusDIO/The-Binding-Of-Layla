package com.layla.ui;

import java.util.List;
import java.util.Map;

import com.layla.AppContext;
import com.layla.core.AssetsManager;
import com.layla.db.DatabaseService;
import com.layla.model.EnemyProfile;
import com.layla.model.EnemyType;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class BestiaryController implements ViewLifecycle {

    // =========================
    // FXML
    // =========================

    @FXML private FlowPane gridPane;
    @FXML private VBox detailsPanel;

    // Panel de detalles
    @FXML private ImageView detailImage;
    @FXML private Label detailName;
    @FXML private Label statSeen, statKills, statDeaths;
    @FXML private Label attrHp, attrDmg, attrSpeed, attrProjDmg;

    // =========================
    // ESTADO
    // =========================

    private Map<String, DatabaseService.EnemyStatEntry> statsMap;

    // =========================
    // CONSTANTES UI
    // =========================

    private static final double ICON_BOX_SIZE = 64.0;
    private static final double ICON_IMAGE_SIZE = 48.0;
    private static final double FALLBACK_RECT_SIZE = 40.0;

    private static final String STYLE_ICON_NORMAL =
            "-fx-background-color: #333; -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8; -fx-cursor: hand;";
    private static final String STYLE_ICON_HOVER =
            "-fx-background-color: #444; -fx-background-radius: 8; -fx-border-color: #ffd54f; -fx-border-radius: 8; -fx-cursor: hand;";

    private static final String STYLE_UNKNOWN_LABEL =
            "-fx-text-fill: #555; -fx-font-size: 24px; -fx-font-weight: bold;";

    private static final String TXT_NO_DESCUBIERTO = "Sin descubrir";

    // IDs de jefes (se mantienen tal cual para que coincidan con imágenes/estadísticas)
    private static final List<String> BOSS_IDS = List.of(
            "BOSS_FLOOR_1", "BOSS_FLOOR_2", "BOSS_FLOOR_3", "BOSS_FLOOR_4", "BOSS_FLOOR_5"
    );

    @Override
    public void onEnter() {
        // Al entrar, recargamos datos por si cambiaron estadísticas con otra partida/perfil.
        loadData();
    }

    /**
     * Carga las estadísticas del perfil actual y reconstruye el grid de enemigos/jefes.
     */
    private void loadData() {
        int profileId = AppContext.getProfileId();
        statsMap = AppContext.db().getAllEnemyStats(profileId);

        gridPane.getChildren().clear();
        hideDetailsPanel();

        // Enemigos normales
        for (EnemyType type : EnemyType.values()) {
            createEnemyIcon(type.name(), false);
        }

        // Jefes
        for (String bossId : BOSS_IDS) {
            createEnemyIcon(bossId, true);
        }
    }

    /**
     * Crea un icono en el grid. Si el enemigo no ha sido visto, se muestra un "?" con tooltip.
     *
     * @param id     identificador (EnemyType.name() o BOSS_FLOOR_X)
     * @param isBoss true si es un jefe
     */
    private void createEnemyIcon(String id, boolean isBoss) {
        DatabaseService.EnemyStatEntry entry = statsMap != null ? statsMap.get(id) : null;
        boolean seen = entry != null && entry.seen() > 0;

        StackPane iconRoot = new StackPane();
        iconRoot.setPrefSize(ICON_BOX_SIZE, ICON_BOX_SIZE);
        iconRoot.setStyle(STYLE_ICON_NORMAL);

        if (seen) {
            // Si está descubierto, intentamos mostrar imagen. Si no existe, fallback a un rectángulo de color.
            Image img = loadEnemyImage(id);

            if (img != null && !img.isError()) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(ICON_IMAGE_SIZE);
                iv.setFitHeight(ICON_IMAGE_SIZE);
                iv.setPreserveRatio(true);
                iconRoot.getChildren().add(iv);
            } else {
                Color c = isBoss ? Color.DARKRED : getColorForType(id);
                iconRoot.getChildren().add(new Rectangle(FALLBACK_RECT_SIZE, FALLBACK_RECT_SIZE, c));
            }

            // Hover
            iconRoot.setOnMouseEntered(e -> iconRoot.setStyle(STYLE_ICON_HOVER));
            iconRoot.setOnMouseExited(e -> iconRoot.setStyle(STYLE_ICON_NORMAL));

            // Click -> detalles (entry debería ser != null aquí, pero nos protegemos por si acaso)
            DatabaseService.EnemyStatEntry safeEntry =
                    (entry != null) ? entry : new DatabaseService.EnemyStatEntry(0, 0, 0);

            iconRoot.setOnMouseClicked(e -> showDetails(id, safeEntry, isBoss));

        } else {
            // No descubierto -> "?"
            Label q = new Label("?");
            q.setStyle(STYLE_UNKNOWN_LABEL);
            iconRoot.getChildren().add(q);
            Tooltip.install(iconRoot, new Tooltip(TXT_NO_DESCUBIERTO));
        }

        gridPane.getChildren().add(iconRoot);
    }

    /**
     * Muestra el panel de detalles para un enemigo/jefe.
     * Para jefes, algunas stats se ocultan o se dejan fijas para evitar depender de un EnemyType inexistente.
     */
    private void showDetails(String id, DatabaseService.EnemyStatEntry entry, boolean isBoss) {
        detailsPanel.setVisible(true);

        // Nombre simple (no inventamos traducciones para no “añadir contenido”)
        detailName.setText(id.replace("_", " "));

        // Imagen del detalle (si existe)
        Image img = loadEnemyImage(id);
        if (img != null && !img.isError()) {
            detailImage.setImage(img);
            detailImage.setVisible(true);
        } else {
            detailImage.setImage(null);
        }

        // Stats de la DB
        statSeen.setText(String.valueOf(entry.seen()));
        statKills.setText(String.valueOf(entry.killed()));
        statDeaths.setText(String.valueOf(entry.killedBy()));

        // Atributos (balance)
        if (isBoss) {
            // No tenemos EnemyType para jefes (están por ID), así que no accedemos a profile(...)
            attrHp.setText("???");
            attrDmg.setText("1.0");
            attrSpeed.setText("45");
            attrProjDmg.setText("1.0");
            return;
        }

        // Enemigos normales: leer EnemyProfile desde balance
        try {
            EnemyProfile profile = AppContext.balance().profile(EnemyType.valueOf(id));
            if (profile != null) {
                attrHp.setText(String.format("%.0f", profile.baseHp));
                attrDmg.setText(String.format("%.1f", profile.contactDmg));
                attrSpeed.setText(String.format("%.0f", profile.speed));
                attrProjDmg.setText(String.format("%.1f", profile.projDamage));
            }
        } catch (Exception ignore) {
            // Si algo falla, dejamos lo que hubiera (o vacío). Preferible a crashear la UI.
        }
    }

    /**
     * Carga la imagen de un enemigo/jefe desde resources.
     * Si no existe, devuelve null para permitir fallback.
     */
    private Image loadEnemyImage(String id) {
        String path = "assets/images/enemies/" + id + ".png";
        try {
            return AssetsManager.loadImage(path);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Color de fallback por tipo de enemigo (solo para EnemyType válidos).
     */
    private Color getColorForType(String id) {
        try {
            return switch (EnemyType.valueOf(id)) {
                case SHOOTER -> Color.ORANGE;
                case MELEE -> Color.CRIMSON;
                case TURRET -> Color.DODGERBLUE;
                case TANK -> Color.DARKOLIVEGREEN;
                case KAMIKAZE -> Color.MAGENTA;
                default -> Color.GRAY;
            };
        } catch (Exception e) {
            return Color.GRAY;
        }
    }

    /**
     * Oculta el panel de detalles (sin animaciones).
     */
    private void hideDetailsPanel() {
        detailsPanel.setVisible(false);
    }

    @FXML
    private void onBack() {
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }
}
