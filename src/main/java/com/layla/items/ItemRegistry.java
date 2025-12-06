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

        // COMMON
        register(new ItemDefinition(
            ItemId.BANDAGE,
            "Bandage",
            "+2 HP regen.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.HP_REGEN, 2.0)
            )
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
            ItemId.RUNNING_SHOES,
            "Running Shoes",
            "+10% move speed.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 1.10)
            )
        ));

        register(new ItemDefinition(
            ItemId.PROTEIN_BAR,
            "Protein Bar",
            "+5 max health.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 5.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.DUCT_TAPE,
            "Duct Tape",
            "Patch yourself up: small regen and lifesteal.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.HP_REGEN, 0.5),
                StatModifier.additive(PlayerStatId.LIFESTEAL, 2.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.LIGHT_BOOTS,
            "Light Boots",
            "Faster feet and a bit of dodge.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MOVE_SPEED, 25.0),
                StatModifier.additive(PlayerStatId.DODGE, 4.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.ENERGY_DRINK,
            "Energy Drink",
            "+12% fire rate, +5% move speed.",
            ItemPoolType.SHOP,
            List.of(
                StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 1.12),
                StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 1.05)
            )
        ));

        register(new ItemDefinition(
            ItemId.BROKEN_BULLET,
            "Broken Bullet",
            "+0.25 damage, -10% range.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.25),
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_RANGE, 0.9)
            )
        ));

        register(new ItemDefinition(
            ItemId.SHARP_ROCK,
            "Sharp Rock",
            "A bit more damage and knockback.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.2),
                StatModifier.additive(PlayerStatId.KNOCKBACK, 10.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.PRACTICE_TARGET,
            "Practice Target",
            "+5% crit chance, +0.1 crit damage.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.CRIT_CHANCE, 5.0),
                StatModifier.additive(PlayerStatId.CRIT_DAMAGE, 0.1)
            )
        ));

        register(new ItemDefinition(
            ItemId.METAL_SCRAP,
            "Metal Scrap",
            "+1 armor, +2 harvesting.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.ARMOR, 1.0),
                StatModifier.additive(PlayerStatId.HARVESTING, 2.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.RUSTY_SPRING,
            "Rusty Spring",
            "+0.25 fire rate, -2% move speed.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.FIRE_RATE, 0.25),
                StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 0.98),
                StatModifier.additive(PlayerStatId.KNOCKBACK, 5.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.PEASHOOTER_AMMO,
            "Peashooter Ammo",
            "Tiny bullets, faster shots.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_SPEED, 50.0),
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.1)
            )
        ));

        register(new ItemDefinition(
            ItemId.HARDENED_TIP,
            "Hardened Tip",
            "+1 pierce, -5% fire rate.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_PIERCE, 1.0),
                StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 0.95)
            )
        ));

        register(new ItemDefinition(
            ItemId.PIGGY_BANK,
            "Piggy Bank",
            "+5 harvesting.",
            ItemPoolType.SHOP,
            List.of(
                StatModifier.additive(PlayerStatId.HARVESTING, 5.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.LUCKY_CHARM,
            "Lucky Charm",
            "+8 luck.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.LUCK, 8.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.MAGNET,
            "Magnet",
            "+50 pickup range.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PICKUP_RANGE, 50.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.COUPON,
            "Coupon",
            "10% shop discount.",
            ItemPoolType.SHOP,
            List.of(
                StatModifier.additive(PlayerStatId.SHOP_DISCOUNT, 0.10)
            )
        ));

        register(new ItemDefinition(
            ItemId.COIN_BAG,
            "Coin Bag",
            "More coins, easier pickups.",
            ItemPoolType.SHOP,
            List.of(
                StatModifier.additive(PlayerStatId.HARVESTING, 3.0),
                StatModifier.additive(PlayerStatId.PICKUP_RANGE, 15.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.SCRAP_METAL,
            "Scrap Metal",
            "+1 armor, +5 harvesting, -5% move speed.",
            ItemPoolType.SHOP,
            List.of(
                StatModifier.additive(PlayerStatId.ARMOR, 1.0),
                StatModifier.additive(PlayerStatId.HARVESTING, 5.0),
                StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 0.95)
            )
        ));

        // UNCOMMON
        register(new ItemDefinition(
            ItemId.IRON_PLATE,
            "Iron Plate",
            "+3 armor.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.ARMOR, 3.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.MEDKIT,
            "Medkit",
            "+4 max health, +4 HP regen.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 4.0),
                StatModifier.additive(PlayerStatId.HP_REGEN, 4.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.REINFORCED_BOOTS,
            "Reinforced Boots",
            "Sturdy steps: speed, armor and knockback.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MOVE_SPEED, 15.0),
                StatModifier.additive(PlayerStatId.ARMOR, 1.0),
                StatModifier.additive(PlayerStatId.KNOCKBACK, 10.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.NANO_SHIELD,
            "Nano Shield",
            "+8% dodge, +1 armor.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.DODGE, 8.0),
                StatModifier.additive(PlayerStatId.ARMOR, 1.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.MEGA_HEART,
            "Mega Heart",
            "+12 max health.",
            ItemPoolType.BOSS,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 12.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.EXPLOSIVE_ROUNDS,
            "Explosive Rounds",
            "Heavier hits and huge knockback.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.3),
                StatModifier.additive(PlayerStatId.KNOCKBACK, 40.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.PRECISION_SCOPE,
            "Precision Scope",
            "+25% range, +5% crit chance.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_RANGE, 1.25),
                StatModifier.additive(PlayerStatId.CRIT_CHANCE, 5.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.SPIKED_GLOVES,
            "Spiked Gloves",
            "Life on hit and extra shove.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.LIFESTEAL, 4.0),
                StatModifier.additive(PlayerStatId.KNOCKBACK, 20.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.DUAL_SPRINGS,
            "Dual Springs",
            "+15% fire rate and a bit of shove.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 1.15),
                StatModifier.additive(PlayerStatId.KNOCKBACK, 5.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.BOUNCING_GEL,
            "Bouncing Gel",
            "+1 projectile bounce.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_BOUNCE, 1.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.TEFLON_AMMO,
            "Teflon Ammo",
            "+1 pierce and faster shots.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_PIERCE, 1.0),
                StatModifier.additive(PlayerStatId.PROJECTILE_SPEED, 30.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.CRITICAL_EYE,
            "Critical Eye",
            "+10% crit chance, +0.25 crit damage.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.CRIT_CHANCE, 10.0),
                StatModifier.additive(PlayerStatId.CRIT_DAMAGE, 0.25)
            )
        ));

        register(new ItemDefinition(
            ItemId.GOLDEN_WALLET,
            "Golden Wallet",
            "+10 harvesting, +5 luck.",
            ItemPoolType.SHOP,
            List.of(
                StatModifier.additive(PlayerStatId.HARVESTING, 10.0),
                StatModifier.additive(PlayerStatId.LUCK, 5.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.LUCKY_DICE,
            "Lucky Dice",
            "+15 luck, +3% crit chance.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.LUCK, 15.0),
                StatModifier.additive(PlayerStatId.CRIT_CHANCE, 3.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.BIG_MAGNET,
            "Big Magnet",
            "Huge pickup range, slightly slower.",
            ItemPoolType.SHOP,
            List.of(
                StatModifier.additive(PlayerStatId.PICKUP_RANGE, 120.0),
                StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 0.97)
            )
        ));

        register(new ItemDefinition(
            ItemId.VOUCHER,
            "Voucher",
            "20% shop discount and a bit more income.",
            ItemPoolType.SHOP,
            List.of(
                StatModifier.additive(PlayerStatId.SHOP_DISCOUNT, 0.20),
                StatModifier.additive(PlayerStatId.HARVESTING, 2.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.LUCKY_COIN,
            "Lucky Coin",
            "+12 luck, +2 harvesting.",
            ItemPoolType.SHOP,
            List.of(
                StatModifier.additive(PlayerStatId.LUCK, 12.0),
                StatModifier.additive(PlayerStatId.HARVESTING, 2.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.SHOP_SCANNER,
            "Shop Scanner",
            "Better deals and better offers.",
            ItemPoolType.SHOP,
            List.of(
                StatModifier.additive(PlayerStatId.SHOP_DISCOUNT, 0.15),
                StatModifier.additive(PlayerStatId.LUCK, 6.0)
            )
        ));

        // RARE
        register(new ItemDefinition(
            ItemId.REACTOR_CORE,
            "Reactor Core",
            "+25 max health, -10% move speed.",
            ItemPoolType.BOSS,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 25.0),
                StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 0.90)
            )
        ));

        register(new ItemDefinition(
            ItemId.ADRENALINE_PUMP,
            "Adrenaline Pump",
            "Faster fire rate and movement, plus regen.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 1.20),
                StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 1.05),
                StatModifier.additive(PlayerStatId.HP_REGEN, 1.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.BIO_STEEL_SKIN,
            "Bio-Steel Skin",
            "+5 armor, +1.5 HP regen.",
            ItemPoolType.BOSS,
            List.of(
                StatModifier.additive(PlayerStatId.ARMOR, 5.0),
                StatModifier.additive(PlayerStatId.HP_REGEN, 1.5)
            )
        ));

        register(new ItemDefinition(
            ItemId.GENETIC_UPGRADE,
            "Genetic Upgrade",
            "Sustain and a touch of max health.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.LIFESTEAL, 4.0),
                StatModifier.additive(PlayerStatId.HP_REGEN, 2.0),
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 3.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.SNIPER_MODULE,
            "Sniper Module",
            "+40% range, +0.5 crit damage.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_RANGE, 1.40),
                StatModifier.additive(PlayerStatId.CRIT_DAMAGE, 0.5)
            )
        ));

        register(new ItemDefinition(
            ItemId.BALLISTIC_CPU,
            "Ballistic CPU",
            "Tuned cadence and guidance.",
            ItemPoolType.BOSS,
            List.of(
                StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 1.25),
                StatModifier.additive(PlayerStatId.PROJECTILE_SPEED, 80.0),
                StatModifier.additive(PlayerStatId.CRIT_CHANCE, 4.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.PLASMA_ROUNDS,
            "Plasma Rounds",
            "+15% damage, +1 pierce, +5% speed.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 1.15),
                StatModifier.additive(PlayerStatId.PROJECTILE_PIERCE, 1.0),
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_SPEED, 1.05)
            )
        ));

        register(new ItemDefinition(
            ItemId.RICOCHET_CORE,
            "Ricochet Core",
            "+2 bounces, -10% range.",
            ItemPoolType.BOSS,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_BOUNCE, 2.0),
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_RANGE, 0.90)
            )
        ));

        register(new ItemDefinition(
            ItemId.DRILL_BIT,
            "Drill Bit",
            "+2 pierce, +0.1 damage.",
            ItemPoolType.BOSS,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_PIERCE, 2.0),
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.1)
            )
        ));

        register(new ItemDefinition(
            ItemId.GOLDEN_MIND,
            "Golden Mind",
            "Luck shines on every roll.",
            ItemPoolType.SECRET,
            List.of(
                StatModifier.additive(PlayerStatId.LUCK, 25.0),
                StatModifier.additive(PlayerStatId.SHOP_DISCOUNT, 0.10),
                StatModifier.additive(PlayerStatId.CRIT_CHANCE, 5.0)
            )
        ));

        // LEGENDARY
        register(new ItemDefinition(
            ItemId.CHAOS_ENGINE,
            "Chaos Engine",
            "+20% fire rate, +15% damage, +2 pierce, -15 max health.",
            ItemPoolType.BOSS,
            List.of(
                StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 1.20),
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 1.15),
                StatModifier.additive(PlayerStatId.PROJECTILE_PIERCE, 2.0),
                StatModifier.additive(PlayerStatId.MAX_HEALTH, -15.0)
            )
        ));

        register(new ItemDefinition(
            ItemId.TITAN_BLOOD,
            "Titan Blood",
            "+40 max health, +10 armor, -20% move speed.",
            ItemPoolType.BOSS,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 40.0),
                StatModifier.additive(PlayerStatId.ARMOR, 10.0),
                StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 0.80)
            )
        ));
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
