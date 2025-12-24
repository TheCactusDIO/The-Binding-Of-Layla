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

/**
 * Controlador del overlay de tienda.
 *
 * <p>Responsabilidades principales:</p>
 * <ul>
 *   <li>Renderizar ofertas (items) y la compra de un corazón.</li>
 *   <li>Gestionar el reroll (rebarajar) y su coste creciente.</li>
 *   <li>Actualizar accesibilidad (habilitar/deshabilitar botones) según monedas y estado del jugador.</li>
 *   <li>Exponer callbacks (cambios de monedas, items, reroll, cierre).</li>
 * </ul>
 *
 * <p>Notas:</p>
 * <ul>
 *   <li>No cambia la lógica de balance: solo dispara callbacks y delega el grant del ítem a {@link StatsService}.</li>
 *   <li>El método {@link #initialize()} configura atajos de teclado (ESC y R) para el overlay.</li>
 * </ul>
 */
public final class ShopOverlayController {

    /**
     * Oferta de la tienda: un ítem con su precio y estado de venta.
     */
    public static final class ShopOffer {
        /** Ítem ofertado. */
        public final ItemId itemId;
        /** Precio de compra en monedas. */
        public final int price;
        /** Indica si ya fue vendido. */
        public boolean sold = false;

        /**
         * Crea una oferta.
         *
         * @param itemId ítem ofertado
         * @param price precio en monedas
         */
        public ShopOffer(ItemId itemId, int price) {
            this.itemId = itemId;
            this.price = price;
        }
    }

    private static final String HEART_ROW_TAG = "HEART_ROW";

    // Estilos reutilizados (evita repetir strings en varios sitios)
    private static final String STYLE_ROW =
            "-fx-background-color: #333333; -fx-background-radius: 8; -fx-padding: 10; -fx-border-color: #555; -fx-border-radius: 8;";
    private static final String STYLE_BUY_ENABLED =
            "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 12; -fx-padding: 4 12; -fx-background-radius: 6;";
    private static final String STYLE_BUY_DISABLED =
            "-fx-background-color: #555; -fx-text-fill: #aaa; -fx-font-size: 12; -fx-padding: 4 12; -fx-background-radius: 6;";
    private static final String STYLE_BUY_SOLD =
            "-fx-background-color: #444; -fx-text-fill: #888;";

    // =========================
    // FXML
    // =========================
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
    private Consumer<ShopOverlayController> onRerollRequested = c -> { };
    private Consumer<Integer> onRerollPriceChanged = i -> { };
    private Runnable onClose = ShopOverlayController::noop;

    // Corazón
    private int heartPrice = 0;
    private Consumer<ShopOverlayController> onHeartBuyRequest = c -> { };

