package com.layla.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import com.layla.core.AssetsManager;
import com.layla.items.ItemDefinition;
import com.layla.items.ItemId;
import com.layla.items.ItemRegistry;
import com.layla.services.StatsService;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class ShopOverlayController {

    public static class ShopOffer {
        public final ItemId itemId;
        public final int price;
        public ShopOffer(ItemId itemId, int price) {
            this.itemId = itemId;
            this.price = price;
        }
    }

    @FXML private Label coinsLabel;
    @FXML private VBox offersContainer;
    @FXML private VBox statsContainer;
    @FXML private Button continueButton;
    @FXML private Button rerollButton;

    private StatsService statsService;
    private int currentCoins;
    private int rerollBasePrice = 1;
    private List<ShopOffer> offers = new ArrayList<>();

    private Consumer<Integer> onCoinsChanged;
    private Runnable onItemsChanged;
    private Consumer<ShopOverlayController> onRerollRequested;
    private Consumer<Integer> onRerollPriceChanged;
    private Runnable onClose;

    @FXML
    private void initialize() {
        if (statsContainer != null) {
            statsContainer.setStyle("-fx-background-color: rgba(30, 30, 30, 0.95); -fx-background-radius: 10; -fx-padding: 15; -fx-border-color: #444; -fx-border-radius: 10; -fx-border-width: 2;");
        }

        if (offers != null && !offers.isEmpty()) {
            buildOffersUI();
        }
        buildStatsUI();

        if (continueButton != null) {
            continueButton.setText("EXIT SHOP");
            continueButton.setOnAction(e -> onCloseAction());
        }

        if (rerollButton != null) {
            rerollButton.setOnAction(e -> onRerollAction());
        }
    }

    public void setStatsService(StatsService stats) {
        this.statsService = stats;
        buildStatsUI();
    }

    public void setCoins(int coins) {
        this.currentCoins = Math.max(0, coins);
        updateCoinsLabel();
        refreshAffordability();
    }

    public int getCoins() { return currentCoins; }

    public void setRerollBasePrice(int price) {
        this.rerollBasePrice = Math.max(0, price);
        updateRerollLabel();
        refreshAffordability();
    }

    public int getRerollBasePrice() { return this.rerollBasePrice; }

    public void setOffers(List<ShopOffer> offers) {
        this.offers.clear();
        if (offers != null) {
            this.offers.addAll(offers);
        }
        if (offersContainer != null) {
            buildOffersUI();
        }
    }

    public void setOnCoinsChanged(Consumer<Integer> callback) { this.onCoinsChanged = callback; }
    public void setOnItemsChanged(Runnable callback) { this.onItemsChanged = callback; }
    public void setOnRerollRequested(Consumer<ShopOverlayController> callback) { this.onRerollRequested = callback; }
    public void setOnRerollPriceChanged(Consumer<Integer> callback) { this.onRerollPriceChanged = callback; }
    public void setOnClose(Runnable callback) { this.onClose = callback; }

    public void onShow() {
        updateCoinsLabel();
        updateRerollLabel();
        buildOffersUI();
        buildStatsUI();
        refreshAffordability();

        if (continueButton != null) {
            continueButton.setText("EXIT SHOP");
        }
    }

    private void updateCoinsLabel() {
        if (coinsLabel != null) coinsLabel.setText("Coins: " + currentCoins + "¢");
    }

    private void updateRerollLabel() {
        if (rerollButton != null) {
            rerollButton.setText("Reroll (" + rerollBasePrice + "¢)");
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
        row.setStyle("-fx-background-color: #333333; -fx-background-radius: 8; -fx-padding: 10; -fx-border-color: #555; -fx-border-radius: 8;");
        row.setAlignment(Pos.CENTER_LEFT);

        ImageView iconView = new ImageView();
        iconView.setFitWidth(42.0);
        iconView.setFitHeight(42.0);
        iconView.setPreserveRatio(true);
        String path = ItemRegistry.getIconPath(offer.itemId);
        if (path != null) {
             Image icon = AssetsManager.loadImage(path.startsWith("/") ? path.substring(1) : path);
             if (icon != null) iconView.setImage(icon);
        }

        VBox infoBox = new VBox(2.0);
        ItemDefinition def = ItemRegistry.getDefinition(offer.itemId);
        String name = (def != null ? def.getName() : offer.itemId.name());
        String desc = (def != null ? def.getDescription() : "???");

        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-size: 16; -fx-text-fill: #ffd54f; -fx-font-weight: bold;");

        Label descLabel = new Label(desc);
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(220.0);
        descLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 11;");

        infoBox.getChildren().addAll(nameLabel, descLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox buyBox = new VBox(4);
        buyBox.setAlignment(Pos.CENTER_RIGHT);

        Label priceLabel = new Label(offer.price + "¢");
        priceLabel.setStyle("-fx-text-fill: #ffdd55; -fx-font-weight: bold; -fx-font-size: 16;");

        Button buyButton = new Button("COMPRAR");
        buyButton.setStyle("-fx-font-size: 12; -fx-padding: 4 12; -fx-background-radius: 6; -fx-background-color: #4CAF50; -fx-text-fill: white;");
        buyButton.setUserData(offer);
        buyButton.setOnAction(e -> handlePurchase(offer, buyButton));

        buyBox.getChildren().addAll(priceLabel, buyButton);

        row.getChildren().addAll(iconView, infoBox, spacer, buyBox);
        return row;
    }

    private void handlePurchase(ShopOffer offer, Button button) {
        if (currentCoins < offer.price) return;

        currentCoins -= offer.price;
        if (onCoinsChanged != null) onCoinsChanged.accept(currentCoins);
        updateCoinsLabel();

        if (statsService != null) {
            statsService.grantItem(offer.itemId);
        }

        button.setDisable(true);
        button.setText("VENDIDO");
        button.setStyle("-fx-background-color: #444; -fx-text-fill: #888;");

        offers.remove(offer);

        if (onItemsChanged != null) onItemsChanged.run();

        buildStatsUI();
        refreshAffordability();
    }

    private void onRerollAction() {
        if (currentCoins >= rerollBasePrice) {
            currentCoins -= rerollBasePrice;
            if (onCoinsChanged != null) onCoinsChanged.accept(currentCoins);

            // Incremento aleatorio entre 1 y 3
            int increment = ThreadLocalRandom.current().nextInt(1, 4); // 1, 2, o 3
            rerollBasePrice += increment;

            if (onRerollPriceChanged != null) onRerollPriceChanged.accept(rerollBasePrice);

            if (onRerollRequested != null) {
                onRerollRequested.accept(this);
            }

            updateCoinsLabel();
            updateRerollLabel();

            // Mostrar texto flotante con el coste pagado
            showRerollFloatText(rerollBasePrice - increment);
            refreshAffordability();
        }
    }

    private void onCloseAction() {
        if (onClose != null) onClose.run();
    }

    private void refreshAffordability() {
        if (offersContainer == null) return;

        for (Node node : offersContainer.getChildren()) {
            if (node instanceof HBox row) {
                if (!row.getChildren().isEmpty() && row.getChildren().get(row.getChildren().size() - 1) instanceof VBox buyBox) {
                    for (Node n : buyBox.getChildren()) {
                        if (n instanceof Button btn && !btn.getText().equals("VENDIDO")) {
                            Object ud = btn.getUserData();
                            if (ud instanceof ShopOffer offer) {
                                btn.setDisable(currentCoins < offer.price);
                                if (currentCoins < offer.price) {
                                    btn.setStyle("-fx-background-color: #555; -fx-text-fill: #aaa; -fx-font-size: 12; -fx-padding: 4 12; -fx-background-radius: 6;");
                                } else {
                                    btn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 12; -fx-padding: 4 12; -fx-background-radius: 6;");
                                }
                            }
                        }
                    }
                }
            }
        }

        if (rerollButton != null) {
            rerollButton.setDisable(currentCoins < rerollBasePrice);
        }
    }

    private void buildStatsUI() {
        if (statsContainer == null || statsService == null) return;
        statsContainer.getChildren().clear();

        addStatRow("Max HP",       statsService.getMaxHealth(),       false, "Vida máxima total.");
        addStatRow("Regeneration", statsService.getHpRegen(),         false, "Vida recuperada cada segundo.");
        addStatRow("Lifesteal",    statsService.getLifesteal(),       true,  "Probabilidad de curarse al dañar.");
        addStatRow("Damage",       statsService.getProjectileDamage(),false, "Daño por disparo.");
        addStatRow("Fire Rate",    statsService.getFireRate(),        false, "Disparos por segundo.");
        addStatRow("Speed",        statsService.getMoveSpeed(),       false, "Velocidad de movimiento.");
        addStatRow("Armor",        statsService.getArmor(),           false, "Reducción de daño.");
        addStatRow("Luck",         statsService.getLuck(),            false, "Suerte en drops.");
    }

    private void addStatRow(String name, double value, boolean isPercent, String desc) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 0, 2, 0));

        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-text-fill: #ccc; -fx-font-weight: bold; -fx-font-size: 12;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String valStr = isPercent ? String.format("%.0f%%", value) : String.format("%.1f", value);
        Label valLbl = new Label(valStr);

        String color = "#fff";
        if (value > 0) color = "#4caf50";

        valLbl.setStyle("-fx-text-fill: " + color + "; -fx-font-family: 'Consolas'; -fx-font-size: 12; -fx-font-weight: bold;");

        if (desc != null) {
            Tooltip tt = new Tooltip(desc);
            Tooltip.install(row, tt);
        }

        row.getChildren().addAll(nameLbl, spacer, valLbl);
        statsContainer.getChildren().add(row);
    }

    private void showRerollFloatText(int costPaid) {
        if (rerollButton == null || rerollButton.getScene() == null) return;

        Label floating = new Label("- " + costPaid + "¢");
        floating.setStyle("-fx-text-fill: #ff5555; -fx-font-size: 16px; -fx-font-weight: bold; -fx-effect: dropshadow(gaussian, black, 2, 1, 0, 0);");

        Scene scene = rerollButton.getScene();
        if (scene.getRoot() instanceof StackPane root) {
            root.getChildren().add(floating);

            var p = rerollButton.localToScene(rerollButton.getWidth()/2, 0);
            var local = root.sceneToLocal(p.getX(), p.getY());

            floating.setTranslateX(local.getX());
            floating.setTranslateY(local.getY());

            Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(floating.translateYProperty(), local.getY()),
                    new KeyValue(floating.opacityProperty(), 1.0)
                ),
                new KeyFrame(Duration.millis(600),
                    new KeyValue(floating.translateYProperty(), local.getY() - 30),
                    new KeyValue(floating.opacityProperty(), 0.0)
                )
            );
            tl.setOnFinished(e -> root.getChildren().remove(floating));
            tl.play();
        }
    }

    @FXML private void onReroll() { onRerollAction(); }
    @FXML private void onClose() { onCloseAction(); }
}
