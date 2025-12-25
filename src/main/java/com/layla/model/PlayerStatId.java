package com.layla.model;

/**
 * Identificadores de estadísticas del jugador que pueden modificarse en tiempo de ejecución.
 *
 * <p>Estas estadísticas afectan al movimiento, el disparo (proyectiles) y la supervivencia.</p>
 *
 * <p><strong>Nota:</strong> las unidades exactas dependen de la implementación del sistema de combate
 * (por ejemplo, si {@code RANGO_DEL_PROYECTIL} se interpreta como segundos de vida o como distancia).</p>
 */
public enum PlayerStatId {

    /** Velocidad de movimiento del jugador (normalmente en px/s). */
    VELOCIDAD_DE_MOVIMIENTO,

    /** Vida máxima del jugador (por ejemplo, HP; si usas corazones, define la conversión en tu HUD). */
    VIDA_MAXIMA,

    /** Cadencia de disparo (disparos por segundo). */
    CADENCIA,

    /** Velocidad del proyectil (normalmente en px/s). */
    VELOCIDAD_DEL_PROYECTIL,

    /** Alcance del proyectil (segundos de vida o distancia efectiva, según tu lógica). */
    RANGO_DEL_PROYECTIL,

    /** Daño por impacto del proyectil (HP por golpe). */
    DAÑO_DEL_PROYECTIL,

    /** Número de proyectiles por disparo. */
    NUMERO_DE_PROYECTILES,

    /** Número de enemigos que atraviesa un proyectil antes de desaparecer. */
    PENETRACIÓN_DEL_PROYECTIL,

    /** Número de rebotes que puede realizar un proyectil. */
    REBOTE_DEL_PROYECTIL,

    /** Capacidad de “perseguir” enemigos (proyectil con guiado). */
    AUTOAPUNTADO_DEL_PROYECTIL
}
