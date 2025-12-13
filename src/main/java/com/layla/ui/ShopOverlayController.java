package com.layla.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import com.layla.core.AssetsManager;
import com.layla.entities.Player;
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
        public boolean sold = false; // Agregado campo sold

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
    private Player player; // Necesario para curar
    private int currentCoins;
    private int rerollBasePrice = 1;
    private List<ShopOffer> offers = new ArrayList<>();

    private IntConsumer onCoinsChanged; // Cambiado a IntConsumer
    private Runnable onItemsChanged;
    private Consumer<ShopOverlayController> onRerollRequested;
    private Consumer<Integer> onRerollPriceChanged;
    private Runnable onClose;

    // Lógica Corazón
    private int heartPrice = 0;
    private Consumer<ShopOverlayController> onHeartBuyRequest;

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

    public void setPlayer(Player player) { this.player = player; }

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

    // Métodos para el corazón
    public void setHeartPrice(int price) { this.heartPrice = price; refreshAffordability(); }
    public void setOnHeartBuyRequest(Consumer<ShopOverlayController> callback) { this.onHeartBuyRequest = callback; }

    public void setOnCoinsChanged(IntConsumer callback) { this.onCoinsChanged = callback; }
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

        // 1. Agregar Fila del Corazón
        offersContainer.getChildren().add(createHeartRow());

        // 2. Agregar Filas de Ofertas
        for (ShopOffer offer : offers) {
            offersContainer.getChildren().add(createOfferRow(offer));
        }
        refreshAffordability();
    }

    private HBox createHeartRow() {
        HBox row = new HBox(10.0);
        row.setStyle("-fx-background-color: #333333; -fx-background-radius: 8; -fx-padding: 10; -fx-border-color: #555; -fx-border-radius: 8;");
        row.setAlignment(Pos.CENTER_LEFT);

        ImageView iconView = new ImageView();
        iconView.setFitWidth(42.0);
        iconView.setFitHeight(42.0);
        iconView.setPreserveRatio(true);
        // Usar el icono del corazón de salud
        Image icon = AssetsManager.loadImage("assets/images/health_icon.png");
        if (icon != null) iconView.setImage(icon);

        VBox infoBox = new VBox(2.0);
        Label nameLabel = new Label("Red Heart");
        nameLabel.setStyle("-fx-font-size: 16; -fx-text-fill: #ff5555; -fx-font-weight: bold;");

        Label descLabel = new Label("Restores 1 Heart container.");
        descLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 11;");

        infoBox.getChildren().addAll(nameLabel, descLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox buyBox = new VBox(4);
        buyBox.setAlignment(Pos.CENTER_RIGHT);

        Label priceLabel = new Label(heartPrice + "¢");
        priceLabel.setStyle("-fx-text-fill: #ffdd55; -fx-font-weight: bold; -fx-font-size: 16;");

        Button buyButton = new Button("COMPRAR");
        buyButton.setStyle("-fx-font-size: 12; -fx-padding: 4 12; -fx-background-radius: 6; -fx-background-color: #4CAF50; -fx-text-fill: white;");

        buyButton.setOnAction(e -> {
            if (onHeartBuyRequest != null) onHeartBuyRequest.accept(this);
            // El GameController manejará la lógica y llamará a setCoins/setHeartPrice, lo que refrescará la UI
        });

        buyBox.getChildren().addAll(priceLabel, buyButton);
        row.getChildren().addAll(iconView, infoBox, spacer, buyBox);

        // Guardamos referencia al precio para actualizarlo si cambia
        row.setUserData("HEART_ROW");

        return row;
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

        Button buyButton = new Button(offer.sold ? "VENDIDO" : "COMPRAR");
        if (offer.sold) {
            buyButton.setDisable(true);
            buyButton.setStyle("-fx-background-color: #444; -fx-text-fill: #888;");
        } else {
            buyButton.setStyle("-fx-font-size: 12; -fx-padding: 4 12; -fx-background-radius: 6; -fx-background-color: #4CAF50; -fx-text-fill: white;");
        }

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

        offer.sold = true;
        button.setDisable(true);
        button.setText("VENDIDO");
        button.setStyle("-fx-background-color: #444; -fx-text-fill: #888;");

        if (onItemsChanged != null) onItemsChanged.run();

        buildStatsUI();
        refreshAffordability();
    }

    private void onRerollAction() {
        if (currentCoins >= rerollBasePrice) {
            currentCoins -= rerollBasePrice;
            if (onCoinsChanged != null) onCoinsChanged.accept(currentCoins);

            // Incremento aleatorio entre 1 y 3
            int increment = ThreadLocalRandom.current().nextInt(1, 4);
            rerollBasePrice += increment;

            if (onRerollPriceChanged != null) onRerollPriceChanged.accept(rerollBasePrice);

            if (onRerollRequested != null) {
                onRerollRequested.accept(this);
            }

            updateCoinsLabel();
            updateRerollLabel();

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
                if ("HEART_ROW".equals(row.getUserData())) {
                    // Actualizar fila corazón
                    VBox buyBox = (VBox) row.getChildren().get(row.getChildren().size() - 1);
                    Label priceLbl = (Label) buyBox.getChildren().get(0);
                    Button btn = (Button) buyBox.getChildren().get(1);

                    priceLbl.setText(heartPrice + "¢");

                    boolean canAfford = currentCoins >= heartPrice;
                    boolean healthFull = (player != null && player.getHealth() >= player.getMaxHealth());

                    btn.setDisable(!canAfford || healthFull);

                    if (healthFull) {
                        btn.setText("LLENO");
                        btn.setStyle("-fx-background-color: #555; -fx-text-fill: #aaa;");
                    } else if (!canAfford) {
                        btn.setText("COMPRAR");
                        btn.setStyle("-fx-background-color: #555; -fx-text-fill: #aaa;");
                    } else {
                        btn.setText("COMPRAR");
                        btn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
                    }
                    continue;
                }

                // Ofertas normales
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
