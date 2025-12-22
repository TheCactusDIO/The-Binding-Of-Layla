package com.layla.items;

import java.util.List;
import java.util.Objects;

import com.layla.model.StatModifier;

/**
 * Metadatos inmutables que describen un ítem pasivo.
 *
 * <p>Incluye su identificador, nombre/descripcion, el pool al que pertenece y los modificadores
 * de stats que aplica. Opcionalmente puede requerir un logro para estar disponible.</p>
 */
public final class ItemDefinition {

    private final ItemId id;
    private final String name;
    private final String description;
    private final ItemPoolType poolType;
    private final List<StatModifier> modifiers;

    /**
     * ID del logro necesario para desbloquear este ítem.
     * <p>Si es {@code null}, el ítem está desbloqueado por defecto.</p>
     */
    private final String requiredAchievementId;

    /**
     * Constructor completo.
     *
     * @param id identificador del ítem (no null)
     * @param name nombre visible (no null)
     * @param description descripción visible (no null)
     * @param poolType pool al que pertenece el ítem (no null)
     * @param modifiers lista de modificadores que aplica (no null; se copia a lista inmutable)
     * @param requiredAchievementId id del logro requerido, o {@code null} si no requiere logro
     */
    public ItemDefinition(
            ItemId id,
            String name,
            String description,
            ItemPoolType poolType,
            List<StatModifier> modifiers,
            String requiredAchievementId
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.poolType = Objects.requireNonNull(poolType, "poolType");
        this.modifiers = List.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        this.requiredAchievementId = requiredAchievementId;
    }

    /**
     * Constructor de conveniencia para ítems sin requisito de logro.
     *
     * @param id identificador del ítem (no null)
     * @param name nombre visible (no null)
     * @param description descripción visible (no null)
     * @param poolType pool al que pertenece el ítem (no null)
     * @param modifiers lista de modificadores que aplica (no null; se copia a lista inmutable)
     */
    public ItemDefinition(
            ItemId id,
            String name,
            String description,
            ItemPoolType poolType,
            List<StatModifier> modifiers
    ) {
        this(id, name, description, poolType, modifiers, null);
    }

    /** @return el identificador del ítem. */
    public ItemId getId() {
        return id;
    }

    /** @return el nombre visible del ítem. */
    public String getName() {
        return name;
    }

    /** @return la descripción visible del ítem. */
    public String getDescription() {
        return description;
    }

    /** @return el pool al que pertenece el ítem. */
    public ItemPoolType getPoolType() {
        return poolType;
    }

    /** @return lista inmutable de modificadores del ítem. */
    public List<StatModifier> getModifiers() {
        return modifiers;
    }

    /**
     * @return el id del logro requerido, o {@code null} si no requiere logro.
     */
    public String getRequiredAchievementId() {
        return requiredAchievementId;
    }

    /**
     * @return {@code true} si el ítem está desbloqueado por defecto (no requiere logro).
     */
    public boolean isUnlockedByDefault() {
        return requiredAchievementId == null;
    }
}
