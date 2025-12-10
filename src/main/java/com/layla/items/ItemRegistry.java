package com.layla.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.layla.model.PlayerStatId;
import com.layla.model.StatModifier;
import com.layla.services.AchievementService;

/** Static registry of every passive item definition. */
public final class ItemRegistry {

    private static final Map<ItemId, ItemDefinition> ITEMS = new EnumMap<>(ItemId.class);
    private static final Map<ItemId, String> ICON_PATHS = new EnumMap<>(ItemId.class);

    static {
        // --- NEW ITEMS ---
        register(new ItemDefinition(
            ItemId.TRIPLE_SHOT,
            "The Inner Eye",
            "Triple shot but slower fire rate.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_COUNT, 2.0),
                StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 0.7)
            )
        ));
        ICON_PATHS.put(ItemId.TRIPLE_SHOT, "assets/images/tripleshot.png");

        register(new ItemDefinition(
            ItemId.QUAD_SHOT,
            "Mutant Spider",
            "Quad shot, much slower.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_COUNT, 3.0),
                StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 0.55)
            )
        ));
        // IMAGEN CUSTOM: Quadshot
        ICON_PATHS.put(ItemId.QUAD_SHOT, "assets/images/quadshot.png");

        register(new ItemDefinition(
            ItemId.CUPIDS_ARROW,
            "Cupid's Arrow",
            "Piercing shots.",
            ItemPoolType.SHOP,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_PIERCE, 1.0)
            )
        ));
        // IMAGEN CUSTOM: Piercing
        ICON_PATHS.put(ItemId.CUPIDS_ARROW, "assets/images/piercing.png");

        register(new ItemDefinition(
            ItemId.RUBBER_CEMENT,
            "Rubber Cement",
            "Bouncing tears.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_BOUNCE, 1.0)
            )
        ));
        // IMAGEN CUSTOM: Bouncing
        ICON_PATHS.put(ItemId.RUBBER_CEMENT, "assets/images/bouncing.png");

        // NUEVO: Homing
        register(new ItemDefinition(
            ItemId.SPOON_BENDER,
            "Spoon Bender",
            "Homing shots.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_HOMING, 1.0)
            )
        ));
        // IMAGEN CUSTOM: Homing
        ICON_PATHS.put(ItemId.SPOON_BENDER, "assets/images/homing.png");

        // --- EXISTING REGISTRATION ---

        register(new ItemDefinition(
            ItemId.PEASHOOTER_AMMO,
            "Peashooter Ammo",
            "Tiny bullets, faster shots. (Reward: First Blood)",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_SPEED, 50.0),
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.1)
            ),
            "ACH_FIRST_KILL"
        ));
        ICON_PATHS.put(ItemId.PEASHOOTER_AMMO, "assets/images/item1.png");

        register(new ItemDefinition(
            ItemId.GLASS_CANNON,
            "Glass Cannon",
            "Massive damage boost but fragile. (Reward: Survivor)",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.of(PlayerStatId.PROJECTILE_DAMAGE, 1.0, 1.75),
                StatModifier.additive(PlayerStatId.MAX_HEALTH, -2.0)
            ),
            "ACH_SURVIVOR"
        ));
        ICON_PATHS.put(ItemId.GLASS_CANNON, "assets/images/item2.png");

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
        ICON_PATHS.put(ItemId.TEARS_UP, "assets/images/item3.png");

        register(new ItemDefinition(
            ItemId.SNIPER_MODULE,
            "Sniper Module",
            "+40% range, +0.5 crit damage. (Reward: Novice Hunter)",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_RANGE, 1.40),
                StatModifier.additive(PlayerStatId.CRIT_DAMAGE, 0.5)
            ),
            "ACH_NOVICE_HUNTER"
        ));
        ICON_PATHS.put(ItemId.SNIPER_MODULE, "assets/images/item4.png");

        register(new ItemDefinition(
            ItemId.SHOT_SPEED_UP,
            "Shot Speed Up",
            "Faster projectiles.",
            ItemPoolType.SHOP,
            List.of(
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_SPEED, 1.25)
            )
        ));
        ICON_PATHS.put(ItemId.SHOT_SPEED_UP, "assets/images/item5.png");

        register(new ItemDefinition(
            ItemId.BANDAGE,
            "Bandage",
            "+2 HP regen.",
            ItemPoolType.TREASURE,
            List.of(StatModifier.additive(PlayerStatId.HP_REGEN, 2.0))
        ));

        register(new ItemDefinition(
            ItemId.SMALL_CHESTPLATE,
            "Small Chestplate",
            "+2 armor, slightly slower.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.ARMOR, 2.0),
                StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 0.97)
            )
        ));

        register(new ItemDefinition(
            ItemId.GOLDEN_WALLET,
            "Golden Wallet",
            "+10 harvesting, +5 luck. (Reward: Pocket Money)",
            ItemPoolType.SHOP,
            List.of(
                StatModifier.additive(PlayerStatId.HARVESTING, 10.0),
                StatModifier.additive(PlayerStatId.LUCK, 5.0)
            ),
            "ACH_POCKET_MONEY"
        ));

        register(new ItemDefinition(
            ItemId.COUPON,
            "Coupon",
            "10% shop discount. (Reward: Big Spender)",
            ItemPoolType.SHOP,
            List.of(StatModifier.additive(PlayerStatId.SHOP_DISCOUNT, 0.10)),
            "ACH_BIG_SPENDER"
        ));

        register(new ItemDefinition(
            ItemId.LUCKY_CHARM,
            "Lucky Charm",
            "+8 luck. (Reward: Boss Slayer)",
            ItemPoolType.TREASURE,
            List.of(StatModifier.additive(PlayerStatId.LUCK, 8.0)),
            "ACH_BOSS_SLAYER"
        ));

        register(new ItemDefinition(
            ItemId.TITAN_BLOOD,
            "Titan Blood",
            "+40 max health, +10 armor. (Reward: The End)",
            ItemPoolType.BOSS,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 40.0),
                StatModifier.additive(PlayerStatId.ARMOR, 10.0),
                StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 0.80)
            ),
            "ACH_THE_END"
        ));

        register(new ItemDefinition(
            ItemId.MEDKIT,
            "Medkit",
            "+4 max health, +4 HP regen. (Reward: Try Again)",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 4.0),
                StatModifier.additive(PlayerStatId.HP_REGEN, 4.0)
            ),
            "ACH_TRY_AGAIN"
        ));

        register(new ItemDefinition(
            ItemId.SHOP_SCANNER,
            "Shop Scanner",
            "Better deals and better offers. (Reward: Gear Up)",
            ItemPoolType.SHOP,
            List.of(
                StatModifier.additive(PlayerStatId.SHOP_DISCOUNT, 0.15),
                StatModifier.additive(PlayerStatId.LUCK, 6.0)
            ),
            "ACH_GEAR_UP"
        ));

        register(new ItemDefinition(ItemId.RUNNING_SHOES, "Running Shoes", "+10% move speed.", ItemPoolType.TREASURE, List.of(StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 1.10))));
        register(new ItemDefinition(ItemId.PROTEIN_BAR, "Protein Bar", "+5 max health.", ItemPoolType.TREASURE, List.of(StatModifier.additive(PlayerStatId.MAX_HEALTH, 5.0))));
        register(new ItemDefinition(ItemId.MAGNET, "Magnet", "+50 pickup range.", ItemPoolType.SHOP, List.of(StatModifier.additive(PlayerStatId.PICKUP_RANGE, 50.0))));
        register(new ItemDefinition(ItemId.SWIFT_BOOTS, "Swift Boots", "+Move speed.", ItemPoolType.TREASURE, List.of(StatModifier.additive(PlayerStatId.MOVE_SPEED, 40.0))));
        register(new ItemDefinition(ItemId.RANGE_UP, "Range Up", "Projectiles live longer.", ItemPoolType.TREASURE, List.of(StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 0.6))));
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
        return ICON_PATHS.getOrDefault(id, "assets/images/item1.png");
    }

    public static List<ItemDefinition> getUnlockedByPool(ItemPoolType poolType, AchievementService achievements) {
        if (poolType == null) return List.of();

        List<ItemDefinition> list = new ArrayList<>();

        for (ItemDefinition def : ITEMS.values()) {
            if (def.getPoolType() != poolType) continue;

            if (def.isUnlockedByDefault()) {
                list.add(def);
            } else if (achievements != null && achievements.isUnlocked(def.getRequiredAchievementId())) {
                list.add(def);
            }
        }

        if (list.isEmpty()) {
            System.err.println("[ItemRegistry] Warning: Pool " + poolType + " empty after filtering! Returning defaults.");
            for (ItemDefinition def : ITEMS.values()) {
                if (def.getPoolType() == poolType && def.isUnlockedByDefault()) {
                    list.add(def);
                }
            }
        }

        return Collections.unmodifiableList(list);
    }

    public static List<ItemDefinition> allDefinitions() {
        return Collections.unmodifiableList(new ArrayList<>(ITEMS.values()));
    }
}
