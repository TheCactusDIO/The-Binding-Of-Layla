package com.layla.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
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

public final class StatsService {

    private final PlayerStats baseStats = new PlayerStats();
    private final Map<PlayerStatId, List<StatModifier>> runtimeModifiers =
            new EnumMap<>(PlayerStatId.class);

    private final List<ItemId> ownedItems = new ArrayList<>();

    public PlayerStats getBaseStats() { return baseStats; }

    public double getBaseStat(PlayerStatId statId) {
        Objects.requireNonNull(statId, "statId");
        return baseStats.getBase(statId);
    }

    public void setBaseStat(PlayerStatId statId, double value) {
        Objects.requireNonNull(statId, "statId");
        baseStats.setBase(statId, clamp(statId, value));
    }

    public void addModifier(StatModifier modifier) {
        Objects.requireNonNull(modifier, "modifier");
        runtimeModifiers
                .computeIfAbsent(modifier.getStatId(), k -> new ArrayList<>())
                .add(modifier);
    }

    public void removeModifier(StatModifier modifier) {
        if (modifier == null) return;
        List<StatModifier> list = runtimeModifiers.get(modifier.getStatId());
        if (list == null) return;
        list.remove(modifier);
        if (list.isEmpty()) {
            runtimeModifiers.remove(modifier.getStatId());
        }
    }

    public void resetDefaults() {
        baseStats.resetDefaults();
        baseStats.setBase(PlayerStatId.PROJECTILE_COUNT, 1.0);
        runtimeModifiers.clear();
        ownedItems.clear();
    }

    public double getStat(PlayerStatId statId) {
        Objects.requireNonNull(statId, "statId");

        double base = baseStats.getBase(statId);
        double additive = 0.0;
        double multiplicative = 1.0;

        List<StatModifier> mods = runtimeModifiers.get(statId);
        if (mods != null) {
            for (StatModifier mod : mods) {
                additive += mod.getAdditive();
                multiplicative *= mod.getMultiplicative();
            }
        }

        for (ItemId item : ownedItems) {
            ItemDefinition def = ItemRegistry.getDefinition(item);
            if (def == null) continue;

            for (StatModifier mod : def.getModifiers()) {
                if (mod.getStatId() == statId) {
                    additive += mod.getAdditive();
                    multiplicative *= mod.getMultiplicative();
                }
            }
        }

        return clamp(statId, (base + additive) * multiplicative);
    }

    public boolean grantItem(ItemId itemId) {
        if (itemId == null) return false;
        if (ItemRegistry.getDefinition(itemId) == null) return false;
        ownedItems.add(itemId);
        return true;
    }

    public boolean hasItem(ItemId itemId) { return itemId != null && ownedItems.contains(itemId); }
    public void clearItems() { ownedItems.clear(); }
    public Set<ItemId> getOwnedItems() { return Collections.unmodifiableSet(new LinkedHashSet<>(ownedItems)); }
    public List<ItemId> getOwnedItemsStacked() { return Collections.unmodifiableList(ownedItems); }

    private double clamp(PlayerStatId statId, double value) {
        return switch (statId) {
            case MOVE_SPEED, FIRE_RATE, PROJECTILE_SPEED,
                 PROJECTILE_RANGE, PROJECTILE_DAMAGE, MAX_HEALTH,
                 PICKUP_RANGE, CRIT_DAMAGE, PROJECTILE_COUNT,
                 PROJECTILE_PIERCE, PROJECTILE_BOUNCE, PROJECTILE_HOMING
                 -> Math.max(0.0, value);
            default -> value;
        };
    }

    public double getMoveSpeed()        { return getStat(PlayerStatId.MOVE_SPEED); }
    public double getMaxHealth()        { return getStat(PlayerStatId.MAX_HEALTH); }
    public double getFireRate()         { return getStat(PlayerStatId.FIRE_RATE); }
    public double getProjectileSpeed()  { return getStat(PlayerStatId.PROJECTILE_SPEED); }
    public double getProjectileRange()  { return getStat(PlayerStatId.PROJECTILE_RANGE); }
    public double getProjectileDamage() { return getStat(PlayerStatId.PROJECTILE_DAMAGE); }
    public double getHpRegen()          { return getStat(PlayerStatId.HP_REGEN); }
    public double getLifesteal()        { return getStat(PlayerStatId.LIFESTEAL); }
    public double getArmor()            { return getStat(PlayerStatId.ARMOR); }
    public double getDodge()            { return getStat(PlayerStatId.DODGE); }
    public double getCritChance()       { return getStat(PlayerStatId.CRIT_CHANCE); }
    public double getCritDamage()       { return getStat(PlayerStatId.CRIT_DAMAGE); }
    public double getKnockback()        { return getStat(PlayerStatId.KNOCKBACK); }
    public double getHarvesting()       { return getStat(PlayerStatId.HARVESTING); }
    public double getLuck()             { return getStat(PlayerStatId.LUCK); }
    public double getPickupRange()      { return getStat(PlayerStatId.PICKUP_RANGE); }
    public double getShopDiscount()     { return getStat(PlayerStatId.SHOP_DISCOUNT); }
    public double getProjectilePierce() { return getStat(PlayerStatId.PROJECTILE_PIERCE); }
    public double getProjectileBounce() { return getStat(PlayerStatId.PROJECTILE_BOUNCE); }
    public double getProjectileCount()  { return getStat(PlayerStatId.PROJECTILE_COUNT); }
    public double getProjectileHoming() { return getStat(PlayerStatId.PROJECTILE_HOMING); }
}
