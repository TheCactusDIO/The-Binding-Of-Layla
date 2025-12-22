package com.layla.items;

/**
 * Pool (origen) al que pertenece un ítem pasivo.
 *
 * <p>Se usa para decidir en qué tipo de sala/loot puede aparecer el ítem.</p>
 */
public enum ItemPoolType {
    /** Ítems típicos de recompensa de boss. */
    BOSS,

    /** Ítems disponibles en tienda. */
    SHOP,
}
