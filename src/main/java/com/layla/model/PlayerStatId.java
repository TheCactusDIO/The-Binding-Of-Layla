package com.layla.model;

/**
 * Identifiers for every player statistic that can be modified at runtime.
 * These drive movement, projectile behaviour and survivability.
 */
public enum PlayerStatId {
    MOVE_SPEED,          // px/s
    MAX_HEALTH,          // health points (2 HP = 1 heart)
    FIRE_RATE,           // shots per second
    PROJECTILE_SPEED,    // px/s
    PROJECTILE_RANGE,    // seconds of lifetime
    PROJECTILE_DAMAGE,   // HP per projectile hit

    // Survivability and combat breadth (Brotato-like)
    HP_REGEN,            // health per second
    LIFESTEAL,           // % chance to heal on hit
    ARMOR,               // flat damage reduction value
    DODGE,               // % chance to evade damage
    CRIT_CHANCE,         // % crit chance
    CRIT_DAMAGE,         // crit damage multiplier (1.5 = 150%)
    KNOCKBACK,           // knockback force
    HARVESTING,          // extra resources at wave end
    LUCK,                // luck for drops/shops
    PICKUP_RANGE,        // coin pickup radius
    SHOP_DISCOUNT,       // shop discount multiplier (0.1 = 10%)
    PROJECTILE_PIERCE,   // number of enemies a projectile pierces
    PROJECTILE_BOUNCE    // number of projectile bounces
}
