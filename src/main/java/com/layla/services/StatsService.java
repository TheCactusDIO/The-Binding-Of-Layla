package com.layla.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.layla.items.ItemDefinition;
import com.layla.items.ItemId;
import com.layla.items.ItemRegistry;
import com.layla.model.PlayerStatId;
import com.layla.model.PlayerStats;
import com.layla.model.StatModifier;

/**
 * Central access point for player statistics, runtime modifiers and passive items.
 */
public final class StatsService {

    private final PlayerStats baseStats = new PlayerStats();
    private final Map<PlayerStatId, List<StatModifier>> runtimeModifiers = new EnumMap<>(PlayerStatId.class);
    private final Set<ItemId> ownedItems = EnumSet.noneOf(ItemId.class);

    public PlayerStats getBaseStats() {
        return baseStats;
    }

    public double getBaseStat(PlayerStatId statId) {
        Objects.requireNonNull(statId, "statId");
        return baseStats.getBase(statId);
    }

    /** Updates the base value for a stat (before modifiers and items). */
    public void setBaseStat(PlayerStatId statId, double value) {
        Objects.requireNonNull(statId, "statId");
        baseStats.setBase(statId, clampStat(statId, value));
    }

    /** Adds a temporary/runtime modifier (e.g. buffs, debuffs). */
    public void addModifier(StatModifier modifier) {
        Objects.requireNonNull(modifier, "modifier");
        runtimeModifiers
            .computeIfAbsent(modifier.getStatId(), k -> new ArrayList<>())
            .add(modifier);
    }

    /** Removes a previously registered modifier by reference. */
    public void removeModifier(StatModifier modifier) {
        if (modifier == null) return;
        List<StatModifier> list = runtimeModifiers.get(modifier.getStatId());
        if (list == null) return;
        list.remove(modifier);
        if (list.isEmpty()) {
            runtimeModifiers.remove(modifier.getStatId());
        }
    }

    /** Clears all state and owned items, going back to defaults. */
    public void resetDefaults() {
        baseStats.resetDefaults();
        runtimeModifiers.clear();
        ownedItems.clear();
    }

    /** Returns the final value of a stat after applying modifiers and passive items. */
    public double getStat(PlayerStatId statId) {
        Objects.requireNonNull(statId, "statId");
        double base = baseStats.getBase(statId);
        double additive = 0.0;
        double multiplicative = 1.0;

        List<StatModifier> list = runtimeModifiers.get(statId);
        if (list != null) {
            for (StatModifier mod : list) {
                additive += mod.getAdditive();
                multiplicative *= mod.getMultiplicative();
            }
        }

        for (ItemId itemId : ownedItems) {
            ItemDefinition definition = ItemRegistry.getDefinition(itemId);
            if (definition == null) continue;
            for (StatModifier mod : definition.getModifiers()) {
                if (mod.getStatId() == statId) {
                    additive += mod.getAdditive();
                    multiplicative *= mod.getMultiplicative();
                }
            }
        }

        return clampStat(statId, (base + additive) * multiplicative);
    }

    /**
     * Grants a passive item to the player.
     * @return true if the item was added to the inventory.
     */
    public boolean grantItem(ItemId itemId) {
        if (itemId == null) return false;
        ItemDefinition definition = ItemRegistry.getDefinition(itemId);
        if (definition == null) return false;

        // if (definition.isUnique() && ownedItems.contains(itemId)) {
        //     return false;
        // }

        ownedItems.add(itemId);
        return true;
    }

    public boolean hasItem(ItemId itemId) {
        if (itemId == null) return false;
        return ownedItems.contains(itemId);
    }

    public void clearItems() {
        ownedItems.clear();
    }

    public Set<ItemId> getOwnedItems() {
        return Collections.unmodifiableSet(ownedItems);
    }

    private double clampStat(PlayerStatId statId, double value) {
        return switch (statId) {
            case MOVE_SPEED, FIRE_RATE, PROJECTILE_SPEED, PROJECTILE_RANGE,
                 PROJECTILE_DAMAGE -> Math.max(0.0, value);
            case MAX_HEALTH -> Math.max(0.0, value);
        };
    }

    // Typed getters for convenience
    public double getMoveSpeed()        { return getStat(PlayerStatId.MOVE_SPEED); }
    public double getMaxHealth()        { return getStat(PlayerStatId.MAX_HEALTH); }
    public double getFireRate()         { return getStat(PlayerStatId.FIRE_RATE); }
    public double getProjectileSpeed()  { return getStat(PlayerStatId.PROJECTILE_SPEED); }
    public double getProjectileRange()  { return getStat(PlayerStatId.PROJECTILE_RANGE); }
    public double getProjectileDamage() { return getStat(PlayerStatId.PROJECTILE_DAMAGE); }
}
