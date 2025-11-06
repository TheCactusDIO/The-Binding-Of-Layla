package com.layla.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a modifier applied on top of a base statistic.
 * Supports additive and multiplicative adjustments.
 * Each modifier affects a single StatType and can be toggled on/off.
 */
public final class StatModifier {

    private final String id;          // Unique identifier
    private final StatType type;      // Stat affected
    private final double additive;    // +X
    private final double multiplier;  // ×factor (1.10 = +10%)
    private boolean enabled = true;   // can be temporarily disabled

    public StatModifier(String id, StatType type, double additive, double multiplier) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.additive = additive;
        this.multiplier = multiplier;
    }

    /** Generates an ID automatically. */
    public StatModifier(StatType type, double additive, double multiplier) {
        this(UUID.randomUUID().toString(), type, additive, multiplier);
    }

    /** Create a purely additive modifier (+X). */
    public static StatModifier additive(StatType type, double amount) {
        return new StatModifier(type, amount, 1.0);
    }

    /** Create a purely multiplicative modifier (×factor, e.g. 1.15 = +15%). */
    public static StatModifier multiplier(StatType type, double multiplier) {
        return new StatModifier(type, 0.0, multiplier);
    }

    public String getId() {
        return id;
    }

    public StatType getType() {
        return type;
    }

    public double getAdditive() {
        return additive;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
