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

    // NUEVO: ID del logro necesario para desbloquear este ítem (null = desbloqueado por defecto)
    private final String requiredAchievementId;

    public ItemDefinition(ItemId id,
                          String name,
                          String description,
                          ItemPoolType poolType,
                          List<StatModifier> modifiers,
                          String requiredAchievementId) { // Constructor completo
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.poolType = Objects.requireNonNull(poolType, "poolType");
        this.modifiers = List.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        this.requiredAchievementId = requiredAchievementId;
    }

    // Constructor de conveniencia (para ítems sin requisitos)
    public ItemDefinition(ItemId id,
                          String name,
                          String description,
                          ItemPoolType poolType,
                          List<StatModifier> modifiers) {
        this(id, name, description, poolType, modifiers, null);
    }

    public ItemId getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ItemPoolType getPoolType() { return poolType; }
    public List<StatModifier> getModifiers() { return modifiers; }

    // NUEVO Getter
    public String getRequiredAchievementId() { return requiredAchievementId; }

    public boolean isUnlockedByDefault() { return requiredAchievementId == null; }
}
