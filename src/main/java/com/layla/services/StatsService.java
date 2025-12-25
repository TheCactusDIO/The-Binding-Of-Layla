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

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;

/**
 * Servicio responsable de gestionar las estadísticas del jugador.
 * <p>
 * Combina:
 * </p>
 * <ul>
 *   <li>Estadísticas base ({@link PlayerStats}).</li>
 *   <li>Modificadores temporales en ejecución ({@link StatModifier}).</li>
 *   <li>Modificadores aportados por objetos obtenidos (items).</li>
 * </ul>
 * <p>
 * También expone una propiedad de versión para que la UI pueda reaccionar cuando
 * cambie el inventario sin necesidad de hacer "polling".
 * </p>
 */
public final class StatsService {

    private final PlayerStats baseStats = new PlayerStats();

    private final Map<PlayerStatId, List<StatModifier>> runtimeModifiers =
            new EnumMap<>(PlayerStatId.class);

    private final List<ItemId> ownedItems = new ArrayList<>();

    /**
     * Contador de versión para notificar a la UI cuando cambia la lista de items.
     * Se incrementa cada vez que se añaden o se limpian items.
     */
    private final ReadOnlyIntegerWrapper ownedItemsVersion =
            new ReadOnlyIntegerWrapper(this, "ownedItemsVersion", 0);

    /**
     * @return propiedad de solo lectura con la versión actual de los items poseídos.
     */
    public ReadOnlyIntegerProperty ownedItemsVersionProperty() {
        return ownedItemsVersion.getReadOnlyProperty();
    }

    /**
     * @return versión actual de items poseídos.
     */
    public int getOwnedItemsVersion() {
        return ownedItemsVersion.get();
    }

    /**
     * Incrementa la versión para provocar una actualización reactiva en la UI.
     */
    private void bumpOwnedItemsVersion() {
        ownedItemsVersion.set(ownedItemsVersion.get() + 1);
    }

    /**
     * @return referencia a las estadísticas base del jugador.
     */
    public PlayerStats getBaseStats() {
        return baseStats;
    }

    /**
     * Obtiene el valor base de un stat (sin modificadores).
     *
     * @param statId identificador del stat
     * @return valor base
     * @throws NullPointerException si statId es null
     */
    public double getBaseStat(PlayerStatId statId) {
        Objects.requireNonNull(statId, "statId");
        return baseStats.getBase(statId);
    }

    /**
     * Establece el valor base de un stat (sin modificadores).
     *
     * @param statId identificador del stat
     * @param value valor a establecer
     * @throws NullPointerException si statId es null
     */
    public void setBaseStat(PlayerStatId statId, double value) {
        Objects.requireNonNull(statId, "statId");
        baseStats.setBase(statId, clamp(statId, value));
    }

    /**
     * Añade un modificador temporal en ejecución.
     *
     * @param modifier modificador a añadir
     * @throws NullPointerException si modifier es null
     */
    public void addModifier(StatModifier modifier) {
        Objects.requireNonNull(modifier, "modifier");
        runtimeModifiers
                .computeIfAbsent(modifier.getStatId(), k -> new ArrayList<>())
                .add(modifier);
    }

    /**
     * Elimina un modificador temporal en ejecución si existe.
     *
     * @param modifier modificador a eliminar (si es null no hace nada)
     */
    public void removeModifier(StatModifier modifier) {
        if (modifier == null) return;

        List<StatModifier> list = runtimeModifiers.get(modifier.getStatId());
        if (list == null) return;

        list.remove(modifier);
        if (list.isEmpty()) {
            runtimeModifiers.remove(modifier.getStatId());
        }
    }

    /**
     * Restaura estadísticas base por defecto y limpia modificadores e items.
     * <p>
     * Mantiene el comportamiento actual: además fija {@link PlayerStatId#NUMERO_DE_PROYECTILES} a 1.0.
     * </p>
     */
    public void resetDefaults() {
        baseStats.resetDefaults();
        baseStats.setBase(PlayerStatId.NUMERO_DE_PROYECTILES, 1.0);

        runtimeModifiers.clear();
        ownedItems.clear();
        bumpOwnedItemsVersion();
    }

