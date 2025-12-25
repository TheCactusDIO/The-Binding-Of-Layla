package com.layla.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Estadísticas base del jugador y de sus proyectiles.
 * <p>
 * Estos valores son la “base” sobre la que luego se aplican modificadores (items y/o runtime).
 * Los valores se expresan en unidades del mundo (px/s, segundos, etc.).
 * </p>
 */
public final class PlayerStats {

    private final Map<PlayerStatId, Double> baseValues = new EnumMap<>(PlayerStatId.class);

    /**
     * Crea las estadísticas base con sus valores por defecto.
     */
    public PlayerStats() {
        resetDefaults();
    }

    /**
     * Restaura los valores base por defecto.
     * <p>
     * Importante: este método <b>no</b> fija {@link PlayerStatId#NUMERO_DE_PROYECTILES}; eso lo estás
     * haciendo desde {@code StatsService.resetDefaults()} (como en tu código).
     * </p>
     */
    public void resetDefaults() {
        setBase(PlayerStatId.VELOCIDAD_DE_MOVIMIENTO, 300.0);
        setBase(PlayerStatId.VIDA_MAXIMA, 6.0);
        setBase(PlayerStatId.CADENCIA, 15.0);
        setBase(PlayerStatId.VELOCIDAD_DEL_PROYECTIL, 400.0);
        setBase(PlayerStatId.RANGO_DEL_PROYECTIL, 10.0);
        setBase(PlayerStatId.DAÑO_DEL_PROYECTIL, 3.0);

        // Estadísticas relacionadas con el comportamiento del proyectil.
        setBase(PlayerStatId.PENETRACIÓN_DEL_PROYECTIL, 0.0);
        setBase(PlayerStatId.REBOTE_DEL_PROYECTIL, 0.0);
    }

    /**
     * Obtiene el valor base de un stat.
     *
     * @param statId identificador del stat
     * @return valor base (0.0 si no existe en el mapa)
     * @throws NullPointerException si statId es null
     */
    public double getBase(PlayerStatId statId) {
        Objects.requireNonNull(statId, "statId");
        return baseValues.getOrDefault(statId, 0.0);
    }

    /**
     * Establece el valor base de un stat.
     *
     * @param statId identificador del stat
     * @param value valor a guardar
     * @throws NullPointerException si statId es null
     */
    public void setBase(PlayerStatId statId, double value) {
        Objects.requireNonNull(statId, "statId");
        baseValues.put(statId, value);
    }
}
