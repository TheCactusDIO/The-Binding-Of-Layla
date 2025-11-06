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
        setBase(StatType.MOVE_SPEED, 320.0);
        setBase(StatType.FIRE_COOLDOWN, 0.25);
        setBase(StatType.PROJECTILE_SPEED, 520.0);
        setBase(StatType.RANGE_PIXELS, 624.0); // ~1.2s lifetime at 520px/s
        setBase(StatType.DAMAGE, 3.5);
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
