package com.layla.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Base statistics for the player and their projectiles.
 * Values are expressed in world units (px/s, seconds, etc.).
 */
public final class PlayerStats {

    private final Map<PlayerStatId, Double> baseValues = new EnumMap<>(PlayerStatId.class);

    public PlayerStats() {
        resetDefaults();
    }

    public void resetDefaults() {
        setBase(PlayerStatId.MOVE_SPEED,        160.0);
        setBase(PlayerStatId.MAX_HEALTH,        6.0);
        setBase(PlayerStatId.FIRE_RATE,         3.0);
        setBase(PlayerStatId.PROJECTILE_SPEED,  400.0);
        setBase(PlayerStatId.PROJECTILE_RANGE,  1.0);
        setBase(PlayerStatId.PROJECTILE_DAMAGE, 1.0);
    }

    public double getBase(PlayerStatId statId) {
        Objects.requireNonNull(statId, "statId");
        return baseValues.getOrDefault(statId, 0.0);
    }

    public void setBase(PlayerStatId statId, double value) {
        Objects.requireNonNull(statId, "statId");
        baseValues.put(statId, value);
    }
}
