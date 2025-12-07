package com.layla.ui;

import java.util.List;

import com.layla.AppContext;
import com.layla.core.AssetsManager;
import com.layla.items.ItemDefinition;
import com.layla.items.ItemRegistry;
import com.layla.model.StatModifier;
import com.layla.services.AchievementService;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class ItemsController implements ViewLifecycle {

    @FXML private FlowPane gridPane;
    @FXML private VBox detailsPanel;

    // UI Detalles
    @FXML private ImageView detailImage;
    @FXML private Label detailName;
    @FXML private Label detailDescription;
    @FXML private Label detailStats; // Para listar los modificadores (+Damage, etc.)
    @FXML private Label detailPool;

    private final AchievementService achievements = AppContext.achievements();

    @Override
    public void onEnter() {
        loadData();
    }

    public void loadData() {
        gridPane.getChildren().clear();
        detailsPanel.setVisible(false);

        List<ItemDefinition> allItems = ItemRegistry.allDefinitions();

        for (ItemDefinition def : allItems) {
            createItemIcon(def);
        }
    }

    private void createItemIcon(ItemDefinition def) {
        // Verificar si está desbloqueado
        boolean unlocked = def.isUnlockedByDefault();
        if (!unlocked && def.getRequiredAchievementId() != null) {
            unlocked = achievements.isUnlocked(def.getRequiredAchievementId());
        }

        StackPane iconRoot = new StackPane();
        iconRoot.setPrefSize(64, 64);
        iconRoot.setStyle("-fx-background-color: #333; -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8; -fx-cursor: hand;");

        if (unlocked) {
            try {
                String path = ItemRegistry.getIconPath(def.getId());
                if (path != null) {
                    // Quitar el slash inicial si existe para AssetsManager
                    String cleanPath = path.startsWith("/") ? path.substring(1) : path;
                    Image img = AssetsManager.loadImage(cleanPath);
                    if (img != null) {
                        ImageView iv = new ImageView(img);
                        iv.setFitWidth(48); iv.setFitHeight(48);
                        iv.setPreserveRatio(true);
                        iconRoot.getChildren().add(iv);
                    }
                }
            } catch (Exception e) { /* Fallback */ }

            iconRoot.setOnMouseEntered(e -> iconRoot.setStyle("-fx-background-color: #444; -fx-background-radius: 8; -fx-border-color: #ffd54f; -fx-border-radius: 8; -fx-cursor: hand;"));
            iconRoot.setOnMouseExited(e -> iconRoot.setStyle("-fx-background-color: #333; -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8; -fx-cursor: hand;"));
            iconRoot.setOnMouseClicked(e -> showDetails(def));

        } else {
            // Ítem bloqueado
            Label q = new Label("?");
            q.setStyle("-fx-text-fill: #555; -fx-font-size: 24px; -fx-font-weight: bold;");
            iconRoot.getChildren().add(q);
        }

        gridPane.getChildren().add(iconRoot);
    }

    private void showDetails(ItemDefinition def) {
        detailsPanel.setVisible(true);
        detailName.setText(def.getName());
        detailDescription.setText(def.getDescription());
        detailPool.setText("Pool: " + def.getPoolType().name());

        // Cargar imagen grande
        try {
            String path = ItemRegistry.getIconPath(def.getId());
            String cleanPath = path.startsWith("/") ? path.substring(1) : path;
            detailImage.setImage(AssetsManager.loadImage(cleanPath));
        } catch (Exception e) { detailImage.setImage(null); }

        // Formatear stats
        StringBuilder statsText = new StringBuilder();
        for (StatModifier mod : def.getModifiers()) {
            if (mod.getAdditive() != 0) {
                String sign = mod.getAdditive() > 0 ? "+" : "";
                statsText.append(String.format("%s%.1f %s\n", sign, mod.getAdditive(), mod.getStatId().name()));
            }
            if (mod.getMultiplicative() != 1.0) {
                statsText.append(String.format("x%.2f %s\n", mod.getMultiplicative(), mod.getStatId().name()));
            }
        }
        detailStats.setText(statsText.toString());
    }
}
