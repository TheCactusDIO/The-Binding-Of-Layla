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
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public final class ShopOverlayController {

    public static final class ShopOffer {
        public final ItemId itemId;
        public final int price;
        public boolean sold = false;

        public ShopOffer(ItemId itemId, int price) {
            this.itemId = itemId;
            this.price = price;
        }
    }

    private static final String HEART_ROW_TAG = "HEART_ROW";

    @FXML private StackPane root;
    @FXML private Label coinsLabel;
    @FXML private VBox offersContainer;
    @FXML private VBox statsContainer;
    @FXML private Button continueButton;
    @FXML private Button rerollButton;

    private StatsService statsService;
    private Player player;

    private int currentCoins;
    private int rerollBasePrice = 1;
    private final List<ShopOffer> offers = new ArrayList<>();

    private IntConsumer onCoinsChanged = ShopOverlayController::noopInt;
    private Runnable onItemsChanged = ShopOverlayController::noop;
    private Consumer<ShopOverlayController> onRerollRequested = c -> {};
    private Consumer<Integer> onRerollPriceChanged = i -> {};
    private Runnable onClose = ShopOverlayController::noop;

    // Corazón
    private int heartPrice = 0;
    private Consumer<ShopOverlayController> onHeartBuyRequest = c -> {};

    @FXML
    private void initialize() {
        // Atajos de teclado del overlay
        if (root != null) {
            root.setFocusTraversable(true);
            root.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    onCloseAction();
                    e.consume();
                } else if (e.getCode() == KeyCode.R) {
                    onRerollAction();
                    e.consume();
                }
            });
        }
    }

    // ---------------------------
    // Setters / callbacks
    // ---------------------------

    public void setStatsService(StatsService stats) {
        this.statsService = stats;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public void setCoins(int coins) {
        this.currentCoins = Math.max(0, coins);
        updateCoinsLabel();
        refreshAffordability();
    }

    public int getCoins() {
        return currentCoins;
    }

    public void setRerollBasePrice(int price) {
        this.rerollBasePrice = Math.max(0, price);
        updateRerollLabel();
        refreshAffordability();
    }

    public void setOffers(List<ShopOffer> newOffers) {
        offers.clear();
        if (newOffers != null) offers.addAll(newOffers);
        if (offersContainer != null) buildOffersUI();
    }

    public void setHeartPrice(int price) {
        this.heartPrice = Math.max(0, price);
        refreshAffordability();
    }

    public void setOnHeartBuyRequest(Consumer<ShopOverlayController> callback) {
        this.onHeartBuyRequest = (callback != null) ? callback : c -> {};
    }

    public void setOnCoinsChanged(IntConsumer callback) {
        this.onCoinsChanged = (callback != null) ? callback : ShopOverlayController::noopInt;
    }

    public void setOnItemsChanged(Runnable callback) {
        this.onItemsChanged = (callback != null) ? callback : ShopOverlayController::noop;
    }

    public void setOnRerollRequested(Consumer<ShopOverlayController> callback) {
        this.onRerollRequested = (callback != null) ? callback : c -> {};
    }

    public void setOnRerollPriceChanged(Consumer<Integer> callback) {
        this.onRerollPriceChanged = (callback != null) ? callback : i -> {};
    }

    public void setOnClose(Runnable callback) {
        this.onClose = (callback != null) ? callback : ShopOverlayController::noop;
    }

    // ---------------------------
    // Lifecycle
    // ---------------------------

    public void onShow() {
        if (root != null) root.requestFocus();

        updateCoinsLabel();
        updateRerollLabel();
        buildOffersUI();
        buildStatsUI();
        refreshAffordability();

        if (continueButton != null) {
            continueButton.setText("SALIR DE LA TIENDA");
        }
        if (rerollButton != null) {
            // El texto real lo pone updateRerollLabel()
        }
    }

    // ---------------------------
    // UI updates
    // ---------------------------

    private void updateCoinsLabel() {
        if (coinsLabel != null) coinsLabel.setText("Monedas: " + currentCoins + "¢");
    }

    private void updateRerollLabel() {
        if (rerollButton != null) rerollButton.setText("Rebarajar (" + rerollBasePrice + "¢)");
    }

    private void buildOffersUI() {
        if (offersContainer == null) return;
        offersContainer.getChildren().clear();

        offersContainer.getChildren().add(createHeartRow());

        for (ShopOffer offer : offers) {
            offersContainer.getChildren().add(createOfferRow(offer));
        }

        refreshAffordability();
    }

    private HBox createHeartRow() {
        HBox row = new HBox(10.0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: #333333; -fx-background-radius: 8; -fx-padding: 10; -fx-border-color: #555; -fx-border-radius: 8;");

        ImageView iconView = new ImageView();
        iconView.setFitWidth(42.0);
        iconView.setFitHeight(42.0);
        iconView.setPreserveRatio(true);

        Image icon = AssetsManager.loadImage("assets/images/health_icon.png");
        if (icon != null) iconView.setImage(icon);

        VBox infoBox = new VBox(2.0);

        Label nameLabel = new Label("Corazón rojo");
        nameLabel.setStyle("-fx-font-size: 16; -fx-text-fill: #ff5555; -fx-font-weight: bold;");

        Label descLabel = new Label("Restaura 1 corazón (2 HP).");
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
        buyButton.setOnAction(e -> onHeartBuyRequest.accept(this));

        buyBox.getChildren().addAll(priceLabel, buyButton);

        row.getChildren().addAll(iconView, infoBox, spacer, buyBox);
        row.setUserData(HEART_ROW_TAG);

        return row;
    }

    private HBox createOfferRow(ShopOffer offer) {
        HBox row = new HBox(10.0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: #333333; -fx-background-radius: 8; -fx-padding: 10; -fx-border-color: #555; -fx-border-radius: 8;");

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
        if (offer.sold) return;
        if (currentCoins < offer.price) return;

        currentCoins -= offer.price;
        onCoinsChanged.accept(currentCoins);
        updateCoinsLabel();

        if (statsService != null) {
            statsService.grantItem(offer.itemId);
        }

        offer.sold = true;
        button.setDisable(true);
        button.setText("VENDIDO");
        button.setStyle("-fx-background-color: #444; -fx-text-fill: #888;");

        onItemsChanged.run();

        buildStatsUI();
        refreshAffordability();
    }

    private void onRerollAction() {
        if (currentCoins < rerollBasePrice) return;

        currentCoins -= rerollBasePrice;
        onCoinsChanged.accept(currentCoins);

        int increment = ThreadLocalRandom.current().nextInt(1, 4);
        int costPaid = rerollBasePrice;

        rerollBasePrice += increment;
        onRerollPriceChanged.accept(rerollBasePrice);

        onRerollRequested.accept(this);

        updateCoinsLabel();
        updateRerollLabel();

        showRerollFloatText(costPaid);
        refreshAffordability();
    }

    private void onCloseAction() {
        onClose.run();
    }

    private void refreshAffordability() {
        if (offersContainer == null) return;

        for (Node node : offersContainer.getChildren()) {
            if (!(node instanceof HBox row)) continue;

            // Corazón
            if (HEART_ROW_TAG.equals(row.getUserData())) {
                VBox buyBox = (VBox) row.getChildren().get(row.getChildren().size() - 1);
                Label priceLbl = (Label) buyBox.getChildren().get(0);
                Button btn = (Button) buyBox.getChildren().get(1);

                priceLbl.setText(heartPrice + "¢");

                boolean canAfford = currentCoins >= heartPrice;
                boolean healthFull = (player != null && player.getHealth() >= player.getMaxHealth());

                if (healthFull) {
                    btn.setText("LLENO");
                    btn.setDisable(true);
                    btn.setStyle("-fx-background-color: #555; -fx-text-fill: #aaa; -fx-font-size: 12; -fx-padding: 4 12; -fx-background-radius: 6;");
                } else if (!canAfford) {
                    btn.setText("COMPRAR");
                    btn.setDisable(true);
                    btn.setStyle("-fx-background-color: #555; -fx-text-fill: #aaa; -fx-font-size: 12; -fx-padding: 4 12; -fx-background-radius: 6;");
                } else {
                    btn.setText("COMPRAR");
                    btn.setDisable(false);
                    btn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 12; -fx-padding: 4 12; -fx-background-radius: 6;");
                }
                continue;
            }

            // Ofertas normales
            if (row.getChildren().isEmpty()) continue;

            Node last = row.getChildren().get(row.getChildren().size() - 1);
            if (!(last instanceof VBox buyBox)) continue;

            for (Node n : buyBox.getChildren()) {
                if (!(n instanceof Button btn)) continue;
                if ("VENDIDO".equals(btn.getText())) continue;

                Object ud = btn.getUserData();
                if (!(ud instanceof ShopOffer offer)) continue;

                if (currentCoins < offer.price) {
                    btn.setDisable(true);
                    btn.setStyle("-fx-background-color: #555; -fx-text-fill: #aaa; -fx-font-size: 12; -fx-padding: 4 12; -fx-background-radius: 6;");
                } else {
                    btn.setDisable(false);
                    btn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 12; -fx-padding: 4 12; -fx-background-radius: 6;");
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

        addStatRow("Vida máxima", statsService.getMaxHealth(), false, "Vida máxima total.");
        addStatRow("Daño",        statsService.getProjectileDamage(), false, "Daño por disparo.");
        addStatRow("Cadencia",    statsService.getFireRate(), false, "Disparos por segundo.");
        addStatRow("Velocidad",   statsService.getMoveSpeed(), false, "Velocidad de movimiento.");
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
        valLbl.setStyle("-fx-text-fill: #fff; -fx-font-family: 'Consolas'; -fx-font-size: 12; -fx-font-weight: bold;");

        if (desc != null && !desc.isBlank()) {
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
        if (scene.getRoot() instanceof StackPane spRoot) {
            spRoot.getChildren().add(floating);

            var p = rerollButton.localToScene(rerollButton.getWidth() / 2, 0);
            var local = spRoot.sceneToLocal(p.getX(), p.getY());

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
            tl.setOnFinished(e -> spRoot.getChildren().remove(floating));
            tl.play();
        }
    }

    // Handlers FXML
    @FXML private void onReroll() { onRerollAction(); }
    @FXML private void onClose()  { onCloseAction(); }

    private static void noop() {}
    private static void noopInt(int v) {}
}
