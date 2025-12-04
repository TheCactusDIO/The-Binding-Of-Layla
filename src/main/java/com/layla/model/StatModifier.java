package com.layla.model;

import java.util.Objects;

/**
 * Immutable value that represents a modification to a single player stat.
 * The final stat is computed as (base + additiveSum) * productOfMultipliers.
 */
public final class StatModifier {

    private final PlayerStatId statId;
    private final double additive;
    private final double multiplicative;

    private StatModifier(PlayerStatId statId, double additive, double multiplicative) {
        this.statId = Objects.requireNonNull(statId, "statId");
        this.additive = additive;
        this.multiplicative = multiplicative;
    }

    /** Creates a modifier that adds to the base value. */
    public static StatModifier additive(PlayerStatId statId, double amount) {
        return new StatModifier(statId, amount, 1.0);
    }

    /** Creates a modifier that multiplies the (base + additive) result. */
    public static StatModifier multiplicative(PlayerStatId statId, double factor) {
        return new StatModifier(statId, 0.0, factor);
    }

    /** Factory for combined additive/multiplicative modifiers. */
    public static StatModifier of(PlayerStatId statId, double additive, double multiplicative) {
        return new StatModifier(statId, additive, multiplicative);
    }

    public PlayerStatId getStatId() {
        return statId;
    }

    public double getAdditive() {
        return additive;
    }

    public double getMultiplicative() {
        return multiplicative;
    }
}
