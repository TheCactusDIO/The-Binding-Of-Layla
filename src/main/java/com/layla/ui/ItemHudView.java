package com.layla.ui;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.layla.items.ItemId;
import com.layla.items.ItemRegistry;
import com.layla.services.StatsService;

import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;

/**
 * HUD lateral de ítems:
 * - 3 iconos por fila (horizontal), luego baja y hace otra fila.
 * - Sólo muestra los últimos N ítems (los más recientes).
 */
public final class ItemHudView extends FlowPane {

    private static final int COLUMNS   = 3;   // iconos por fila
    private static final int MAX_ROWS  = 8;   // filas visibles (ajusta si quieres)
    private static final int ICON_SIZE = 24;  // tamaño de icono
    private static final int GAP       = 4;   // separación entre iconos

    private final StatsService statsService;
    private final Map<ItemId, Image> imageCache = new EnumMap<>(ItemId.class);

    public ItemHudView(StatsService statsService) {
        this.statsService = Objects.requireNonNull(statsService, "statsService");

        setOrientation(Orientation.HORIZONTAL);   // rellena en horizontal, luego baja
        setHgap(GAP);
        setVgap(GAP);
        setAlignment(Pos.TOP_RIGHT);

        // Ancho fijo para EXACTAMENTE 3 iconos por fila (aprox)
        double cell   = ICON_SIZE + GAP;
        double width  = COLUMNS * cell - GAP; // último sin gap a la derecha

        setPrefWrapLength(width);
        setPrefWidth(width);
        setMinWidth(width);
        setMaxWidth(width);   // 👈 CLAVE: así no se ensancha y se ve obligado a hacer wrap

        setMouseTransparent(true);
        setPickOnBounds(false);

        getStyleClass().add("item-hud");
    }

    /**
     * Reconstruye el HUD con los ítems actuales.
     * Si hay más ítems que espacio, solo muestra los últimos (más recientes).
     */
    public void refresh() {
        getChildren().clear();

        List<ItemId> allItems = statsService.getOwnedItemsStacked();
        System.out.println("[ItemHudView] refresh, total items=" + allItems.size());
        if (allItems.isEmpty()) return;

        int maxIcons = COLUMNS * MAX_ROWS;
        int total    = allItems.size();
        int start    = Math.max(0, total - maxIcons); // nos quedamos con los últimos

        for (int i = start; i < total; i++) {
            ItemId itemId = allItems.get(i);
            Image image = getOrLoadImage(itemId);
            if (image == null) continue;

            ImageView iv = new ImageView(image);
            iv.setFitWidth(ICON_SIZE);
            iv.setFitHeight(ICON_SIZE);
            iv.setPreserveRatio(true);

            getChildren().add(iv);
        }

        System.out.println("[ItemHudView] children after refresh=" + getChildren().size());
    }

    private Image getOrLoadImage(ItemId itemId) {
        Image cached = imageCache.get(itemId);
        if (cached != null) return cached;

        String path = ItemRegistry.getIconPath(itemId);
        if (path == null) {
            System.err.println("[ItemHudView] No icon path for item " + itemId);
            return null;
        }

        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                System.err.println("[ItemHudView] Could not load icon resource " + path);
                return null;
            }
            Image img = new Image(in);
            imageCache.put(itemId, img);
            return img;
        } catch (Exception ex) {
            System.err.println("[ItemHudView] Error loading icon " + path + ": " + ex.getMessage());
            return null;
        }
    }
}
