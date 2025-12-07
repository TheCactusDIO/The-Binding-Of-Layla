package com.layla.ui;

import java.util.List;

import com.layla.AppContext;
import com.layla.core.AssetsManager;
import com.layla.items.ItemDefinition;
import com.layla.items.ItemRegistry;
import com.layla.model.AchievementDefinition;
import com.layla.model.StatModifier;
import com.layla.services.AchievementService;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
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
    @FXML private Label detailStats;
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
        boolean unlocked = isUnlocked(def);

        StackPane iconRoot = new StackPane();
        iconRoot.setPrefSize(64, 64);
        iconRoot.setStyle("-fx-background-color: #333; -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8; -fx-cursor: hand;");

        if (unlocked) {
            try {
                String path = ItemRegistry.getIconPath(def.getId());
                if (path != null) {
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
        } else {
            // Ítem bloqueado: Signo de interrogación
            Label q = new Label("?");
            q.setStyle("-fx-text-fill: #555; -fx-font-size: 24px; -fx-font-weight: bold;");
            iconRoot.getChildren().add(q);
        }

        // Efecto hover común
        iconRoot.setOnMouseEntered(e -> iconRoot.setStyle("-fx-background-color: #444; -fx-background-radius: 8; -fx-border-color: #ffd54f; -fx-border-radius: 8; -fx-cursor: hand;"));
        iconRoot.setOnMouseExited(e -> iconRoot.setStyle("-fx-background-color: #333; -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8; -fx-cursor: hand;"));

        // Clic para ver detalles (funciona tanto bloqueado como desbloqueado)
        iconRoot.setOnMouseClicked(e -> showDetails(def));

        gridPane.getChildren().add(iconRoot);
    }

    private boolean isUnlocked(ItemDefinition def) {
        if (def.isUnlockedByDefault()) return true;
        return achievements.isUnlocked(def.getRequiredAchievementId());
    }

    private void showDetails(ItemDefinition def) {
        detailsPanel.setVisible(true);
        boolean unlocked = isUnlocked(def);

        if (unlocked) {
            // --- ITEM DESBLOQUEADO ---
            detailName.setText(def.getName());
            detailName.setStyle("-fx-text-fill: #ffd54f; -fx-font-family: 'Upheaval TT (BRK)'; -fx-font-size: 24px;");

            detailDescription.setText(def.getDescription());
            detailDescription.setStyle("-fx-text-fill: white; -fx-font-style: italic;");

            detailPool.setText("Pool: " + def.getPoolType().name());

            // Imagen
            try {
                String path = ItemRegistry.getIconPath(def.getId());
                String cleanPath = path.startsWith("/") ? path.substring(1) : path;
                detailImage.setImage(AssetsManager.loadImage(cleanPath));
            } catch (Exception e) { detailImage.setImage(null); }

            // Stats
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

        } else {
            // --- ITEM BLOQUEADO ---
            detailName.setText("LOCKED ITEM");
            detailName.setStyle("-fx-text-fill: #aa0000; -fx-font-family: 'Upheaval TT (BRK)'; -fx-font-size: 24px;");

            detailPool.setText("Pool: ???");
            detailImage.setImage(null); // Se quita la imagen o se podría poner un candado
            detailStats.setText("");

            // Construir texto de condición basado en el logro requerido
            String achId = def.getRequiredAchievementId();
            if (achId != null) {
                AchievementDefinition achDef = achievements.getDefinition(achId);
                if (achDef != null) {
                    String text = "UNLOCK CONDITION:\n\n" +
                                  "Achievement: " + achDef.getName() + "\n" +
                                  "Requirement: " + achDef.getDescription();
                    detailDescription.setText(text);
                } else {
                    detailDescription.setText("Unlock condition unknown.");
                }
            } else {
                detailDescription.setText("This item is locked by mysterious means.");
            }
            detailDescription.setStyle("-fx-text-fill: #aaaaaa;");
        }
    }
}
