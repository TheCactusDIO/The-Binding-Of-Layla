package com.layla.model;

/**
 * Identificadores de estadísticas del jugador que pueden modificarse en tiempo de ejecución.
 *
 * <p>Estas estadísticas afectan al movimiento, el disparo (proyectiles) y la supervivencia.</p>
 *
 * <p><strong>Nota:</strong> las unidades exactas dependen de la implementación del sistema de combate
 * (por ejemplo, si {@code PROJECTILE_RANGE} se interpreta como segundos de vida o como distancia).</p>
 */
public enum PlayerStatId {

    /** Velocidad de movimiento del jugador (normalmente en px/s). */
    MOVE_SPEED,

    /** Vida máxima del jugador (por ejemplo, HP; si usas corazones, define la conversión en tu HUD). */
    MAX_HEALTH,

    /** Cadencia de disparo (disparos por segundo). */
    FIRE_RATE,

    /** Velocidad del proyectil (normalmente en px/s). */
    PROJECTILE_SPEED,

    /** Alcance del proyectil (segundos de vida o distancia efectiva, según tu lógica). */
    PROJECTILE_RANGE,

    /** Daño por impacto del proyectil (HP por golpe). */
    PROJECTILE_DAMAGE,

    /** Número de proyectiles por disparo. */
    PROJECTILE_COUNT,

    /** Número de enemigos que atraviesa un proyectil antes de desaparecer. */
    PROJECTILE_PIERCE,

    /** Número de rebotes que puede realizar un proyectil. */
    PROJECTILE_BOUNCE,

    /** Capacidad de “perseguir” enemigos (proyectil con guiado). */
    PROJECTILE_HOMING
}
