package com.layla.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.layla.model.PlayerStatId;
import com.layla.model.StatModifier;

/** Static registry of every passive item definition. */
public final class ItemRegistry {

    private static final Map<ItemId, ItemDefinition> ITEMS = new EnumMap<>(ItemId.class);
    private static final Map<ItemId, String> ICON_PATHS = new EnumMap<>(ItemId.class);

    static {
        register(new ItemDefinition(
            ItemId.SWIFT_BOOTS,
            "Swift Boots",
            "+Move speed so dodging feels smoother.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MOVE_SPEED, 40.0),
                StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 1.15)
            )
        ));
        ICON_PATHS.put(ItemId.SWIFT_BOOTS, "/assets/images/item1.png");

        register(new ItemDefinition(
            ItemId.GLASS_CANNON,
            "Glass Cannon",
            "Massive damage boost but fragile.",
            ItemPoolType.BOSS,
            List.of(
                StatModifier.of(PlayerStatId.PROJECTILE_DAMAGE, 1.0, 1.75),
                StatModifier.additive(PlayerStatId.MAX_HEALTH, -2.0)
            )
        ));
        ICON_PATHS.put(ItemId.GLASS_CANNON, "/assets/images/item2.png");

        register(new ItemDefinition(
            ItemId.TEARS_UP,
            "Tears Up",
            "Fires faster.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.FIRE_RATE, 0.8),
                StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 1.20)
            )
        ));
        ICON_PATHS.put(ItemId.TEARS_UP, "/assets/images/item3.png");

        register(new ItemDefinition(
            ItemId.RANGE_UP,
            "Range Up",
            "Projectiles live longer.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 0.6)
            )
        ));
        ICON_PATHS.put(ItemId.RANGE_UP, "/assets/images/item4.png");

        register(new ItemDefinition(
            ItemId.SHOT_SPEED_UP,
            "Shot Speed Up",
            "Faster projectiles.",
            ItemPoolType.SHOP,
            List.of(
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_SPEED, 1.25)
            )
        ));
        ICON_PATHS.put(ItemId.SHOT_SPEED_UP, "/assets/images/item5.png");
    }

    private ItemRegistry() {}

    private static void register(ItemDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        ITEMS.put(definition.getId(), definition);
    }

    public static ItemDefinition getDefinition(ItemId id) {
        if (id == null) return null;
        return ITEMS.get(id);
    }

    public static String getIconPath(ItemId id) {
        if (id == null) return null;
        return ICON_PATHS.get(id);
    }

    public static List<ItemDefinition> getByPool(ItemPoolType poolType) {
        if (poolType == null) return List.of();
        List<ItemDefinition> list = new ArrayList<>();
        for (ItemDefinition definition : ITEMS.values()) {
            if (definition.getPoolType() == poolType) {
                list.add(definition);
            }
        }
        return Collections.unmodifiableList(list);
    }

    public static List<ItemDefinition> allDefinitions() {
        return Collections.unmodifiableList(new ArrayList<>(ITEMS.values()));
    }
}
