package com.layla.model;

/**
 * Tipos de enemigos disponibles en el juego.
 *
 * <p>Cada tipo suele mapear a un {@link EnemyProfile} (balanceo) y a una lógica/animación
 * específica dentro de {@code Enemy}.</p>
 */
public enum EnemyType {

    /** Enemigo a distancia: se mueve y dispara. */
    SHOOTER,

    /** Enemigo cuerpo a cuerpo: persigue al jugador y busca contacto. */
    MELEE,

    /** Enemigo estacionario: orienta su sprite y dispara en ráfagas. */
    TURRET,

    /** Enemigo pesado: más vida y tamaño; suele ser más lento y resistente. */
    TANK,

    /** Enemigo suicida: acelera para embestir al jugador. */
    KAMIKAZE
}
