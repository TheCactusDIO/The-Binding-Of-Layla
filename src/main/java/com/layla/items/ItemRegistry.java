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
        // ==========================================
        //          WAVE 3: MUSHROOMS & SPEED
        // ==========================================

        // --- THE WAFER ---
        register(new ItemDefinition(
            ItemId.THE_WAFER,
            "The Wafer",
            "HEALTH UP!!!.",
            ItemPoolType.BOSS,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 6.0) // Añadir que te cure entero al cogerlo
            )
        ));
        ICON_PATHS.put(ItemId.THE_WAFER, "assets/images/wafer.png");

        // --- ODD MUSHROOM (THIN) ---
        register(new ItemDefinition(
            ItemId.ODD_MUSHROOM_THIN,
            "Odd Mushroom (Thin)",
            "Fire Rate Up + Speed Up + DMG Down.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 2.0),      // Dispara el doble de rápido
                StatModifier.additive(PlayerStatId.MOVE_SPEED, 30.0),          // Mucha velocidad
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 0.9) // -10% Daño
            )
        ));
        ICON_PATHS.put(ItemId.ODD_MUSHROOM_THIN, "assets/images/odd_mushroom_thin.png");

        // --- ODD MUSHROOM (LARGE) ---
        register(new ItemDefinition(
            ItemId.ODD_MUSHROOM_LARGE,
            "Odd Mushroom (Large)",
            "HP Up + DMG Up + Range Up + Speed Down.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 1.0),
                StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 20.0),
                StatModifier.additive(PlayerStatId.MOVE_SPEED, -20.0)          // Te hace más lento
            )
        ));
        ICON_PATHS.put(ItemId.ODD_MUSHROOM_LARGE, "assets/images/odd_mushroom_large.png");

        // --- GROWTH HORMONES ---
        register(new ItemDefinition(
            ItemId.GROWTH_HORMONES,
            "Growth Hormones",
            "DMG Up + Speed Up.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 1.0),
                StatModifier.additive(PlayerStatId.MOVE_SPEED, 20.0)
            )
        ));
        ICON_PATHS.put(ItemId.GROWTH_HORMONES, "assets/images/growth_hormones.png");

        // --- JESUS JUICE ---
        register(new ItemDefinition(
            ItemId.JESUS_JUICE,
            "Jesus Juice",
            "DMG Up + Range Up.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.5),
                StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 40.0)
            )
        ));
        ICON_PATHS.put(ItemId.JESUS_JUICE, "assets/images/jesus_juice.png");

        // --- ROID RAGE ---
        register(new ItemDefinition(
            ItemId.ROID_RAGE,
            "Roid Rage",
            "Speed Up + Range Up.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MOVE_SPEED, 20.0),
                StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 20.0)
            )
        ));
        ICON_PATHS.put(ItemId.ROID_RAGE, "assets/images/roid_rage.png");

        // --- THE BELT ---
        register(new ItemDefinition(
            ItemId.THE_BELT,
            "The Belt",
            "Speed Up.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MOVE_SPEED, 25.0)
            )
        ));
        ICON_PATHS.put(ItemId.THE_BELT, "assets/images/the_belt.png");

        // --- WOODEN SPOON ---
        register(new ItemDefinition(
            ItemId.WOODEN_SPOON,
            "Wooden Spoon",
            "Speed Up.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MOVE_SPEED, 25.0)
            )
        ));
        ICON_PATHS.put(ItemId.WOODEN_SPOON, "assets/images/wooden_spoon.png");

        // ==========================================
        //          ISAAC CLASSICS (WAVE 2)
        // ==========================================

        // --- STIGMATA ---
        register(new ItemDefinition(
            ItemId.STIGMATA,
            "Stigmata",
            "HP Up + DMG Up.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.3)
            )
        ));
        ICON_PATHS.put(ItemId.STIGMATA, "assets/images/stigmata.png");

        // --- BLUE CAP ---
        register(new ItemDefinition(
            ItemId.BLUE_CAP,
            "Blue Cap",
            "HP Up + Tears Up + Shot Speed Down.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                StatModifier.additive(PlayerStatId.FIRE_RATE, 0.7),
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_SPEED, 0.8)
            )
        ));
        ICON_PATHS.put(ItemId.BLUE_CAP, "assets/images/blue_cap.png");

        // --- STEM CELLS ---
        register(new ItemDefinition(
            ItemId.STEM_CELLS,
            "Stem Cells",
            "HP Up + Shot Speed Up.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_SPEED, 1.16)
            )
        ));
        ICON_PATHS.put(ItemId.STEM_CELLS, "assets/images/stem_cells.png");

        // --- SMB SUPER FAN ---
        register(new ItemDefinition(
            ItemId.SMB_SUPER_FAN,
            "SMB Super Fan",
            "All stats up... but you feel slower.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.3),
                StatModifier.additive(PlayerStatId.FIRE_RATE, 0.2),
                StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 0.85)
            )
        ));
        ICON_PATHS.put(ItemId.SMB_SUPER_FAN, "assets/images/smb_super_fan.png");

        // --- CAPRICORN ---
        register(new ItemDefinition(
            ItemId.CAPRICORN,
            "Capricorn",
            "All stats up.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.5),
                StatModifier.additive(PlayerStatId.MOVE_SPEED, 15.0),
                StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 1.0) // +1 segundo de vida
            )
        ));
        ICON_PATHS.put(ItemId.CAPRICORN, "assets/images/capricorn.png");

        // ==========================================
        //             GAME CHANGERS
        // ==========================================

        // --- POLYPHEMUS ---
        register(new ItemDefinition(
            ItemId.POLYPHEMUS,
            "Polyphemus",
            "Mega Tears.",
            ItemPoolType.BOSS,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 4.0),
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 2.0),
                StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 0.4) // Dispara muy lento
            )
        ));
        ICON_PATHS.put(ItemId.POLYPHEMUS, "assets/images/polyphemus.png");

        // --- SACRED HEART ---
        register(new ItemDefinition(
            ItemId.SACRED_HEART,
            "Sacred Heart",
            "Homing + DMG + HP.",
            ItemPoolType.BOSS,
            List.of(
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 2.3),
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                StatModifier.additive(PlayerStatId.PROJECTILE_HOMING, 1.0),
                StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 0.6),
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_SPEED, 0.75)
            )
        ));
        ICON_PATHS.put(ItemId.SACRED_HEART, "assets/images/sacred_heart.png");

        // --- 20/20 ---
        register(new ItemDefinition(
            ItemId.TWENTY_TWENTY,
            "20/20",
            "Double Shot.",
            ItemPoolType.BOSS,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_COUNT, 1.0)
            )
        ));
        ICON_PATHS.put(ItemId.TWENTY_TWENTY, "assets/images/20_20.png");

        // ==========================================
        //             ISAAC CLASSICS (WAVE 1)
        // ==========================================

        register(new ItemDefinition(
            ItemId.MEAT,
            "Meat!",
            "HP Up + DMG Up.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.3)
            )
        ));
        ICON_PATHS.put(ItemId.MEAT, "assets/images/meat.png");

        register(new ItemDefinition(
            ItemId.THE_HALO,
            "The Halo",
            "All stats up.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.3),
                StatModifier.additive(PlayerStatId.FIRE_RATE, 0.2),
                StatModifier.additive(PlayerStatId.MOVE_SPEED, 15.0)
            )
        ));
        ICON_PATHS.put(ItemId.THE_HALO, "assets/images/halo.png");

        register(new ItemDefinition(
            ItemId.MAGIC_MUSHROOM,
            "Magic Mushroom",
            "All stats up! (Strong)",
            ItemPoolType.BOSS,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 1.5),
                StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 30.0),
                StatModifier.additive(PlayerStatId.MOVE_SPEED, 20.0)
            )
        ));
        ICON_PATHS.put(ItemId.MAGIC_MUSHROOM, "assets/images/magic_mushroom.png");

        register(new ItemDefinition(
            ItemId.BUCKET_OF_LARD,
            "Bucket of Lard",
            "HP way up, speed down.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.MAX_HEALTH, 4.0),
                StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 0.85)
            )
        ));
        ICON_PATHS.put(ItemId.BUCKET_OF_LARD, "assets/images/lard.png");

        register(new ItemDefinition(
            ItemId.CRICKETS_HEAD,
            "Cricket's Head",
            "Massive damage.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.5),
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 1.5)
            )
        ));
        ICON_PATHS.put(ItemId.CRICKETS_HEAD, "assets/images/crickets_head.png");

        register(new ItemDefinition(
            ItemId.SYNTHOIL,
            "Synthoil",
            "DMG + Range.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 1.0),
                StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 50.0)
            )
        ));
        ICON_PATHS.put(ItemId.SYNTHOIL, "assets/images/synthoil.png");

        register(new ItemDefinition(
            ItemId.PENTAGRAM,
            "Pentagram",
            "DMG up.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 1.0)
            )
        ));
        ICON_PATHS.put(ItemId.PENTAGRAM, "assets/images/pentagram.png");

        register(new ItemDefinition(
            ItemId.SOY_MILK,
            "Soy Milk",
            "DMG down, Fire Rate way up.",
            ItemPoolType.BOSS,
            List.of(
                StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 5.0),
                StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 0.2)
            )
        ));
        ICON_PATHS.put(ItemId.SOY_MILK, "assets/images/soy_milk.png");

        // ==========================================
        //             SHOT ITEMS
        // ==========================================

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
        ICON_PATHS.put(ItemId.RUBBER_CEMENT, "assets/images/bouncing.png");

        register(new ItemDefinition(
            ItemId.SPOON_BENDER,
            "Spoon Bender",
            "Homing shots.",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_HOMING, 1.0)
            )
        ));
        ICON_PATHS.put(ItemId.SPOON_BENDER, "assets/images/homing.png");
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
