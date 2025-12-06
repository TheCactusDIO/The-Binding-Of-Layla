package com.layla.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import com.layla.core.AssetsManager;
import com.layla.items.ItemDefinition;
import com.layla.items.ItemId;
import com.layla.items.ItemRegistry;
import com.layla.services.StatsService;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;                      // ← IMPORT NECESARIO
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

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
    @FXML private Button rerollButton;

    private final List<ShopOffer> offers = new ArrayList<>();
    private int coins;
    private int rerollBasePrice = 1;
    private int rerollPrice = 1;
    private StatsService statsService;
    private Runnable onClose;
    private IntConsumer onCoinsChanged;
    private Consumer<ShopOverlayController> onRerollRequested;
    private Runnable onItemsChanged;

    public void setCoins(int coins) {
        this.coins = Math.max(0, coins);
        updateCoinsLabel();
        refreshAffordability();
    }

    public int getCoins() {
        return coins;
    }
    // 🔹 Nuevo setter para el callback de items comprados
    public void setOnItemsChanged(Runnable onItemsChanged) {
        this.onItemsChanged = onItemsChanged;
    }


    public void setOffers(List<ShopOffer> offers) {
        this.offers.clear();
        if (offers != null) {
            this.offers.addAll(offers);
        }
        buildOffersUI();
    }

    public void setStatsService(StatsService statsService) {
        this.statsService = statsService;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void setOnCoinsChanged(IntConsumer onCoinsChanged) {
        this.onCoinsChanged = onCoinsChanged;
    }

    public void setOnRerollRequested(Consumer<ShopOverlayController> onRerollRequested) {
        this.onRerollRequested = onRerollRequested;
    }

    public void setRerollBasePrice(int basePrice) {
        this.rerollBasePrice = Math.max(0, basePrice);
    }

    public void onShow() {
        rerollPrice = Math.max(0, rerollBasePrice);
        buildOffersUI();
        updateCoinsLabel();
        updateRerollLabel();
        if (continueButton != null) {
            continueButton.setOnAction(e -> {
                if (onClose != null) {
                    onClose.run();
                }
            });
        }
        if (rerollButton != null) {
            rerollButton.setOnAction(e -> handleReroll());
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
        if (button.isDisable()) return; // ya comprado
        if (coins < offer.price) return;

        coins = Math.max(0, coins - offer.price);
        updateCoinsLabel();
        notifyCoinsChanged();

        if (statsService != null && offer.itemId != null) {
            statsService.grantItem(offer.itemId);
        }

        // Avisar al GameController de que las stats/items han cambiado
        if (onItemsChanged != null) {
            onItemsChanged.run();
        }

        button.setDisable(true);
        button.setText("Comprado");
        button.setUserData(null); // para que refreshAffordability no lo vuelva a tocar

        var parent = button.getParent();
        if (parent instanceof HBox row) {
            String old = row.getStyle() != null ? row.getStyle() : "";
            row.setStyle(
                old +
                "; -fx-opacity: 0.55;" +
                " -fx-background-color: #202020;"
            );
        }

        refreshAffordability();
    }

    private void handleReroll() {
        if (coins < rerollPrice) return;

        coins = Math.max(0, coins - rerollPrice);
        notifyCoinsChanged();
        updateCoinsLabel();

        int inc = incrementRerollPrice();
        updateRerollLabel();
        refreshAffordability();

        showRerollFloatText(inc);

        if (onRerollRequested != null) {
            onRerollRequested.accept(this);
        }
    }

    private void updateCoinsLabel() {
        if (coinsLabel != null) {
            coinsLabel.setText("Coins: " + coins);
        }
    }

    private void updateRerollLabel() {
        if (rerollButton != null) {
            rerollButton.setText("Reroll (" + rerollPrice + "¢)");
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
                            btn.setDisable(coins < offer.price);
                        }
                    }
                }
            }
        }
        if (rerollButton != null) {
            rerollButton.setDisable(coins < rerollPrice);
        }
    }

    private void notifyCoinsChanged() {
        if (onCoinsChanged != null) {
            onCoinsChanged.accept(coins);
        }
    }

    private int incrementRerollPrice() {
        int inc = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 8);
        rerollPrice = rerollPrice + inc;
        return inc;
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

    private void showRerollFloatText(int inc) {
        if (rerollButton == null || rerollButton.getScene() == null) return;

        Label floating = new Label("+" + inc + "¢");
        floating.setStyle("-fx-text-fill: #ffdd55; -fx-font-size: 20px; -fx-font-weight: bold;");

        Scene scene = rerollButton.getScene();
        if (!(scene.getRoot() instanceof StackPane root)) {
            return; // por si cambias el root algún día
        }

        root.getChildren().add(floating);

        // Coordenadas en escena
        var p = rerollButton.localToScene(0, 0);
        double x = p.getX() + rerollButton.getWidth() * 0.5 - 10;
        double y = p.getY() - 10;

        // Las convertimos a coords del root
        var localInRoot = root.sceneToLocal(x, y);
        floating.setTranslateX(localInRoot.getX());
        floating.setTranslateY(localInRoot.getY());

        Timeline tl = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(floating.opacityProperty(), 1.0),
                new KeyValue(floating.translateYProperty(), floating.getTranslateY())
            ),
            new KeyFrame(Duration.millis(900),
                new KeyValue(floating.opacityProperty(), 0.0),
                new KeyValue(floating.translateYProperty(), floating.getTranslateY() - 35)
            )
        );

        tl.setOnFinished(e -> root.getChildren().remove(floating));
        tl.play();
    }
}