    /**
     * Inicialización del controlador (llamado por JavaFX/FXML).
     *
     * <p>Configura atajos de teclado:</p>
     * <ul>
     *   <li>ESC: cerrar tienda</li>
     *   <li>R: rebarajar (reroll)</li>
     * </ul>
     */
    @FXML
    private void initialize() {
        if (root == null) return;

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

    // ---------------------------
    // Setters / callbacks
    // ---------------------------

    /**
     * Asigna el servicio de estadísticas usado para otorgar ítems y leer stats.
     *
     * @param stats servicio de stats (puede ser null)
     */
    public void setStatsService(StatsService stats) {
        this.statsService = stats;
    }

    /**
     * Asigna el jugador, necesario para bloquear la compra de corazón si la vida está llena.
     *
     * @param player jugador (puede ser null)
     */
    public void setPlayer(Player player) {
        this.player = player;
    }

    /**
     * Actualiza el número de monedas disponibles y refresca la UI.
     *
     * @param coins monedas actuales (se clamp a 0 como mínimo)
     */
    public void setCoins(int coins) {
        this.currentCoins = Math.max(0, coins);
        updateCoinsLabel();
        refreshAffordability();
    }

    /**
     * Devuelve las monedas actuales.
     *
     * @return monedas actuales
     */
    public int getCoins() {
        return currentCoins;
    }

    /**
     * Define el coste base del reroll y refresca la UI.
     *
     * @param price coste base (se clamp a 0 como mínimo)
     */
    public void setRerollBasePrice(int price) {
        this.rerollBasePrice = Math.max(0, price);
        updateRerollLabel();
        refreshAffordability();
    }

    /**
     * Sustituye las ofertas actuales por una nueva lista y reconstruye la UI si procede.
     *
     * @param newOffers lista de ofertas (puede ser null)
     */
    public void setOffers(List<ShopOffer> newOffers) {
        offers.clear();
        if (newOffers != null) offers.addAll(newOffers);
        if (offersContainer != null) buildOffersUI();
    }

    /**
     * Define el precio del corazón y refresca la accesibilidad.
     *
     * @param price precio (se clamp a 0 como mínimo)
     */
    public void setHeartPrice(int price) {
        this.heartPrice = Math.max(0, price);
        refreshAffordability();
    }

    /**
     * Callback para solicitar la compra del corazón.
     *
     * @param callback callback (si es null se usa un no-op)
     */
    public void setOnHeartBuyRequest(Consumer<ShopOverlayController> callback) {
        this.onHeartBuyRequest = (callback != null) ? callback : c -> { };
    }

    /**
     * Callback cuando cambian las monedas.
     *
     * @param callback callback (si es null se usa un no-op)
     */
    public void setOnCoinsChanged(IntConsumer callback) {
        this.onCoinsChanged = (callback != null) ? callback : ShopOverlayController::noopInt;
    }

    /**
     * Callback cuando cambian los ítems (por ejemplo, tras una compra).
     *
     * @param callback callback (si es null se usa un no-op)
     */
    public void setOnItemsChanged(Runnable callback) {
        this.onItemsChanged = (callback != null) ? callback : ShopOverlayController::noop;
    }

    /**
     * Callback para notificar que se ha solicitado un reroll.
     *
     * @param callback callback (si es null se usa un no-op)
     */
    public void setOnRerollRequested(Consumer<ShopOverlayController> callback) {
        this.onRerollRequested = (callback != null) ? callback : c -> { };
    }

    /**
     * Callback cuando cambia el precio del reroll.
     *
     * @param callback callback (si es null se usa un no-op)
     */
    public void setOnRerollPriceChanged(Consumer<Integer> callback) {
        this.onRerollPriceChanged = (callback != null) ? callback : i -> { };
    }

    /**
     * Callback al cerrar el overlay.
     *
     * @param callback callback (si es null se usa un no-op)
     */
    public void setOnClose(Runnable callback) {
        this.onClose = (callback != null) ? callback : ShopOverlayController::noop;
    }

    // ---------------------------
    // Lifecycle
    // ---------------------------

    /**
     * Llamar cuando se va a mostrar el overlay.
     *
     * <p>Reconstruye listas y actualiza textos/botones en base al estado actual.</p>
     */
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
        // rerollButton: su texto real lo pone updateRerollLabel()
    }

    // ---------------------------
    // UI updates
    // ---------------------------

    /**
     * Refresca el label de monedas.
     */
    private void updateCoinsLabel() {
        if (coinsLabel != null) coinsLabel.setText("Monedas: " + currentCoins + "¢");
    }

    /**
     * Refresca el texto del botón de reroll con el coste actual.
     */
    private void updateRerollLabel() {
        if (rerollButton != null) rerollButton.setText("Rebarajar (" + rerollBasePrice + "¢)");
    }

    /**
     * Reconstruye la lista de ofertas (corazón + ítems) en el contenedor.
     */
    private void buildOffersUI() {
        if (offersContainer == null) return;

        offersContainer.getChildren().clear();
        offersContainer.getChildren().add(createHeartRow());

        for (ShopOffer offer : offers) {
            offersContainer.getChildren().add(createOfferRow(offer));
        }

        refreshAffordability();
    }

