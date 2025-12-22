package com.layla.ui;

import java.util.List;

import com.layla.AppContext;
import com.layla.core.AssetsManager;
import com.layla.model.AchievementDefinition;
import com.layla.services.AchievementService;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class AchievementsController implements ViewLifecycle {

    // =========================
    // FXML
    // =========================
    @FXML private VBox achievementListContainer;
    @FXML private VBox detailPanel;

    @FXML private HBox mainContentBox;
    @FXML private ScrollPane scrollPane;

    @FXML private ImageView detailImage;
    @FXML private Label detailName;
    @FXML private Label detailDescription;
    @FXML private Label detailUnlockContent;
    @FXML private Label detailStatus;

    // =========================
    // SERVICIOS / DATOS
    // =========================
    private final AchievementService achievementService = AppContext.achievements();
    private final List<AchievementDefinition> allAchievements = achievementService.getAllDefinitions();

    // =========================
    // UI / ESTADO
    // =========================
    private Node selectedAchievementNode = null;

    private static final double DETAIL_WIDTH = 380.0;

    private static final String ITEM_STYLE_NORMAL =
            "-fx-padding: 10 15; -fx-background-color: #222; -fx-background-radius: 8; -fx-cursor: hand;";
    private static final String ITEM_STYLE_SELECTED =
            "-fx-padding: 10 15; -fx-background-color: #444; -fx-background-radius: 8; -fx-cursor: hand;";

    // Textos UI
    private static final String TXT_DESBLOQUEADO = "DESBLOQUEADO";
    private static final String TXT_BLOQUEADO = "BLOQUEADO";
    private static final String TXT_CONDICION_OCULTA = "Bloqueado. ¡Descubre la condición!";
    private static final String TXT_RECOMPENSA_OCULTA = "Recompensa oculta.";

    // Efecto “locked” real (grayscale) en JavaFX
    private static final ColorAdjust LOCKED_EFFECT = createLockedEffect();

    private static ColorAdjust createLockedEffect() {
        ColorAdjust ca = new ColorAdjust();
        ca.setSaturation(-1.0);   // blanco y negro
        ca.setBrightness(-0.15);  // un pelín más oscuro
        return ca;
    }

    @Override
    public void onEnter() {
        populateList();
        selectedAchievementNode = null;
        hideDetailPanelImmediately();
    }

    private void populateList() {
        achievementListContainer.getChildren().clear();
        for (AchievementDefinition def : allAchievements) {
            achievementListContainer.getChildren().add(createListItem(def));
        }
    }

    private HBox createListItem(AchievementDefinition def) {
        boolean unlocked = achievementService.isUnlocked(def.getId());

        HBox item = new HBox(15);
        item.getStyleClass().add("achievement-list-item");
        item.setStyle(ITEM_STYLE_NORMAL);
        item.setPrefHeight(60);
        item.setAlignment(Pos.CENTER_LEFT);

        ImageView iconView = new ImageView(getAchievementIcon(def.getIconPath()));
        iconView.setFitWidth(40);
        iconView.setFitHeight(40);
        iconView.setPreserveRatio(true);

        applyLockedVisual(iconView, unlocked);

        Label nameLabel = new Label(def.getName());
        String nameColor = unlocked ? "white" : "#777";
        nameLabel.setStyle("-fx-text-fill: " + nameColor + "; -fx-font-size: 16px; -fx-font-weight: bold;");

        item.getChildren().addAll(iconView, nameLabel);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        item.setOnMouseClicked(e -> selectAchievement(item, def));

        return item;
    }

    private void applyLockedVisual(ImageView iv, boolean unlocked) {
        if (iv == null) return;

        if (unlocked) {
            iv.setOpacity(1.0);
            iv.setEffect(null);
        } else {
            iv.setOpacity(0.35);
            iv.setEffect(LOCKED_EFFECT);
        }
    }

    private void selectAchievement(Node item, AchievementDefinition def) {
        boolean unlocked = achievementService.isUnlocked(def.getId());

        if (selectedAchievementNode != null) {
            selectedAchievementNode.setStyle(ITEM_STYLE_NORMAL);
        }

        selectedAchievementNode = item;
        item.setStyle(ITEM_STYLE_SELECTED);

        showDetails(def, unlocked);

        if (!detailPanel.isVisible() || detailPanel.getPrefWidth() < DETAIL_WIDTH) {
            openDetailPanelAnimated();
        }
    }

    @FXML
    private void onCloseDetails() {
        closeDetailPanelAnimated();
    }

    private void showDetails(AchievementDefinition def, boolean unlocked) {
        detailName.setText(def.getName());

        // ✅ Aquí sí ocultamos la condición si está bloqueado (tal como decía el comentario)
        detailDescription.setText(unlocked ? def.getDescription() : TXT_CONDICION_OCULTA);

        // Recompensa: si bloqueado, no spoileamos
        detailUnlockContent.setText(unlocked ? def.getUnlockContent() : TXT_RECOMPENSA_OCULTA);

        detailStatus.setText(unlocked ? TXT_DESBLOQUEADO : TXT_BLOQUEADO);
        detailStatus.setStyle("-fx-text-fill: " + (unlocked ? "#00aa00" : "#aa0000") + ";");

        Image icon = getAchievementIcon(def.getIconPath());
        detailImage.setImage(icon);
        applyLockedVisual(detailImage, unlocked);
    }

    private Image getAchievementIcon(String path) {
        String normalized = normalizeResourcePath(path);

        Image img = AssetsManager.loadImage(normalized);
        if (img != null) return img;

        // Fallback
        try {
            return new Image(getClass().getResourceAsStream("/assets/images/achievements/default.png"));
        } catch (Exception ignore) {
            return null;
        }
    }

    @FXML
    private void onBack() {
        // Limpieza visual
        onCloseDetails();
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }

    private void openDetailPanelAnimated() {
        detailPanel.setVisible(true);
        detailPanel.setManaged(true);

        Timeline timeline = new Timeline(
            new KeyFrame(Duration.millis(300),
                new KeyValue(detailPanel.prefWidthProperty(), DETAIL_WIDTH),
                new KeyValue(detailPanel.opacityProperty(), 1.0)
            )
        );
        timeline.play();
    }

    private void closeDetailPanelAnimated() {
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.millis(300),
                new KeyValue(detailPanel.prefWidthProperty(), 0),
                new KeyValue(detailPanel.opacityProperty(), 0.0)
            )
        );

        timeline.setOnFinished(e -> {
            detailPanel.setVisible(false);
            detailPanel.setManaged(false);

            if (selectedAchievementNode != null) {
                selectedAchievementNode.setStyle(ITEM_STYLE_NORMAL);
            }
            selectedAchievementNode = null;
        });

        timeline.play();
    }

    private void hideDetailPanelImmediately() {
        detailPanel.setVisible(false);
        detailPanel.setManaged(false);
        detailPanel.setPrefWidth(0);
        detailPanel.setOpacity(0);
    }

    private String normalizeResourcePath(String path) {
        if (path == null) return "assets/images/achievements/default.png";
        String p = path.trim();
        if (p.isEmpty()) return "assets/images/achievements/default.png";
        return p.startsWith("/") ? p.substring(1) : p;
    }
}
