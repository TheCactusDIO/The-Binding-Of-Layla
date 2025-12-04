package com.layla.items;

import java.util.List;
import java.util.Objects;

import com.layla.model.StatModifier;

/** Immutable metadata that describes a passive item. */
public final class ItemDefinition {

    private final ItemId id;
    private final String name;
    private final String description;
    private final ItemPoolType poolType;
    private final List<StatModifier> modifiers;
    //private final boolean unique;

    public ItemDefinition(ItemId id,
                          String name,
                          String description,
                          ItemPoolType poolType,
                          List<StatModifier> modifiers,
                          boolean unique) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.poolType = Objects.requireNonNull(poolType, "poolType");
        this.modifiers = List.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        //this.unique = unique;
    }

    public ItemDefinition(ItemId id,
                          String name,
                          String description,
                          ItemPoolType poolType,
                          List<StatModifier> modifiers) {
        this(id, name, description, poolType, modifiers, true);
    }

    public ItemId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ItemPoolType getPoolType() {
        return poolType;
    }

    public List<StatModifier> getModifiers() {
        return modifiers;
    }

    // public boolean isUnique() {
    //     return unique;
    // }
}
