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
        // --- ITEM REGISTRATION START ---

        // Ejemplo: Bloqueado por First Blood
        register(new ItemDefinition(
            ItemId.PEASHOOTER_AMMO,
            "Peashooter Ammo",
            "Tiny bullets, faster shots. (Reward: First Blood)",
            ItemPoolType.TREASURE,
            List.of(
                StatModifier.additive(PlayerStatId.PROJECTILE_SPEED, 50.0),
                StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.1)
            ),
            "ACH_FIRST_KILL" // <--- REQUIERE LOGRO
        ));
        ICON_PATHS.put(ItemId.PEASHOOTER_AMMO, "assets/images/item1.png"); // Reusamos placeholders

        // Ejemplo: Bloqueado por Survivor
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

        // Bloqueado por Novice Hunter
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

        // Bloqueado por Pocket Money
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

        // Bloqueado por Big Spender
        register(new ItemDefinition(
            ItemId.COUPON,
            "Coupon",
            "10% shop discount. (Reward: Big Spender)",
            ItemPoolType.SHOP,
            List.of(StatModifier.additive(PlayerStatId.SHOP_DISCOUNT, 0.10)),
            "ACH_BIG_SPENDER"
        ));

        // Bloqueado por Boss Slayer
        register(new ItemDefinition(
            ItemId.LUCKY_CHARM,
            "Lucky Charm",
            "+8 luck. (Reward: Boss Slayer)",
            ItemPoolType.TREASURE,
            List.of(StatModifier.additive(PlayerStatId.LUCK, 8.0)),
            "ACH_BOSS_SLAYER"
        ));

        // Bloqueado por The End (Victoria)
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

        // Bloqueado por Try Again (Muerte)
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

        // Bloqueado por Gear Up
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

        // Ítems comunes sin bloqueo (relleno para que siempre haya algo)
        register(new ItemDefinition(ItemId.RUNNING_SHOES, "Running Shoes", "+10% move speed.", ItemPoolType.TREASURE, List.of(StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 1.10))));
        register(new ItemDefinition(ItemId.PROTEIN_BAR, "Protein Bar", "+5 max health.", ItemPoolType.TREASURE, List.of(StatModifier.additive(PlayerStatId.MAX_HEALTH, 5.0))));
        register(new ItemDefinition(ItemId.MAGNET, "Magnet", "+50 pickup range.", ItemPoolType.SHOP, List.of(StatModifier.additive(PlayerStatId.PICKUP_RANGE, 50.0))));
        register(new ItemDefinition(ItemId.SWIFT_BOOTS, "Swift Boots", "+Move speed.", ItemPoolType.TREASURE, List.of(StatModifier.additive(PlayerStatId.MOVE_SPEED, 40.0))));
        register(new ItemDefinition(ItemId.RANGE_UP, "Range Up", "Projectiles live longer.", ItemPoolType.TREASURE, List.of(StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 0.6))));

        // --- END REGISTRATION ---
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
        // Fallback genérico si no tiene icono específico asignado arriba
        return ICON_PATHS.getOrDefault(id, "assets/images/item1.png");
    }

    // Método legacy (devuelve todos)
    public static List<ItemDefinition> getByPool(ItemPoolType poolType) {
        return getUnlockedByPool(poolType, null);
    }

    /**
     * Devuelve la lista de ítems de un pool específico, FILTRANDO los que no estén desbloqueados.
     * @param poolType Tipo de pool (TIENDA, TESORO, etc.)
     * @param achievements Servicio de logros (puede ser null, en cuyo caso devuelve solo los default)
     */
    public static List<ItemDefinition> getUnlockedByPool(ItemPoolType poolType, AchievementService achievements) {
        if (poolType == null) return List.of();

        List<ItemDefinition> list = new ArrayList<>();

        for (ItemDefinition def : ITEMS.values()) {
            // 1. Coincidir pool (o aceptar pool genérico si existiera)
            if (def.getPoolType() != poolType) continue;

            // 2. Verificar desbloqueo
            if (def.isUnlockedByDefault()) {
                list.add(def);
            } else if (achievements != null && achievements.isUnlocked(def.getRequiredAchievementId())) {
                list.add(def);
            }
        }

        // Fallback de seguridad: Si no hay ítems desbloqueados en este pool,
        // devolvemos los básicos para no romper la tienda.
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
