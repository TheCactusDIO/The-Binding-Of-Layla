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
 * Registro estático de todas las definiciones de objetos pasivos.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Guarda los metadatos de {@link ItemDefinition} para cada {@link ItemId}.</li>
 *   <li>Guarda las rutas de iconos usadas por la UI/pedestales.</li>
 *   <li>Filtra objetos por {@link ItemPoolType} y bloqueos opcionales por logros ({@link AchievementService}).</li>
 * </ul>
 */
public final class ItemRegistry {

    private static final String DEFAULT_ICON_PATH = "/assets/images/item1.png";

    // --- IDs de logros (deben coincidir con AchievementService) ---
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
        // POOL DE TIENDA (antes TREASURE)  +  bloqueos por logros
        // =========================================================

        // --- OLEADA 3: Hongos y velocidad ---
        register(shop(ItemId.ODD_MUSHROOM_THIN,
                "Odd Mushroom (Thin)",
                "Cadencia de fuego ↑ + Velocidad ↑ + Daño ↓.",
                List.of(
                        StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 2.0),
                        StatModifier.additive(PlayerStatId.MOVE_SPEED, 30.0),
                        StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 0.9)
                )),
                "/assets/images/odd_mushroom_thin.png"
        );

        register(shop(ItemId.ODD_MUSHROOM_LARGE,
                "Odd Mushroom (Large)",
                "Vida máx. ↑ + Daño ↑ + Alcance ↑ + Velocidad ↓.",
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
                "Daño ↑ + Velocidad ↑.",
                List.of(
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 1.0),
                        StatModifier.additive(PlayerStatId.MOVE_SPEED, 20.0)
                )),
                "/assets/images/growth_hormones.png"
        );

        register(shop(ItemId.JESUS_JUICE,
                "Jesus Juice",
                "Daño ↑ + Alcance ↑.",
                List.of(
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.5),
                        StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 40.0)
                )),
                "/assets/images/jesus_juice.png"
        );

        register(shop(ItemId.ROID_RAGE,
                "Roid Rage",
                "Velocidad ↑ + Alcance ↑.",
                List.of(
                        StatModifier.additive(PlayerStatId.MOVE_SPEED, 20.0),
                        StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 20.0)
                )),
                "/assets/images/roid_rage.png"
        );

        // BLOQUEO: completar piso 1
        register(shopLocked(ItemId.THE_BELT,
                "The Belt",
                "Velocidad ↑.",
                List.of(StatModifier.additive(PlayerStatId.MOVE_SPEED, 25.0)),
                ACH_FLOOR_MASTER_1),
                "/assets/images/the_belt.png"
        );

        register(shop(ItemId.WOODEN_SPOON,
                "Wooden Spoon",
                "Velocidad ↑.",
                List.of(StatModifier.additive(PlayerStatId.MOVE_SPEED, 25.0))),
                "/assets/images/wooden_spoon.png"
        );

        // --- Clásicos de Isaac (Oleada 2) ---
        // BLOQUEO: primera muerte
        register(shopLocked(ItemId.STIGMATA,
                "Stigmata",
                "Vida máx. ↑ + Daño ↑.",
                List.of(
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.3)
                ),
                ACH_TRY_AGAIN),
                "/assets/images/stigmata.png"
        );

        register(shop(ItemId.BLUE_CAP,
                "Blue Cap",
                "Vida máx. ↑ + Lágrimas ↑ + Velocidad de proyectil ↓.",
                List.of(
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                        StatModifier.additive(PlayerStatId.FIRE_RATE, 0.7),
                        StatModifier.multiplicative(PlayerStatId.PROJECTILE_SPEED, 0.8)
                )),
                "/assets/images/blue_cap.png"
        );

        register(shop(ItemId.STEM_CELLS,
                "Stem Cells",
                "Vida máx. ↑ + Velocidad de proyectil ↑.",
                List.of(
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                        StatModifier.multiplicative(PlayerStatId.PROJECTILE_SPEED, 1.16)
                )),
                "/assets/images/stem_cells.png"
        );

        register(shop(ItemId.SMB_SUPER_FAN,
                "SMB Super Fan",
                "Todas las estadísticas ↑... pero te sientes más lento.",
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
                "Todas las estadísticas ↑.",
                List.of(
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.5),
                        StatModifier.additive(PlayerStatId.MOVE_SPEED, 15.0),
                        StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 1.0)
                )),
                "/assets/images/capricorn.png"
        );

        // --- Clásicos de Isaac (Oleada 1) ---
        // BLOQUEO: primera kill
        register(shopLocked(ItemId.MEAT,
                "Meat!",
                "Vida máx. ↑ + Daño ↑.",
                List.of(
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.3)
                ),
                ACH_FIRST_KILL),
                "/assets/images/meat.png"
        );

        // BLOQUEO: 50 kills
        register(shopLocked(ItemId.THE_HALO,
                "The Halo",
                "Todas las estadísticas ↑.",
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
                "Mucha vida ↑, velocidad ↓.",
                List.of(
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 4.0),
                        StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 0.85)
                )),
                "/assets/images/lard.png"
        );

        // BLOQUEO: 25 monedas en una run
        register(shopLocked(ItemId.CRICKETS_HEAD,
                "Cricket's Head",
                "Daño masivo.",
                List.of(
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 0.5),
                        StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 1.5)
                ),
                ACH_POCKET_MONEY),
                "/assets/images/crickets_head.png"
        );

        register(shop(ItemId.SYNTHOIL,
                "Synthoil",
                "Daño + Alcance.",
                List.of(
                        StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 1.0),
                        StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 50.0)
                )),
                "/assets/images/synthoil.png"
        );

        register(shop(ItemId.PENTAGRAM,
                "Pentagram",
                "Daño ↑.",
                List.of(StatModifier.additive(PlayerStatId.PROJECTILE_DAMAGE, 1.0))),
                "/assets/images/pentagram.png"
        );

        // --- Objetos de disparo ---
        register(shop(ItemId.TRIPLE_SHOT,
                "The Inner Eye",
                "Triple disparo, pero menor cadencia de fuego.",
                List.of(
                        StatModifier.additive(PlayerStatId.PROJECTILE_COUNT, 2.0),
                        StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 0.7)
                )),
                "/assets/images/tripleshot.png"
        );

        // BLOQUEO: tener 5 pasivos
        register(shopLocked(ItemId.QUAD_SHOT,
                "Mutant Spider",
                "Cuádruple disparo, mucho más lento.",
                List.of(
                        StatModifier.additive(PlayerStatId.PROJECTILE_COUNT, 3.0),
                        StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 0.55)
                ),
                ACH_GEAR_UP),
                "/assets/images/quadshot.png"
        );

        register(shop(ItemId.CUPIDS_ARROW,
                "Cupid's Arrow",
                "Disparos perforantes.",
                List.of(StatModifier.additive(PlayerStatId.PROJECTILE_PIERCE, 1.0))),
                "/assets/images/piercing.png"
        );

        register(shop(ItemId.RUBBER_CEMENT,
                "Rubber Cement",
                "Lágrimas rebotantes.",
                List.of(StatModifier.additive(PlayerStatId.PROJECTILE_BOUNCE, 1.0))),
                "/assets/images/bouncing.png"
        );

        // BLOQUEO: comprar 1 objeto en tienda
        register(shopLocked(ItemId.SPOON_BENDER,
                "Spoon Bender",
                "Disparos teledirigidos.",
                List.of(StatModifier.additive(PlayerStatId.PROJECTILE_HOMING, 1.0)),
                ACH_BIG_SPENDER),
                "/assets/images/homing.png"
        );

        // =========================================================
        // POOL DE BOSS  (solo aparece tras matar al boss)
        // =========================================================

        // BLOQUEO: matar tu primer boss
        register(bossLocked(ItemId.THE_WAFER,
                "The Wafer",
                "¡¡¡VIDA ↑!!!.",
                List.of(StatModifier.additive(PlayerStatId.MAX_HEALTH, 6.0)),
                ACH_BOSS_SLAYER),
                "/assets/images/wafer.png"
        );

        register(boss(ItemId.MAGIC_MUSHROOM,
                "Magic Mushroom",
                "¡Todas las estadísticas ↑! (Fuerte)",
                List.of(
                        StatModifier.additive(PlayerStatId.MAX_HEALTH, 2.0),
                        StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 1.5),
                        StatModifier.additive(PlayerStatId.PROJECTILE_RANGE, 30.0),
                        StatModifier.additive(PlayerStatId.MOVE_SPEED, 20.0
                        )
                )),
                "/assets/images/magic_mushroom.png"
        );

        // BLOQUEO: ganar run
        register(bossLocked(ItemId.SACRED_HEART,
                "Sacred Heart",
                "Teledirigido + Daño + Vida.",
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
                "Doble disparo.",
                List.of(StatModifier.additive(PlayerStatId.PROJECTILE_COUNT, 1.0))),
                "/assets/images/20_20.png"
        );

        // BLOQUEO: ganar con 2 HP o menos
        register(bossLocked(ItemId.POLYPHEMUS,
                "Polyphemus",
                "Mega lágrimas.",
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
                "Daño ↓, cadencia de fuego muy ↑.",
                List.of(
                        StatModifier.multiplicative(PlayerStatId.FIRE_RATE, 5.0),
                        StatModifier.multiplicative(PlayerStatId.PROJECTILE_DAMAGE, 0.2)
                )),
                "/assets/images/soy_milk.png"
        );
    }

    private ItemRegistry() {
        // clase de utilidad
    }

    // -------------------------
    // Helpers de factoría (registro más limpio)
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
     * Registra una definición de objeto y su ruta de icono.
     *
     * <p>Centralizar esto evita bugs del tipo "definición registrada pero sin icono".</p>
     *
     * @param definition metadatos inmutables del objeto
     * @param iconPath ruta del recurso del icono (p. ej. "/assets/images/xxx.png")
     */
    private static void register(ItemDefinition definition, String iconPath) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(iconPath, "iconPath");

        ITEMS.put(definition.getId(), definition);
        ICON_PATHS.put(definition.getId(), iconPath);
    }

    /**
     * Devuelve la definición de un objeto para un id dado.
     *
     * @param id identificador del objeto
     * @return definición o {@code null} si no existe / si id es null
     */
    public static ItemDefinition getDefinition(ItemId id) {
        return (id == null) ? null : ITEMS.get(id);
    }

    /**
     * Devuelve la ruta del recurso del icono para el id de objeto dado.
     *
     * @param id identificador del objeto
     * @return ruta del icono, o una ruta por defecto si no existe / si id es null
     */
    public static String getIconPath(ItemId id) {
        if (id == null) return DEFAULT_ICON_PATH;
        return ICON_PATHS.getOrDefault(id, DEFAULT_ICON_PATH);
    }

    /**
     * Devuelve los objetos desbloqueados de un pool dado.
     *
     * <p>Si {@code achievements} es null, solo se devuelven los objetos desbloqueados por defecto.</p>
     *
     * @param poolType pool a filtrar
     * @param achievements servicio de logros (opcional)
     * @return lista inmutable de objetos elegibles (puede estar vacía)
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
     * @return lista inmutable con todas las definiciones de objetos registradas.
     */
    public static List<ItemDefinition> allDefinitions() {
        return List.copyOf(ITEMS.values());
    }
}
