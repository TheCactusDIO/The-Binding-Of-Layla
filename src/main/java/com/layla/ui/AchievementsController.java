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
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class AchievementsController implements ViewLifecycle {

    @FXML private VBox achievementListContainer;
    @FXML private VBox detailPanel;
    @FXML private HBox mainContentBox;
    @FXML private ScrollPane scrollPane;

    // Detail Panel fields
    @FXML private ImageView detailImage;
    @FXML private Label detailName;
    @FXML private Label detailDescription;
    @FXML private Label detailUnlockContent;
    @FXML private Label detailStatus;

    private final AchievementService achievementService = AppContext.achievements();
    private final List<AchievementDefinition> allAchievements = achievementService.getAllDefinitions();

    private Node selectedAchievementNode = null;
    private static final double DETAIL_WIDTH = 380.0;

    @Override
    public void onEnter() {
        populateList();

        // Estado inicial: panel oculto
        detailPanel.setVisible(false);
        detailPanel.setManaged(false);
        detailPanel.setPrefWidth(0);
        detailPanel.setOpacity(0);
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
        item.setStyle("-fx-padding: 10 15; -fx-background-color: #222; -fx-background-radius: 8; -fx-cursor: hand;");
        item.setPrefHeight(60);
        item.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        ImageView iconView = new ImageView(getAchievementIcon(def.getIconPath()));
        iconView.setFitWidth(40);
        iconView.setFitHeight(40);
        iconView.setPreserveRatio(true);

        if (!unlocked) {
            iconView.setStyle("-fx-opacity: 0.3; -fx-effect: grayscale(1.0);");
        }

        Label nameLabel = new Label(def.getName());
        nameLabel.setStyle("-fx-text-fill: " + (unlocked ? "white" : "#666") + "; -fx-font-size: 16px; -fx-font-weight: bold;");

        item.getChildren().addAll(iconView, nameLabel);
        HBox.setHgrow(nameLabel, javafx.scene.layout.Priority.ALWAYS);

        item.setOnMouseClicked(e -> selectAchievement(item, def, unlocked));

        return item;
    }

    private void selectAchievement(Node item, AchievementDefinition def, boolean unlocked) {
        if (selectedAchievementNode != null) {
            selectedAchievementNode.setStyle(selectedAchievementNode.getStyle().replace("-fx-background-color: #444", "-fx-background-color: #222"));
        }

        selectedAchievementNode = item;
        item.setStyle(item.getStyle().replace("-fx-background-color: #222", "-fx-background-color: #444"));

        showDetails(def, unlocked);

        // Animación de apertura del panel (si no estaba ya abierto)
        if (!detailPanel.isVisible() || detailPanel.getPrefWidth() < DETAIL_WIDTH) {
            detailPanel.setVisible(true);
            detailPanel.setManaged(true);

            // Animamos el ANCHO, lo que empuja suavemente la lista hacia la izquierda
            Timeline timeline = new Timeline();
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(300),
                new KeyValue(detailPanel.prefWidthProperty(), DETAIL_WIDTH),
                new KeyValue(detailPanel.opacityProperty(), 1.0)
            ));
            timeline.play();
        }
    }

    @FXML
    private void onCloseDetails() {
        // Animación de cierre del panel
        Timeline timeline = new Timeline();
        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(300),
            new KeyValue(detailPanel.prefWidthProperty(), 0),
            new KeyValue(detailPanel.opacityProperty(), 0.0)
        ));

        timeline.setOnFinished(e -> {
            detailPanel.setVisible(false);
            detailPanel.setManaged(false);
            if (selectedAchievementNode != null) {
                 selectedAchievementNode.setStyle(selectedAchievementNode.getStyle().replace("-fx-background-color: #444", "-fx-background-color: #222"));
            }
            selectedAchievementNode = null;
        });
        timeline.play();
    }

    private void showDetails(AchievementDefinition def, boolean unlocked) {
        detailName.setText(def.getName());
        detailDescription.setText(def.getDescription());
        detailUnlockContent.setText(unlocked ? def.getUnlockContent() : "Locked. Discover the condition!");
        detailStatus.setText(unlocked ? "UNLOCKED" : "LOCKED");
        detailStatus.setStyle("-fx-text-fill: " + (unlocked ? "#00aa00" : "#aa0000") + ";");

        Image icon = getAchievementIcon(def.getIconPath());
        detailImage.setImage(icon);
        if (!unlocked) {
             detailImage.setStyle("-fx-opacity: 0.3; -fx-effect: grayscale(1.0);");
        } else {
             detailImage.setStyle(null);
        }
    }

    private Image getAchievementIcon(String path) {
        try {
            return AssetsManager.loadImage(path.startsWith("/") ? path.substring(1) : path);
        } catch (Exception e) {
            return new Image(getClass().getResourceAsStream("/assets/images/achievements/default.png"));
        }
    }

    @FXML
    private void onBack() {
        // Cierre suave antes de salir
        onCloseDetails();
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }
}
