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
    @FXML private VBox statsContainer; // Contenedor para las stats (derecha)
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

    public int getCoins() { return coins; }

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
        buildStatsUI(); // Construimos la lista de stats al abrir
        updateCoinsLabel();
        updateRerollLabel();

        if (continueButton != null) {
            continueButton.setOnAction(e -> {
                if (onClose != null) onClose.run();
            });
        }
        if (rerollButton != null) {
            rerollButton.setOnAction(e -> handleReroll());
        }
    }

    // ================= SECCIÓN DE STATS (DERECHA) =================

    private void buildStatsUI() {
        if (statsContainer == null || statsService == null) return;
        statsContainer.getChildren().clear();

        // Lista de stats importantes estilo Brotato
        addStatRow("Max HP",        statsService.getMaxHealth(),        false, "Vida máxima total.");
        addStatRow("Regeneration",  statsService.getHpRegen(),          false, "Vida recuperada cada segundo.");
        addStatRow("Lifesteal",     statsService.getLifesteal(),        true,  "Probabilidad de curarse al dañar un enemigo.");
        addStatRow("Damage",        statsService.getProjectileDamage(), false, "Daño por disparo.");
        addStatRow("Attack Speed",  statsService.getFireRate(),         false, "Disparos por segundo.");
        addStatRow("Crit Chance",   statsService.getCritChance(),       true,  "Probabilidad de hacer doble daño.");
        addStatRow("Range",         statsService.getProjectileRange(),  false, "Distancia que viajan los disparos (tiempo de vida).");
        addStatRow("Speed",         statsService.getMoveSpeed(),        false, "Velocidad de movimiento.");
        addStatRow("Armor",         statsService.getArmor(),            false, "Reduce el daño recibido.");
        addStatRow("Dodge",         statsService.getDodge(),            true,  "Probabilidad de ignorar un golpe.");
        addStatRow("Luck",          statsService.getLuck(),             false, "Afecta la calidad de los drops y la tienda.");
        addStatRow("Harvesting",    statsService.getHarvesting(),       false, "Dinero y experiencia extra al final de la ronda.");
    }

    private void addStatRow(String name, double value, boolean isPercent, String description) {
        HBox row = new HBox();
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.setPadding(new javafx.geometry.Insets(2, 0, 2, 0));

        // Nombre de la stat
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-text-fill: #2c2c2c; -fx-font-weight: bold; -fx-font-size: 14;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Valor formateado
        String valStr = isPercent ? String.format("%.0f%%", value) : String.format("%.1f", value);
        Label valLbl = new Label(valStr);
        valLbl.setStyle("-fx-text-fill: #1a1a1a; -fx-font-family: 'Consolas'; -fx-font-size: 14; -fx-font-weight: bold;");

        // Color verde/rojo según si es positivo o negativo
        if (value > 0) valLbl.setStyle(valLbl.getStyle() + "-fx-text-fill: #006400;");
        else if (value < 0) valLbl.setStyle(valLbl.getStyle() + "-fx-text-fill: #8b0000;");

        row.getChildren().addAll(nameLbl, spacer, valLbl);

        // --- TOOLTIP MEJORADO ---
        if (description != null && !description.isEmpty()) {
            Tooltip tt = new Tooltip(description);
            // Hacer que aparezca casi al instante (sin esperar 1 segundo)
            tt.setShowDelay(Duration.millis(50));
            tt.setShowDuration(Duration.INDEFINITE);
            // Estilo específico para que no herede el CSS feo de Windows/Mac
            tt.getStyleClass().add("game-tooltip");
            Tooltip.install(row, tt);

            // Cursor de ayuda para indicar que hay info
            row.setCursor(javafx.scene.Cursor.HAND);
        }

        statsContainer.getChildren().add(row);
    }

    // ================= SECCIÓN DE TIENDA (IZQUIERDA) =================

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
        // Estilo de tarjeta oscura para cada ítem
        row.setStyle("-fx-background-color: #333333; -fx-background-radius: 8; -fx-padding: 10; -fx-border-color: #555; -fx-border-radius: 8;");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        ImageView iconView = new ImageView();
        iconView.setFitWidth(42.0);
        iconView.setFitHeight(42.0);
        iconView.setPreserveRatio(true);
        Image icon = loadIcon(offer.itemId);
        if (icon != null) {
            iconView.setImage(icon);
        }

        VBox infoBox = new VBox(2.0);
        Label nameLabel = new Label(getItemName(offer.itemId));
        nameLabel.getStyleClass().add("hud-label"); // Reusa estilo del HUD
        nameLabel.setStyle("-fx-font-size: 16; -fx-text-fill: #ffd54f;");

        Label descLabel = new Label(getItemDescription(offer.itemId));
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(220.0);
        descLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 11;");
        infoBox.getChildren().addAll(nameLabel, descLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox buyBox = new VBox(4);
        buyBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        Label priceLabel = new Label(offer.price + "¢");
        priceLabel.setStyle("-fx-text-fill: #ffdd55; -fx-font-weight: bold; -fx-font-size: 16;");

        Button buyButton = new Button("COMPRAR");
        buyButton.getStyleClass().add("menu-button"); // Reusa estilo de botón
        buyButton.setStyle("-fx-font-size: 12; -fx-padding: 4 12; -fx-background-radius: 6;");

        buyButton.setUserData(offer);
        buyButton.setOnAction(e -> handlePurchase(offer, buyButton));

        buyBox.getChildren().addAll(priceLabel, buyButton);

        row.getChildren().addAll(iconView, infoBox, spacer, buyBox);
        return row;
    }

    private void handlePurchase(ShopOffer offer, Button button) {
        if (offer == null || button == null) return;
        if (button.isDisable()) return;
        if (coins < offer.price) return;

        coins = Math.max(0, coins - offer.price);
        updateCoinsLabel();
        notifyCoinsChanged();

        if (statsService != null && offer.itemId != null) {
            statsService.grantItem(offer.itemId);
        }

        // 🔹 Refrescar la lista de stats de la derecha para ver el cambio inmediato
        buildStatsUI();

        if (onItemsChanged != null) {
            onItemsChanged.run();
        }

        button.setDisable(true);
        button.setText("VENDIDO");
        button.setStyle("-fx-background-color: #444; -fx-text-fill: #888;");
        button.setUserData(null);

        var parent = button.getParent().getParent(); // Subir al HBox principal
        if (parent instanceof HBox row) {
            row.setStyle("-fx-opacity: 0.4; -fx-background-color: #202020; -fx-background-radius: 8; -fx-padding: 10;");
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
            coinsLabel.setText("Coins: " + coins + "¢");
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
                // Buscamos el botón dentro de la estructura (HBox -> VBox -> Button)
                var lastChild = row.getChildren().get(row.getChildren().size() - 1);
                if (lastChild instanceof VBox buyBox) {
                    for(var b : buyBox.getChildren()) {
                        if (b instanceof Button btn) {
                            Object ud = btn.getUserData();
                            if (ud instanceof ShopOffer offer) {
                                btn.setDisable(coins < offer.price);
                            }
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
        int inc = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 3); // Reroll sube poco a poco
        rerollPrice = rerollPrice + inc;
        return inc;
    }

    private String getItemName(ItemId itemId) {
        ItemDefinition def = itemId == null ? null : ItemRegistry.getDefinition(itemId);
        return (def != null && def.getName() != null) ? def.getName() : (itemId != null ? itemId.name() : "Item");
    }

    private String getItemDescription(ItemId itemId) {
        ItemDefinition def = itemId == null ? null : ItemRegistry.getDefinition(itemId);
        return (def != null && def.getDescription() != null) ? def.getDescription() : "Un objeto misterioso.";
    }

    private Image loadIcon(ItemId itemId) {
        if (itemId == null) return null;
        try {
            String path = ItemRegistry.getIconPath(itemId);
            if (path == null) return null;
            String rel = path.startsWith("/") ? path.substring(1) : path;
            return AssetsManager.loadImage(rel);
        } catch (Exception ex) {
            return null;
        }
    }

    private void showRerollFloatText(int inc) {
        if (rerollButton == null || rerollButton.getScene() == null) return;

        Label floating = new Label("+" + inc + "¢");
        floating.setStyle("-fx-text-fill: #ff5555; -fx-font-size: 18px; -fx-font-weight: bold; -fx-effect: dropshadow(gaussian, black, 2, 1, 0, 0);");

        Scene scene = rerollButton.getScene();
        if (!(scene.getRoot() instanceof StackPane root)) return;

        root.getChildren().add(floating);

        var p = rerollButton.localToScene(rerollButton.getWidth()/2, 0);
        var localInRoot = root.sceneToLocal(p.getX(), p.getY());

        floating.setTranslateX(localInRoot.getX());
        floating.setTranslateY(localInRoot.getY() - 10);

        Timeline tl = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(floating.opacityProperty(), 1.0),
                new KeyValue(floating.translateYProperty(), floating.getTranslateY())
            ),
            new KeyFrame(Duration.millis(800),
                new KeyValue(floating.opacityProperty(), 0.0),
                new KeyValue(floating.translateYProperty(), floating.getTranslateY() - 40)
            )
        );

        tl.setOnFinished(e -> root.getChildren().remove(floating));
        tl.play();
    }
}
