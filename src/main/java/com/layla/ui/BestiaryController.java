package com.layla.ui;

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

    @FXML private FlowPane gridPane;
    @FXML private VBox detailsPanel;

    // Details UI
    @FXML private ImageView detailImage;
    @FXML private Label detailName;
    @FXML private Label statSeen, statKills, statDeaths;
    @FXML private Label attrHp, attrDmg, attrSpeed;

    private Map<String, DatabaseService.EnemyStatEntry> statsMap;

    @Override
    public void onEnter() {
        loadData();
    }

    private void loadData() {
        int profileId = AppContext.getProfileId();
        statsMap = AppContext.db().getAllEnemyStats(profileId);
        gridPane.getChildren().clear();

        // Ocultar detalles hasta que se seleccione uno
        detailsPanel.setVisible(false);

        // Iterar sobre todos los tipos de enemigos definidos en el juego
        for (EnemyType type : EnemyType.values()) {
            createEnemyIcon(type);
        }
    }

    private void createEnemyIcon(EnemyType type) {
        DatabaseService.EnemyStatEntry entry = statsMap.get(type.name());
        boolean seen = entry != null && entry.seen() > 0;

        StackPane iconRoot = new StackPane();
        iconRoot.setPrefSize(64, 64);
        iconRoot.setStyle("-fx-background-color: #333; -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8; -fx-cursor: hand;");

        if (seen) {
            // Intentar cargar imagen del enemigo
            try {
                // Asume ruta assets/images/enemies/TYPE.png
                String path = "assets/images/enemies/" + type.name() + ".png";
                Image img = AssetsManager.loadImage(path);

                if (img != null && !img.isError()) {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(48);
                    iv.setFitHeight(48);
                    iv.setPreserveRatio(true);
                    iconRoot.getChildren().add(iv);
                } else {
                    // Fallback visual si no hay sprite: rectángulo de color
                    Rectangle rect = new Rectangle(40, 40, getColorForType(type));
                    iconRoot.getChildren().add(rect);
                }
            } catch (Exception e) {
                // Fallback seguro
                Rectangle rect = new Rectangle(40, 40, Color.GRAY);
                iconRoot.getChildren().add(rect);
            }

            // Hover effect
            iconRoot.setOnMouseEntered(e -> iconRoot.setStyle("-fx-background-color: #444; -fx-background-radius: 8; -fx-border-color: #ffd54f; -fx-border-radius: 8; -fx-cursor: hand;"));
            iconRoot.setOnMouseExited(e -> iconRoot.setStyle("-fx-background-color: #333; -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8; -fx-cursor: hand;"));

            // Click action
            iconRoot.setOnMouseClicked(e -> showDetails(type, entry));

        } else {
            // No visto: Signo de interrogación
            Label q = new Label("?");
            q.setStyle("-fx-text-fill: #555; -fx-font-size: 24px; -fx-font-weight: bold;");
            iconRoot.getChildren().add(q);
            Tooltip.install(iconRoot, new Tooltip("Undiscovered"));
        }

        gridPane.getChildren().add(iconRoot);
    }

    private void showDetails(EnemyType type, DatabaseService.EnemyStatEntry entry) {
        detailsPanel.setVisible(true);
        detailName.setText(type.name().replace("_", " "));

        // Cargar imagen grande
        try {
            String path = "assets/images/enemies/" + type.name() + ".png";
            Image img = AssetsManager.loadImage(path);
            if (img != null && !img.isError()) {
                detailImage.setImage(img);
                detailImage.setVisible(true);
            } else {
                // Si no hay imagen, usar un rectángulo placeholder o ocultar la imagen
                detailImage.setImage(null);
            }
        } catch (Exception e) {
            detailImage.setImage(null);
        }

        // Stats DB
        statSeen.setText(String.valueOf(entry.seen()));
        statKills.setText(String.valueOf(entry.killed()));
        statDeaths.setText(String.valueOf(entry.killedBy()));

        // Stats Estáticas (GameBalance)
        EnemyProfile profile = AppContext.balance().profile(type);
        if (profile != null) {
            attrHp.setText(String.format("%.0f", profile.baseHp));
            attrDmg.setText(String.format("%.1f", profile.contactDmg));
            attrSpeed.setText(String.format("%.0f", profile.speed));
        }
    }

    private Color getColorForType(EnemyType type) {
        return switch (type) {
            case SHOOTER -> Color.ORANGE;
            case MELEE -> Color.CRIMSON;
            case TURRET -> Color.DODGERBLUE;
            case TANK -> Color.DARKOLIVEGREEN;
            case KAMIKAZE -> Color.MAGENTA;
            default -> Color.GRAY;
        };
    }

    @FXML
    private void onBack() {
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }
}