    /**
     * Crea la fila UI correspondiente a la compra de un corazón.
     *
     * @return fila (HBox) con icono, descripción y botón de compra
     */
    private HBox createHeartRow() {
        HBox row = new HBox(10.0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(STYLE_ROW);

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
        buyButton.setStyle(STYLE_BUY_ENABLED);
        buyButton.setOnAction(e -> onHeartBuyRequest.accept(this));

        buyBox.getChildren().addAll(priceLabel, buyButton);

        row.getChildren().addAll(iconView, infoBox, spacer, buyBox);
        row.setUserData(HEART_ROW_TAG);

        return row;
    }

    /**
     * Crea una fila UI para una oferta de ítem.
     *
     * @param offer oferta a renderizar
     * @return fila (HBox) con icono, descripción, precio y botón de compra
     */
    private HBox createOfferRow(ShopOffer offer) {
        HBox row = new HBox(10.0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(STYLE_ROW);

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
            buyButton.setStyle(STYLE_BUY_SOLD);
        } else {
            buyButton.setStyle(STYLE_BUY_ENABLED);
        }

        buyButton.setUserData(offer);
        buyButton.setOnAction(e -> handlePurchase(offer, buyButton));

        buyBox.getChildren().addAll(priceLabel, buyButton);

        row.getChildren().addAll(iconView, infoBox, spacer, buyBox);
        return row;
    }

    /**
     * Ejecuta la compra de un ítem si se cumplen las condiciones.
     *
     * <p>Comportamiento:</p>
     * <ul>
     *   <li>Resta monedas.</li>
     *   <li>Notifica el cambio de monedas.</li>
     *   <li>Otorga el ítem vía {@link StatsService#grantItem(ItemId)} si existe {@link #statsService}.</li>
     *   <li>Marca la oferta como vendida y deshabilita el botón.</li>
     *   <li>Reconstruye stats y refresca accesibilidad.</li>
     * </ul>
     *
     * @param offer oferta a comprar
     * @param button botón asociado (se actualiza a "VENDIDO")
     */
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
        button.setStyle(STYLE_BUY_SOLD);

        onItemsChanged.run();

        buildStatsUI();
        refreshAffordability();
    }

    /**
     * Acción de reroll: cobra el coste actual, incrementa el coste base y notifica callbacks.
     */
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

    /**
     * Acción de cierre del overlay.
     */
    private void onCloseAction() {
        onClose.run();
    }

    /**
     * Actualiza el estado de los botones de compra (habilitado/deshabilitado) según:
     * <ul>
     *   <li>Monedas actuales</li>
     *   <li>Oferta vendida</li>
     *   <li>Vida del jugador (para el corazón)</li>
     * </ul>
     *
     * <p>Además, deshabilita el botón de reroll si no hay monedas suficientes.</p>
     */
    private void refreshAffordability() {
        if (offersContainer == null) return;

        for (Node node : offersContainer.getChildren()) {
            if (!(node instanceof HBox row)) continue;

            // -------- Corazón --------
            if (HEART_ROW_TAG.equals(row.getUserData())) {
                VBox buyBox = getBuyBox(row);
                if (buyBox == null) continue;

                Label priceLbl = getPriceLabel(buyBox);
                Button btn = getBuyButton(buyBox);
                if (priceLbl == null || btn == null) continue;

                priceLbl.setText(heartPrice + "¢");

                boolean canAfford = currentCoins >= heartPrice;
                boolean healthFull = (player != null && player.getHealth() >= player.getMaxHealth());

                if (healthFull) {
                    btn.setText("LLENO");
                    btn.setDisable(true);
                    btn.setStyle(STYLE_BUY_DISABLED);
                } else if (!canAfford) {
                    btn.setText("COMPRAR");
                    btn.setDisable(true);
                    btn.setStyle(STYLE_BUY_DISABLED);
                } else {
                    btn.setText("COMPRAR");
                    btn.setDisable(false);
                    btn.setStyle(STYLE_BUY_ENABLED);
                }
                continue;
            }

            // -------- Ofertas normales --------
            VBox buyBox = getBuyBox(row);
            if (buyBox == null) continue;

            for (Node n : buyBox.getChildren()) {
                if (!(n instanceof Button btn)) continue;
                if ("VENDIDO".equals(btn.getText())) continue;

                Object ud = btn.getUserData();
                if (!(ud instanceof ShopOffer offer)) continue;

                if (currentCoins < offer.price) {
                    btn.setDisable(true);
                    btn.setStyle(STYLE_BUY_DISABLED);
                } else {
                    btn.setDisable(false);
                    btn.setStyle(STYLE_BUY_ENABLED);
                }
            }
        }

        if (rerollButton != null) {
            rerollButton.setDisable(currentCoins < rerollBasePrice);
        }
    }

