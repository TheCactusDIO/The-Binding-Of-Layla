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
    PROJECTILE_COUNT,    // NUEVO: Number of projectiles per shot

    // Survivability and combat breadth (Brotato-like)
    PROJECTILE_PIERCE,   // number of enemies a projectile pierces
    PROJECTILE_BOUNCE,   // number of projectile bounces
    PROJECTILE_HOMING    // Capacidad de perseguir enemigos
}
