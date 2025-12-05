package com.layla.ui;

import java.util.ArrayList;
import java.util.List;

import com.layla.core.AssetsManager;
import com.layla.items.ItemDefinition;
import com.layla.items.ItemId;
import com.layla.items.ItemRegistry;
import com.layla.services.StatsService;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class ShopOverlayController {

    public static final class ShopOffer {
        public final ItemId itemId;
        public final int price;

        public ShopOffer(ItemId itemId, int price) {
            this.itemId = itemId;
            this.price = price;
        }
    }

    @FXML private Label coinsLabel;
    @FXML private VBox offersContainer;
    @FXML private Button continueButton;

    private final List<ShopOffer> offers = new ArrayList<>();
    private int coins;
    private StatsService statsService;
    private Runnable onClose;

    public void setCoins(int coins) {
        this.coins = Math.max(0, coins);
        updateCoinsLabel();
        refreshAffordability();
    }

    public int getCoins() {
        return coins;
    }

    public void setOffers(List<ShopOffer> offers) {
        this.offers.clear();
        if (offers != null) {
            this.offers.addAll(offers);
        }
    }

    public void setStatsService(StatsService statsService) {
        this.statsService = statsService;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void onShow() {
        buildOffersUI();
        updateCoinsLabel();
        if (continueButton != null) {
            continueButton.setOnAction(e -> {
                if (onClose != null) {
                    onClose.run();
                }
            });
        }
    }

    private void buildOffersUI() {
        if (offersContainer == null) return;
        offersContainer.getChildren().clear();

        for (ShopOffer offer : offers) {
            offersContainer.getChildren().add(createOfferRow(offer));
        }
        refreshAffordability();
    }

    private HBox createOfferRow(ShopOffer offer) {
        HBox row = new HBox(10.0);
        row.setStyle("-fx-background-color: #2a2a2a; -fx-background-radius: 8; -fx-padding: 10;");
        row.setFillHeight(true);

        ImageView iconView = new ImageView();
        iconView.setFitWidth(36.0);
        iconView.setFitHeight(36.0);
        iconView.setPreserveRatio(true);
        Image icon = loadIcon(offer.itemId);
        if (icon != null) {
            iconView.setImage(icon);
        }

        VBox infoBox = new VBox(4.0);
        Label nameLabel = new Label(getItemName(offer.itemId));
        nameLabel.getStyleClass().add("hud-label");
        Label descLabel = new Label(getItemDescription(offer.itemId));
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(260.0);
        descLabel.setStyle("-fx-text-fill: #cccccc;");
        infoBox.getChildren().addAll(nameLabel, descLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label priceLabel = new Label("Cost: " + offer.price);
        priceLabel.setStyle("-fx-text-fill: #ffdd55;");

        Button buyButton = new Button("Buy");
        buyButton.setUserData(offer);
        buyButton.setOnAction(e -> handlePurchase(offer, buyButton));

        row.getChildren().addAll(iconView, infoBox, spacer, priceLabel, buyButton);
        return row;
    }

    private void handlePurchase(ShopOffer offer, Button button) {
        if (offer == null || button == null) return;
        if (button.isDisabled()) return;
        if (coins < offer.price) return;

        coins = Math.max(0, coins - offer.price);
        updateCoinsLabel();

        if (statsService != null && offer.itemId != null) {
            statsService.grantItem(offer.itemId);
        }

        button.setText("Bought");
        button.setDisable(true);
        refreshAffordability();
    }

    private void updateCoinsLabel() {
        if (coinsLabel != null) {
            coinsLabel.setText("Coins: " + coins);
        }
    }

    private void refreshAffordability() {
        if (offersContainer == null) return;
        for (var node : offersContainer.getChildren()) {
            if (node instanceof HBox row) {
                for (var child : row.getChildren()) {
                    if (child instanceof Button btn) {
                        Object ud = btn.getUserData();
                        if (ud instanceof ShopOffer offer) {
                            if (!"Bought".equals(btn.getText())) {
                                btn.setDisable(coins < offer.price);
                            }
                        }
                    }
                }
            }
        }
    }

    private String getItemName(ItemId itemId) {
        ItemDefinition def = itemId == null ? null : ItemRegistry.getDefinition(itemId);
        if (def != null && def.getName() != null) {
            return def.getName();
        }
        return itemId != null ? itemId.name() : "Item";
    }

    private String getItemDescription(ItemId itemId) {
        ItemDefinition def = itemId == null ? null : ItemRegistry.getDefinition(itemId);
        if (def != null && def.getDescription() != null) {
            return def.getDescription();
        }
        return "A mysterious item.";
    }

    private Image loadIcon(ItemId itemId) {
        if (itemId == null) return null;
        try {
            String path = ItemRegistry.getIconPath(itemId);
            if (path == null) return null;
            String rel = path.startsWith("/") ? path.substring(1) : path;
            return AssetsManager.loadImage(rel);
        } catch (Exception ex) {
            System.err.println("[ShopOverlay] Could not load icon for " + itemId + ": " + ex.getMessage());
            return null;
        }
    }
}
