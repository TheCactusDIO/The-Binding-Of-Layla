package com.layla.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Base statistics for the player and their projectiles.
 * Values are expressed in world units (px/s, seconds, etc.).
 */
public final class PlayerStats {

    private final Map<StatType, Double> baseValues = new EnumMap<>(StatType.class);

    public PlayerStats() {
        resetDefaults();
    }

    public void resetDefaults() {
        setBase(StatType.MOVE_SPEED,        160.0);
        setBase(StatType.FIRE_RATE,         3.0);
        setBase(StatType.PROJECTILE_SPEED,  400.0);
        setBase(StatType.PROJECTILE_RANGE,  1.0);
        setBase(StatType.PROJECTILE_DAMAGE, 1.0);
    }

    public double getBase(StatType type) {
        Objects.requireNonNull(type, "type");
        return baseValues.getOrDefault(type, 0.0);
    }

    public void setBase(StatType type, double value) {
        Objects.requireNonNull(type, "type");
        baseValues.put(type, value);
    }
}
