package com.layla.ui;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.layla.items.ItemId;
import com.layla.items.ItemRegistry;
import com.layla.services.StatsService;

import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

/** Simple HUD column that shows owned item icons (stackable). */
public final class ItemHudView extends VBox {

    private final StatsService statsService;
    private final Map<ItemId, ImageView> iconCache = new EnumMap<>(ItemId.class);

    public ItemHudView(StatsService statsService) {
        this.statsService = Objects.requireNonNull(statsService, "statsService");

        setSpacing(8);
        setFillWidth(false);
        setAlignment(Pos.TOP_RIGHT);
        getStyleClass().add("item-hud");
    }

    /** Rebuilds the icon list based on current owned items (with stacks). */
    public void refresh() {
        getChildren().clear();

        List<ItemId> items = statsService.getOwnedItemsStacked();
        System.out.println("[ItemHudView] refresh, items=" + items);

        for (ItemId itemId : items) {
            ImageView iv = iconCache.computeIfAbsent(itemId, this::createIconView);
            if (iv != null) {
                // IMPORTANT: for stacks we need a *new* node each time
                ImageView copy = new ImageView(iv.getImage());
                copy.setFitWidth(iv.getFitWidth());
                copy.setFitHeight(iv.getFitHeight());
                copy.setPreserveRatio(true);
                getChildren().add(copy);
            }
        }

        System.out.println("[ItemHudView] children after refresh=" + getChildren().size());
    }

    private ImageView createIconView(ItemId itemId) {
        String path = ItemRegistry.getIconPath(itemId);
        if (path == null) {
            System.err.println("[ItemHudView] No icon path for item " + itemId);
            return null;
        }
        var stream = getClass().getResourceAsStream(path);
        if (stream == null) {
            System.err.println("[ItemHudView] Could not load icon: " + path);
            return null;
        }

        Image image = new Image(stream, 32, 32, true, true);
        ImageView iv = new ImageView(image);
        iv.setFitWidth(32);
        iv.setFitHeight(32);
        iv.setPreserveRatio(true);
        return iv;
    }
}