    /**
     * Construye el panel de stats (valores actuales del jugador) en base a {@link StatsService}.
     */
    private void buildStatsUI() {
        if (statsContainer == null || statsService == null) return;

        statsContainer.getChildren().clear();

        addStatRow("Vida máxima", statsService.getMaxHealth(), false, "Vida máxima total.");
        addStatRow("Daño",        statsService.getProjectileDamage(), false, "Daño por disparo.");
        addStatRow("Cadencia",    statsService.getFireRate(), false, "Disparos por segundo.");
        addStatRow("Velocidad",   statsService.getMoveSpeed(), false, "Velocidad de movimiento.");
    }

    /**
     * Añade una fila de estadística al panel.
     *
     * @param name nombre mostrado
     * @param value valor numérico
     * @param isPercent si true se formatea como porcentaje
     * @param desc tooltip (si es null o vacío, no se instala)
     */
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
            Tooltip.install(row, new Tooltip(desc));
        }

        row.getChildren().addAll(nameLbl, spacer, valLbl);
        statsContainer.getChildren().add(row);
    }

    /**
     * Muestra un texto flotante cerca del botón de reroll indicando el coste pagado.
     *
     * @param costPaid coste pagado en monedas
     */
    private void showRerollFloatText(int costPaid) {
        if (rerollButton == null) return;

        Scene scene = rerollButton.getScene();
        if (scene == null) return;
        if (!(scene.getRoot() instanceof StackPane spRoot)) return;

        Label floating = new Label("- " + costPaid + "¢");
        floating.setStyle("-fx-text-fill: #ff5555; -fx-font-size: 16px; -fx-font-weight: bold; "
                + "-fx-effect: dropshadow(gaussian, black, 2, 1, 0, 0);");

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

    /**
     * Extrae el VBox de compra (último hijo) de una fila de oferta.
     *
     * @param row fila HBox
     * @return VBox de compra o null si la estructura no coincide
     */
    private static VBox getBuyBox(HBox row) {
        if (row == null || row.getChildren().isEmpty()) return null;

        Node last = row.getChildren().get(row.getChildren().size() - 1);
        return (last instanceof VBox v) ? v : null;
    }

    /**
     * Obtiene la etiqueta de precio del VBox de compra.
     *
     * @param buyBox contenedor (VBox)
     * @return Label de precio o null si no existe
     */
    private static Label getPriceLabel(VBox buyBox) {
        if (buyBox == null || buyBox.getChildren().isEmpty()) return null;

        Node n = buyBox.getChildren().get(0);
        return (n instanceof Label l) ? l : null;
    }

    /**
     * Obtiene el botón de compra del VBox de compra.
     *
     * @param buyBox contenedor (VBox)
     * @return Button de compra o null si no existe
     */
    private static Button getBuyButton(VBox buyBox) {
        if (buyBox == null || buyBox.getChildren().size() < 2) return null;

        Node n = buyBox.getChildren().get(1);
        return (n instanceof Button b) ? b : null;
    }

    // Handlers FXML
    @FXML private void onReroll() { onRerollAction(); }
    @FXML private void onClose()  { onCloseAction(); }

    private static void noop() { }
    private static void noopInt(int v) { }
}
