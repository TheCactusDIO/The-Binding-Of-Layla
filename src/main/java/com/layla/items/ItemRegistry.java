package com.layla.items;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.layla.model.PlayerStatId;
import com.layla.model.StatModifier;
import com.layla.services.AchievementService;

/**
 * Static registry of every passive item definition.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Stores {@link ItemDefinition} metadata for each {@link ItemId}.</li>
 *   <li>Stores icon paths used by UI/pedestals.</li>
 *   <li>Filters items by {@link ItemPoolType} and optional {@link AchievementService} locks.</li>
 * </ul>
 */
public final class ItemRegistry {

    private static final String DEFAULT_ICON_PATH = "/assets/images/item1.png";

    // --- Achievement IDs (must match AchievementService) ---
    private static final String ACH_FIRST_KILL      = "ACH_FIRST_KILL";
    private static final String ACH_NOVICE_HUNTER   = "ACH_NOVICE_HUNTER";
    private static final String ACH_FLOOR_MASTER_1  = "ACH_FLOOR_MASTER_1";
    private static final String ACH_BOSS_SLAYER     = "ACH_BOSS_SLAYER";
    private static final String ACH_POCKET_MONEY    = "ACH_POCKET_MONEY";
    private static final String ACH_BIG_SPENDER     = "ACH_BIG_SPENDER";
    private static final String ACH_GEAR_UP         = "ACH_GEAR_UP";
    private static final String ACH_THE_END         = "ACH_THE_END";
    private static final String ACH_SURVIVOR        = "ACH_SURVIVOR";
    private static final String ACH_TRY_AGAIN       = "ACH_TRY_AGAIN";

    private static final Map<ItemId, ItemDefinition> ITEMS = new EnumMap<>(ItemId.class);
    private static final Map<ItemId, String> ICON_PATHS = new EnumMap<>(ItemId.class);

