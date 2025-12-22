package com.layla.items;

/**
 * Identificadores únicos para cada ítem coleccionable del juego.
 *
 * <p>Este enum se usa como clave estable en el código (registro de ítems, guardado/carga,
 * desbloqueos, pools, etc.).</p>
 */
public enum ItemId {

    /** Disparo triple. */
    TRIPLE_SHOT,
    /** Disparo cuádruple. */
    QUAD_SHOT,
    /** Flechas estilo Cupid (efecto de proyectil específico). */
    CUPIDS_ARROW,
    /** Proyectiles rebotan / efecto "rubber". */
    RUBBER_CEMENT,
    /** Proyectiles con homing (estilo Spoon Bender). */
    SPOON_BENDER,

    /** Vida + Daño. */
    MEAT,
    /** Todas las stats. */
    THE_HALO,
    /** Todas las stats + multiplicador. */
    MAGIC_MUSHROOM,
    /** Vida masiva, velocidad reducida. */
    BUCKET_OF_LARD,
    /** Daño masivo. */
    CRICKETS_HEAD,
    /** Daño + Rango. */
    SYNTHOIL,
    /** Daño. */
    PENTAGRAM,
    /** Cadencia extrema. */
    SOY_MILK,

    /** Vida + Daño. */
    STIGMATA,
    /** Vida + Tears, -ShotSpeed. */
    BLUE_CAP,
    /** Vida + ShotSpeed. */
    STEM_CELLS,
    /** Vida + todas las stats, -Speed. */
    SMB_SUPER_FAN,
    /** Todas las stats equilibradas. */
    CAPRICORN,

    /** Mucho daño, baja cadencia. */
    POLYPHEMUS,
    /** Homing + multiplicador de daño alto. */
    SACRED_HEART,
    /** Doble disparo (20/20). */
    TWENTY_TWENTY,

    /** Más tankiness / reducción de daño. */
    THE_WAFER,
    /** Cadencia + Speed, -Daño. */
    ODD_MUSHROOM_THIN,
    /** Vida + Daño + Rango, -Speed. */
    ODD_MUSHROOM_LARGE,
    /** Daño + Speed. */
    GROWTH_HORMONES,
    /** Daño + Rango. */
    JESUS_JUICE,
    /** Speed + Rango. */
    ROID_RAGE,
    /** Speed. */
    THE_BELT,
    /** Speed. */
    WOODEN_SPOON
}
