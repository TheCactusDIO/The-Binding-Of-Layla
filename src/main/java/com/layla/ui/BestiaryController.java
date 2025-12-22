package com.layla.ui;

import java.util.List;
import java.util.Map;

import com.layla.AppContext;
import com.layla.core.AssetsManager;
import com.layla.db.DatabaseService;
import com.layla.model.EnemyProfile;
import com.layla.model.EnemyType;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
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
import javafx.util.Duration;

public class BestiaryController implements ViewLifecycle {

    // =========================
    // FXML
    // =========================
    @FXML private FlowPane gridPane;
    @FXML private VBox detailsPanel;

    @FXML private ImageView detailImage;
    @FXML private Label detailName;
    @FXML private Label statSeen, statKills, statDeaths;
    @FXML private Label attrHp, attrDmg, attrSpeed, attrProjDmg;

    // =========================
    // ESTADO
    // =========================
    private Map<String, DatabaseService.EnemyStatEntry> statsMap;
    private StackPane selectedIcon = null;

    // =========================
    // CONSTANTES UI
    // =========================
    private static final double ICON_BOX_SIZE = 64.0;
    private static final double ICON_IMAGE_SIZE = 48.0;
    private static final double FALLBACK_RECT_SIZE = 40.0;

    private static final double DETAIL_WIDTH = 380.0;

    private static final String STYLE_ICON_NORMAL =
            "-fx-background-color: #333; -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8; -fx-cursor: hand;";
    private static final String STYLE_ICON_HOVER =
            "-fx-background-color: #444; -fx-background-radius: 8; -fx-border-color: #ffd54f; -fx-border-radius: 8; -fx-cursor: hand;";
    private static final String STYLE_ICON_SELECTED =
            "-fx-background-color: #444; -fx-background-radius: 8; -fx-border-color: #ffd54f; -fx-border-width: 2; -fx-border-radius: 8; -fx-cursor: hand;";

    private static final String STYLE_UNKNOWN_LABEL =
            "-fx-text-fill: #555; -fx-font-size: 24px; -fx-font-weight: bold;";

    private static final String TXT_NO_DESCUBIERTO = "Sin descubrir";

    private static final List<String> BOSS_IDS = List.of(
            "BOSS_FLOOR_1", "BOSS_FLOOR_2", "BOSS_FLOOR_3", "BOSS_FLOOR_4", "BOSS_FLOOR_5"
    );

    @Override
    public void onEnter() {
        loadData();
    }

    private void loadData() {
        int profileId = AppContext.getProfileId();
        statsMap = AppContext.db().getAllEnemyStats(profileId);

        gridPane.getChildren().clear();
        selectedIcon = null;
        hideDetailsPanelImmediately();

        // Enemigos normales
        for (EnemyType type : EnemyType.values()) {
            createEnemyIcon(type.name(), false);
        }

        // Jefes
        for (String bossId : BOSS_IDS) {
            createEnemyIcon(bossId, true);
        }
    }

    private void createEnemyIcon(String id, boolean isBoss) {
        DatabaseService.EnemyStatEntry entry = (statsMap != null) ? statsMap.get(id) : null;

        int seenCount   = (entry != null) ? entry.seen()     : 0;
        int killedCount = (entry != null) ? entry.killed()   : 0;
        int deathsBy    = (entry != null) ? entry.killedBy() : 0;

        boolean seen = seenCount > 0;

        StackPane iconRoot = new StackPane();
        iconRoot.setPrefSize(ICON_BOX_SIZE, ICON_BOX_SIZE);
        iconRoot.setStyle(STYLE_ICON_NORMAL);

        if (seen) {
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

            // Hover (sin pisar selección)
            iconRoot.setOnMouseEntered(e -> {
                if (iconRoot != selectedIcon) iconRoot.setStyle(STYLE_ICON_HOVER);
            });
            iconRoot.setOnMouseExited(e -> {
                if (iconRoot != selectedIcon) iconRoot.setStyle(STYLE_ICON_NORMAL);
            });

            // Click -> detalles
            iconRoot.setOnMouseClicked(e -> {
                selectIcon(iconRoot);
                showDetails(id, isBoss, seenCount, killedCount, deathsBy);
                openDetailsPanelAnimated();
            });

        } else {
            Label q = new Label("?");
            q.setStyle(STYLE_UNKNOWN_LABEL);
            iconRoot.getChildren().add(q);
            Tooltip.install(iconRoot, new Tooltip(TXT_NO_DESCUBIERTO));
        }

        gridPane.getChildren().add(iconRoot);
    }