    /**
     * Calcula el valor final de un stat:
     * <pre>
     * (base + sumaAditiva) * productoMultiplicativo
     * </pre>
     * Incluye:
     * <ul>
     *   <li>Modificadores temporales en ejecución</li>
     *   <li>Modificadores aportados por items poseídos</li>
     * </ul>
     *
     * @param statId identificador del stat
     * @return valor final del stat (clamp aplicado si procede)
     * @throws NullPointerException si statId es null
     */
    public double getStat(PlayerStatId statId) {
        Objects.requireNonNull(statId, "statId");

        double base = baseStats.getBase(statId);
        double additive = 0.0;
        double multiplicative = 1.0;

        // Modificadores temporales
        List<StatModifier> mods = runtimeModifiers.get(statId);
        if (mods != null) {
            for (StatModifier mod : mods) {
                additive += mod.getAdditive();
                multiplicative *= mod.getMultiplicative();
            }
        }

        // Modificadores por items
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

    /**
     * Añade un item al inventario (stackeado, permite duplicados) si existe en el registro.
     *
     * @param itemId id del item
     * @return true si se añadió; false si itemId es null o no existe definición
     */
    public boolean grantItem(ItemId itemId) {
        if (itemId == null) return false;
        if (ItemRegistry.getDefinition(itemId) == null) return false;

        ownedItems.add(itemId);
        bumpOwnedItemsVersion();
        return true;
    }

    /**
     * @param itemId id del item
     * @return true si el item está en la lista (al menos una vez)
     */
    public boolean hasItem(ItemId itemId) {
        return itemId != null && ownedItems.contains(itemId);
    }

    /**
     * Elimina todos los items poseídos.
     */
    public void clearItems() {
        if (!ownedItems.isEmpty()) {
            ownedItems.clear();
            bumpOwnedItemsVersion();
        }
    }

    /**
     * @return conjunto inmodificable de items únicos (manteniendo orden de inserción).
     */
    public Set<ItemId> getOwnedItems() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(ownedItems));
    }

    /**
     * @return lista inmodificable con los items tal y como están "stackeados" (con duplicados).
     */
    public List<ItemId> getOwnedItemsStacked() {
        return Collections.unmodifiableList(ownedItems);
    }

    /**
     * Aplica límites mínimos a stats que no deberían ser negativos.
     *
     * @param statId stat a limitar
     * @param value valor de entrada
     * @return valor limitado
     */
    private double clamp(PlayerStatId statId, double value) {
        return switch (statId) {
            case VELOCIDAD_DE_MOVIMIENTO, CADENCIA, VELOCIDAD_DEL_PROYECTIL,
                 RANGO_DEL_PROYECTIL, DAÑO_DEL_PROYECTIL, VIDA_MAXIMA,
                 NUMERO_DE_PROYECTILES, PENETRACIÓN_DEL_PROYECTIL, REBOTE_DEL_PROYECTIL, AUTOAPUNTADO_DEL_PROYECTIL
                 -> Math.max(0.0, value);
            default -> value;
        };
    }

    /** @return velocidad de movimiento final. */
    public double getMoveSpeed()        { return getStat(PlayerStatId.VELOCIDAD_DE_MOVIMIENTO); }
    /** @return vida máxima final. */
    public double getMaxHealth()        { return getStat(PlayerStatId.VIDA_MAXIMA); }
    /** @return cadencia final (disparos/segundo). */
    public double getFireRate()         { return getStat(PlayerStatId.CADENCIA); }
    /** @return velocidad del proyectil final. */
    public double getProjectileSpeed()  { return getStat(PlayerStatId.VELOCIDAD_DEL_PROYECTIL); }
    /** @return alcance/vida del proyectil final. */
    public double getProjectileRange()  { return getStat(PlayerStatId.RANGO_DEL_PROYECTIL); }
    /** @return daño del proyectil final. */
    public double getProjectileDamage() { return getStat(PlayerStatId.DAÑO_DEL_PROYECTIL); }
    /** @return penetración del proyectil final. */
    public double getProjectilePierce() { return getStat(PlayerStatId.PENETRACIÓN_DEL_PROYECTIL); }
    /** @return rebotes del proyectil final. */
    public double getProjectileBounce() { return getStat(PlayerStatId.REBOTE_DEL_PROYECTIL); }
    /** @return número de proyectiles por disparo final. */
    public double getProjectileCount()  { return getStat(PlayerStatId.NUMERO_DE_PROYECTILES); }
    /** @return capacidad de homing final. */
    public double getProjectileHoming() { return getStat(PlayerStatId.AUTOAPUNTADO_DEL_PROYECTIL); }
}
