package com.layla.items;

/**
 * Unique identifiers for every collectible item.
 */
public enum ItemId {
    // --- SHOT MECHANIC ---
    TRIPLE_SHOT,
    QUAD_SHOT,
    CUPIDS_ARROW,
    RUBBER_CEMENT,
    SPOON_BENDER,

    // --- ISAAC CLASSICS (WAVE 1) ---
    MEAT,           // Vida + Daño
    THE_HALO,       // All stats
    MAGIC_MUSHROOM, // All stats + Multiplier
    BUCKET_OF_LARD, // Vida masiva, lento
    CRICKETS_HEAD,  // Daño masivo
    SYNTHOIL,       // Daño + Rango
    PENTAGRAM,      // Daño
    SOY_MILK,       // Cadencia extrema

    // --- ISAAC CLASSICS (WAVE 2 - HP & UTILITY) ---
    STIGMATA,       // HP + DMG
    BLUE_CAP,       // HP + Tears - ShotSpeed
    STEM_CELLS,     // HP + ShotSpeed
    SMB_SUPER_FAN,  // HP + All Stats - Speed
    CAPRICORN,      // All stats balanced

    // --- GAME CHANGERS ---
    POLYPHEMUS,     // Huge DMG, Slow Fire
    SACRED_HEART,   // Homing + Huge DMG Mult
    TWENTY_TWENTY,  // Double Shot (20/20)

    // --- WAVE 3 (NEW REQUESTS) ---
    THE_WAFER,          // Tankiness
    ODD_MUSHROOM_THIN,  // Fire Rate + Speed - Dmg
    ODD_MUSHROOM_LARGE, // HP + Dmg + Range - Speed
    GROWTH_HORMONES,    // Dmg + Speed
    JESUS_JUICE,        // Dmg + Range
    ROID_RAGE,          // Speed + Range
    THE_BELT,           // Speed
    WOODEN_SPOON        // Speed
}
