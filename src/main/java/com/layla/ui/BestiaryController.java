package com.layla.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.layla.AppContext;
import com.layla.core.AssetsManager;
import com.layla.db.DatabaseService;
import com.layla.model.EnemyProfile;
import com.layla.model.EnemyType;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
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

    @FXML private FlowPane gridPane;
    @FXML private VBox detailsPanel;

    // Details UI
    @FXML private ImageView detailImage;
    @FXML private Label detailName;
    @FXML private Label statSeen, statKills, statDeaths;
    @FXML private Label attrHp, attrDmg, attrSpeed, attrProjDmg; // Añadido attrProjDmg

    private Map<String, DatabaseService.EnemyStatEntry> statsMap;

    @Override
    public void onEnter() {
        loadData();
    }

    private void loadData() {
        int profileId = AppContext.getProfileId();
        statsMap = AppContext.db().getAllEnemyStats(profileId);
        gridPane.getChildren().clear();

        detailsPanel.setVisible(false);

        // Enemigos comunes
        for (EnemyType type : EnemyType.values()) {
            createEnemyIcon(type.name(), false);
        }

        // Jefes
        List<String> bosses = List.of(
            "BOSS_FLOOR_1", "BOSS_FLOOR_2", "BOSS_FLOOR_3", "BOSS_FLOOR_4", "BOSS_FLOOR_5"
        );
        for (String bossId : bosses) {
            createEnemyIcon(bossId, true);
        }
    }

    private void createEnemyIcon(String id, boolean isBoss) {
        DatabaseService.EnemyStatEntry entry = statsMap.get(id);
        boolean seen = entry != null && entry.seen() > 0;

        StackPane iconRoot = new StackPane();
        iconRoot.setPrefSize(64, 64);
        iconRoot.setStyle("-fx-background-color: #333; -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8; -fx-cursor: hand;");

        if (seen) {
            try {
                String path = "assets/images/enemies/" + id + ".png";
                Image img = AssetsManager.loadImage(path);

                if (img != null && !img.isError()) {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(48);
                    iv.setFitHeight(48);
                    iv.setPreserveRatio(true);
                    iconRoot.getChildren().add(iv);
                } else {
                    Color c = isBoss ? Color.DARKRED : getColorForType(id);
                    Rectangle rect = new Rectangle(40, 40, c);
                    iconRoot.getChildren().add(rect);
                }
            } catch (Exception e) {
                Rectangle rect = new Rectangle(40, 40, Color.GRAY);
                iconRoot.getChildren().add(rect);
            }

            iconRoot.setOnMouseEntered(e -> iconRoot.setStyle("-fx-background-color: #444; -fx-background-radius: 8; -fx-border-color: #ffd54f; -fx-border-radius: 8; -fx-cursor: hand;"));
            iconRoot.setOnMouseExited(e -> iconRoot.setStyle("-fx-background-color: #333; -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8; -fx-cursor: hand;"));

            iconRoot.setOnMouseClicked(e -> showDetails(id, entry, isBoss));

        } else {
            Label q = new Label("?");
            q.setStyle("-fx-text-fill: #555; -fx-font-size: 24px; -fx-font-weight: bold;");
            iconRoot.getChildren().add(q);
            Tooltip.install(iconRoot, new Tooltip("Undiscovered"));
        }

        gridPane.getChildren().add(iconRoot);
    }

    private void showDetails(String id, DatabaseService.EnemyStatEntry entry, boolean isBoss) {
        detailsPanel.setVisible(true);
        detailName.setText(id.replace("_", " "));

        try {
            String path = "assets/images/enemies/" + id + ".png";
            Image img = AssetsManager.loadImage(path);
            if (img != null && !img.isError()) {
                detailImage.setImage(img);
                detailImage.setVisible(true);
            } else {
                detailImage.setImage(null);
            }
        } catch (Exception e) {
            detailImage.setImage(null);
        }

        statSeen.setText(String.valueOf(entry.seen()));
        statKills.setText(String.valueOf(entry.killed()));
        statDeaths.setText(String.valueOf(entry.killedBy()));

        if (isBoss) {
            attrHp.setText("???");
            attrDmg.setText("1.0");
            attrSpeed.setText("45");
            attrProjDmg.setText("1.0"); // Daño base de proyectil de Boss
        } else {
            try {
                EnemyProfile profile = AppContext.balance().profile(EnemyType.valueOf(id));
                if (profile != null) {
                    attrHp.setText(String.format("%.0f", profile.baseHp));
                    attrDmg.setText(String.format("%.1f", profile.contactDmg));
                    attrSpeed.setText(String.format("%.0f", profile.speed));
                    attrProjDmg.setText(String.format("%.1f", profile.projDamage));
                }
            } catch (Exception ignore) {}
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
        } catch (Exception e) { return Color.GRAY; }
    }

    @FXML
    private void onBack() {
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }
}
