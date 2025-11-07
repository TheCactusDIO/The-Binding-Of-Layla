package com.layla.model;

/** Supported statistics that drive player movement, shooting and projectile behaviour. */
public enum StatType {
    MOVE_SPEED,          // px/s
    FIRE_RATE,           // shots per second (inverse of cooldown)
    PROJECTILE_SPEED,    // px/s
    PROJECTILE_RANGE,    // seconds of lifetime
    PROJECTILE_DAMAGE    // damage per projectile
}
