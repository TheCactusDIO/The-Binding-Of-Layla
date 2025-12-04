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
    PROJECTILE_DAMAGE    // HP per projectile hit
}
