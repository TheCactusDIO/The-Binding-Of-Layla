package com.layla.services;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.layla.model.PlayerStats;
import com.layla.model.StatModifier;
import com.layla.model.StatType;

/**
 * Central access point for player statistics and their runtime modifiers.
 */
public final class StatsService {

    private final PlayerStats baseStats = new PlayerStats();
    private final Map<StatType, List<StatModifier>> modifiers = new EnumMap<>(StatType.class);

    public PlayerStats getBaseStats() {
        return baseStats;
    }

    /** Adds a modifier to the system. */
    public void addModifier(StatModifier modifier) {
        Objects.requireNonNull(modifier, "modifier");
        modifiers.computeIfAbsent(modifier.getType(), k -> new ArrayList<>()).add(modifier);
    }

    /** Removes a specific modifier instance. */
    public void removeModifier(StatModifier modifier) {
        if (modifier == null) return;
        List<StatModifier> list = modifiers.get(modifier.getType());
        if (list != null) {
            list.remove(modifier);
            if (list.isEmpty()) {
                modifiers.remove(modifier.getType());
            }
        }
    }

    /** Removes all modifiers with a given ID. Returns true if any were removed. */
    public boolean removeModifierById(String id) {
        if (id == null) return false;
        boolean removed = false;

        for (var entry : new ArrayList<>(modifiers.entrySet())) {
            List<StatModifier> list = entry.getValue();
            if (list == null) continue;

            list.removeIf(mod -> id.equals(mod.getId()));
            if (list.isEmpty()) {
                modifiers.remove(entry.getKey());
            }
        }

        // Return true if any modifier with that ID was found and removed
        return removed;
    }

    /** Returns the current effective value for a given stat. */
    public double getStat(StatType type) {
        Objects.requireNonNull(type, "type");
        double base = baseStats.getBase(type);
        List<StatModifier> list = modifiers.get(type);
        if (list == null || list.isEmpty()) {
            return clampStat(type, base);
        }

        double additive = 0.0;
        double multiplier = 1.0;

        for (StatModifier mod : list) {
            if (!mod.isEnabled()) continue; // skip disabled modifiers
            additive += mod.getAdditive();
            multiplier *= mod.getMultiplier();
        }

        double value = (base + additive) * multiplier;
        return clampStat(type, value);
    }

    private double clampStat(StatType type, double value) {
        return switch (type) {
            case MOVE_SPEED, PROJECTILE_SPEED, RANGE_PIXELS, DAMAGE -> Math.max(0.0, value);
            case FIRE_COOLDOWN -> Math.max(0.0, value);
        };
    }

    // ---- Typed getters for convenience ----
    public double getMoveSpeed()        { return getStat(StatType.MOVE_SPEED); }
    public double getFireCooldown()     { return getStat(StatType.FIRE_COOLDOWN); }
    public double getProjectileSpeed()  { return getStat(StatType.PROJECTILE_SPEED); }
    public double getRangePixels()      { return getStat(StatType.RANGE_PIXELS); }
    public double getDamage()           { return getStat(StatType.DAMAGE); }
}