    static {
        // =========================================================
        // SHOP POOL (antes TREASURE)  +  locks por logros
        // =========================================================

        // --- WAVE 3: Mushrooms & Speed ---
        register(shop(ItemId.ODD_MUSHROOM_THIN,
                "Odd Mushroom (Thin)",
                "Fire Rate Up + Speed Up + DMG Down.",
                List.of(
                        StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 2.0),
                        StatModifier.additive(PlayerStatId.MOVE_SPEED, 30.0),
                        StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 0.9)
                )),
                "/assets/images/odd_mushroom_thin.png"
        );

        register(shop(ItemId.ODD_MUSHROOM_LARGE,
                "Odd Mushroom (Large)",
                "HP Up + DMG Up + Range Up + Speed Down.",
                List.of(
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 1.0),
                        StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 20.0),
                        StatModifier.additive(PlayerStatId.MOVE_SPEED, -20.0)
                )),
                "/assets/images/odd_mushroom_large.png"
        );

        register(shop(ItemId.GROWTH_HORMONES,
                "Growth Hormones",
                "DMG Up + Speed Up.",
                List.of(
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 1.0),
                        StatModifier.additive(PlayerStatId.MOVE_SPEED, 20.0)
                )),
                "/assets/images/growth_hormones.png"
        );

        register(shop(ItemId.JESUS_JUICE,
                "Jesus Juice",
                "DMG Up + Range Up.",
                List.of(
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.5),
                        StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 40.0)
                )),
                "/assets/images/jesus_juice.png"
        );

        register(shop(ItemId.ROID_RAGE,
                "Roid Rage",
                "Speed Up + Range Up.",
                List.of(
                        StatModifier.additive(PlayerStatId.MOVE_SPEED, 20.0),
                        StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 20.0)
                )),
                "/assets/images/roid_rage.png"
        );

        // LOCK: completar piso 1
        register(shopLocked(ItemId.THE_BELT,
                "The Belt",
                "Speed Up.",
                List.of(StatModifier.additive(PlayerStatId.MOVE_SPEED, 25.0)),
                ACH_FLOOR_MASTER_1),
                "/assets/images/the_belt.png"
        );

        register(shop(ItemId.WOODEN_SPOON,
                "Wooden Spoon",
                "Speed Up.",
                List.of(StatModifier.additive(PlayerStatId.MOVE_SPEED, 25.0))),
                "/assets/images/wooden_spoon.png"
        );

        // --- Isaac classics (Wave 2) ---
        // LOCK: primera muerte
        register(shopLocked(ItemId.STIGMATA,
                "Stigmata",
                "HP Up + DMG Up.",
                List.of(
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.3)
                ),
                ACH_TRY_AGAIN),
                "/assets/images/stigmata.png"
        );

        register(shop(ItemId.BLUE_CAP,
                "Blue Cap",
                "HP Up + Tears Up + Shot Speed Down.",
                List.of(
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                        StatModifier.additive(PlayerStatId.FIRE_RATE, 0.7),
                        StatModifier.multiplicative(PlayerStatId.PROJECTILE_SPEED, 0.8)
                )),
                "/assets/images/blue_cap.png"
        );

        register(shop(ItemId.STEM_CELLS,
                "Stem Cells",
                "HP Up + Shot Speed Up.",
                List.of(
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                        StatModifier.multiplicative(PlayerStatId.PROJECTILE_SPEED, 1.16)
                )),
                "/assets/images/stem_cells.png"
        );

        register(shop(ItemId.SMB_SUPER_FAN,
                "SMB Super Fan",
                "All stats up... but you feel slower.",
                List.of(
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.3),
                        StatModifier.additive(PlayerStatId.FIRE_RATE, 0.2),
                        StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 0.85)
                )),
                "/assets/images/smb_super_fan.png"
        );

        register(shop(ItemId.CAPRICORN,
                "Capricorn",
                "All stats up.",
                List.of(
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.5),
                        StatModifier.additive(PlayerStatId.MOVE_SPEED, 15.0),
                        StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 1.0)
                )),
                "/assets/images/capricorn.png"
        );

        // --- Isaac classics (Wave 1) ---
        // LOCK: primera kill
        register(shopLocked(ItemId.MEAT,
                "Meat!",
                "HP Up + DMG Up.",
                List.of(
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.3)
                ),
                ACH_FIRST_KILL),
                "/assets/images/meat.png"
        );

        // LOCK: 50 kills
        register(shopLocked(ItemId.THE_HALO,
                "The Halo",
                "All stats up.",
                List.of(
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.3),
                        StatModifier.additive(PlayerStatId.FIRE_RATE, 0.2),
                        StatModifier.additive(PlayerStatId.MOVE_SPEED, 15.0)
                ),
                ACH_NOVICE_HUNTER),
                "/assets/images/halo.png"
        );

        register(shop(ItemId.BUCKET_OF_LARD,
                "Bucket of Lard",
                "HP way up, speed down.",
                List.of(
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 4.0),
                        StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 0.85)
                )),
                "/assets/images/lard.png"
        );

        // LOCK: 25 monedas en una run
        register(shopLocked(ItemId.CRICKETS_HEAD,
                "Cricket's Head",
                "Massive damage.",
                List.of(
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.5),
                        StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 1.5)
                ),
                ACH_POCKET_MONEY),
                "/assets/images/crickets_head.png"
        );

        register(shop(ItemId.SYNTHOIL,
                "Synthoil",
                "DMG + Range.",
                List.of(
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 1.0),
                        StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 50.0)
                )),
                "/assets/images/synthoil.png"
        );

        register(shop(ItemId.PENTAGRAM,
                "Pentagram",
                "DMG up.",
                List.of(StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 1.0))),
                "/assets/images/pentagram.png"
        );

        // --- Shot items ---
        register(shop(ItemId.TRIPLE_SHOT,
                "The Inner Eye",
                "Triple shot but slower fire rate.",
                List.of(
                        StatModifier.additive(PlayerStatId.PROJECTILE_COUNT, 2.0),
                        StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 0.7)
                )),
                "/assets/images/tripleshot.png"
        );

        // LOCK: tener 5 pasivos
        register(shopLocked(ItemId.QUAD_SHOT,
                "Mutant Spider",
                "Quad shot, much slower.",
                List.of(
                        StatModifier.additive(PlayerStatId.PROJECTILE_COUNT, 3.0),
                        StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 0.55)
                ),
                ACH_GEAR_UP),
                "/assets/images/quadshot.png"
        );

        register(shop(ItemId.CUPIDS_ARROW,
                "Cupid's Arrow",
                "Piercing shots.",
                List.of(StatModifier.additive(PlayerStatId.PROJECTILE_PIERCE, 1.0))),
                "/assets/images/piercing.png"
        );

        register(shop(ItemId.RUBBER_CEMENT,
                "Rubber Cement",
                "Bouncing tears.",
                List.of(StatModifier.additive(PlayerStatId.PROJECTILE_BOUNCE, 1.0))),
                "/assets/images/bouncing.png"
        );

        // LOCK: comprar 1 objeto en tienda
        register(shopLocked(ItemId.SPOON_BENDER,
                "Spoon Bender",
                "Homing shots.",
                List.of(StatModifier.additive(PlayerStatId.PROJECTILE_HOMING, 1.0)),
                ACH_BIG_SPENDER),
                "/assets/images/homing.png"
        );

        // =========================================================
        // BOSS POOL  (solo aparece tras matar boss)
        // =========================================================

        // LOCK: matar tu primer boss
        register(bossLocked(ItemId.THE_WAFER,
                "The Wafer",
                "HEALTH UP!!!.",
                List.of(StatModifier.additive(PlayerStatId.MAX_HEALTH, 6.0)),
                ACH_BOSS_SLAYER),
                "/assets/images/wafer.png"
        );

        register(boss(ItemId.MAGIC_MUSHROOM,
                "Magic Mushroom",
                "All stats up! (Strong)",
                List.of(
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                        StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 1.5),
                        StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 30.0),
                        StatModifier.additive(PlayerStatId.MOVE_SPEED, 20.0
                        )
                )),
                "/assets/images/magic_mushroom.png"
        );

        // LOCK: ganar run
        register(bossLocked(ItemId.SACRED_HEART,
                "Sacred Heart",
                "Homing + DMG + HP.",
                List.of(
                        StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 2.3),
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                        StatModifier.additive(PlayerStatId.PROJECTILE_HOMING, 1.0),
                        StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 0.6),
                        StatModifier.multiplicative(PlayerStatId.PROJECTILE_SPEED, 0.75)
                ),
                ACH_THE_END),
                "/assets/images/sacred_heart.png"
        );

        register(boss(ItemId.TWENTY_TWENTY,
                "20/20",
                "Double Shot.",
                List.of(StatModifier.additive(PlayerStatId.PROJECTILE_COUNT, 1.0))),
                "/assets/images/20_20.png"
        );

        // LOCK: ganar con 2 HP o menos
        register(bossLocked(ItemId.POLYPHEMUS,
                "Polyphemus",
                "Mega Tears.",
                List.of(
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 4.0),
                        StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 2.0),
                        StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 0.4)
                ),
                ACH_SURVIVOR),
                "/assets/images/polyphemus.png"
        );

        register(boss(ItemId.SOY_MILK,
                "Soy Milk",
                "DMG down, Fire Rate way up.",
                List.of(
                        StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 5.0),
                        StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 0.2)
                )),
                "/assets/images/soy_milk.png"
        );
    }

    private ItemRegistry() {
        // utility class
    }

    // -------------------------
    // Factory helpers (cleaner registry)
    // -------------------------

    private static ItemDefinition shop(ItemId id, String name, String desc, List<StatModifier> mods) {
        return new ItemDefinition(id, name, desc, ItemPoolType.SHOP, mods);
    }

    private static ItemDefinition shopLocked(ItemId id, String name, String desc, List<StatModifier> mods, String achId) {
        return new ItemDefinition(id, name, desc, ItemPoolType.SHOP, mods, achId);
    }

    private static ItemDefinition boss(ItemId id, String name, String desc, List<StatModifier> mods) {
        return new ItemDefinition(id, name, desc, ItemPoolType.BOSS, mods);
    }

    private static ItemDefinition bossLocked(ItemId id, String name, String desc, List<StatModifier> mods, String achId) {
        return new ItemDefinition(id, name, desc, ItemPoolType.BOSS, mods, achId);
    }

    /**
     * Registers an item definition and its icon path.
     *
     * <p>Centralizing this avoids "definition registered but missing icon" bugs.</p>
     *
     * @param definition immutable metadata for the item
     * @param iconPath resource path for the icon (e.g. "/assets/images/xxx.png")
     */
    private static void register(ItemDefinition definition, String iconPath) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(iconPath, "iconPath");

        ITEMS.put(definition.getId(), definition);
        ICON_PATHS.put(definition.getId(), iconPath);
    }

    /**
     * Returns the definition for a given item id.
     *
     * @param id item identifier
     * @return definition or {@code null} if missing / id is null
     */
    public static ItemDefinition getDefinition(ItemId id) {
        return (id == null) ? null : ITEMS.get(id);
    }

    /**
     * Returns the icon resource path for the given item id.
     *
     * @param id item identifier
     * @return icon path, or a default placeholder path if missing / id is null
     */
    public static String getIconPath(ItemId id) {
        if (id == null) return DEFAULT_ICON_PATH;
        return ICON_PATHS.getOrDefault(id, DEFAULT_ICON_PATH);
    }

    /**
     * Returns unlocked items from a given pool.
     *
     * <p>If {@code achievements} is null, only items unlocked by default are returned.</p>
     *
     * @param poolType pool to filter
     * @param achievements achievement service (optional)
     * @return immutable list of eligible items (may be empty)
     */
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

        return List.copyOf(list);
    }

    /**
     * @return immutable list with all registered item definitions.
     */
    public static List<ItemDefinition> allDefinitions() {
        return List.copyOf(ITEMS.values());
    }
}