    private void selectIcon(StackPane icon) {
        if (selectedIcon != null) {
            selectedIcon.setStyle(STYLE_ICON_NORMAL);
        }
        selectedIcon = icon;
        selectedIcon.setStyle(STYLE_ICON_SELECTED);
    }

    private void showDetails(String id, boolean isBoss, int seen, int kills, int deathsBy) {
        detailName.setText(id.replace("_", " "));

        // Imagen detalle
        Image img = loadEnemyImage(id);
        if (img != null && !img.isError()) {
            detailImage.setImage(img);
        } else {
            detailImage.setImage(null);
        }

        // Stats DB
        statSeen.setText(String.valueOf(seen));
        statKills.setText(String.valueOf(kills));
        statDeaths.setText(String.valueOf(deathsBy));

        // Atributos balance
        if (isBoss) {
            // No inventamos stats si no tenemos perfil por ID
            setAttrs("-", "-", "-", "-");
            return;
        }

        try {
            EnemyProfile profile = AppContext.balance().profile(EnemyType.valueOf(id));
            if (profile != null) {
                attrHp.setText(String.format("%.0f", profile.baseHp));
                attrDmg.setText(String.format("%.1f", profile.contactDmg));
                attrSpeed.setText(String.format("%.0f", profile.speed));
                attrProjDmg.setText(String.format("%.1f", profile.projDamage));
            } else {
                setAttrs("-", "-", "-", "-");
            }
        } catch (Exception ignore) {
            setAttrs("-", "-", "-", "-");
        }
    }

    private void setAttrs(String hp, String dmg, String proj, String speed) {
        attrHp.setText(hp);
        attrDmg.setText(dmg);
        attrProjDmg.setText(proj);
        attrSpeed.setText(speed);
    }

    private Image loadEnemyImage(String id) {
        // Mantengo tu naming: assets/images/enemies/<ID>.png
        String path = "assets/images/enemies/" + id + ".png";
        try {
            return AssetsManager.loadImage(path);
        } catch (Exception e) {
            return null;
        }
    }

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

    private void openDetailsPanelAnimated() {
        if (detailsPanel.isVisible() && detailsPanel.getPrefWidth() >= DETAIL_WIDTH) return;

        detailsPanel.setVisible(true);
        detailsPanel.setManaged(true);

        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(220),
                        new KeyValue(detailsPanel.prefWidthProperty(), DETAIL_WIDTH),
                        new KeyValue(detailsPanel.opacityProperty(), 1.0)
                )
        );
        t.play();
    }

    private void closeDetailsPanelAnimated() {
        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(200),
                        new KeyValue(detailsPanel.prefWidthProperty(), 0),
                        new KeyValue(detailsPanel.opacityProperty(), 0.0)
                )
        );

        t.setOnFinished(e -> {
            detailsPanel.setVisible(false);
            detailsPanel.setManaged(false);

            if (selectedIcon != null) {
                selectedIcon.setStyle(STYLE_ICON_NORMAL);
                selectedIcon = null;
            }
        });

        t.play();
    }

    private void hideDetailsPanelImmediately() {
        detailsPanel.setVisible(false);
        detailsPanel.setManaged(false);
        detailsPanel.setPrefWidth(0);
        detailsPanel.setOpacity(0.0);

        detailImage.setImage(null);
        detailName.setText("SELECT AN ENEMY");
        statSeen.setText("0");
        statKills.setText("0");
        statDeaths.setText("0");
        setAttrs("-", "-", "-", "-");
    }

    @FXML
    private void onCloseDetails() {
        closeDetailsPanelAnimated();
    }

    @FXML
    private void onBack() {
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }
}
