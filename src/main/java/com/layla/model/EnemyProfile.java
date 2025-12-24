package com.layla.model;

/**
 * Perfil de balanceo para un {@link EnemyType}.
 *
 * <p>Contiene los parámetros base (antes de multiplicadores en runtime) que usan
 * el movimiento, colisiones y disparo del enemigo.</p>
 *
 * <p>Diseño:</p>
 * <ul>
 *   <li>Los campos son {@code public} para facilitar el ajuste rápido del balanceo.</li>
 *   <li>Se construye mediante factorías estáticas ({@link #of(double, double, double, double, double, double, double, double, boolean)}).</li>
 * </ul>
 */
public final class EnemyProfile {

    /** Puntuación por defecto que otorga el enemigo al morir. */
    public static final int DEFAULT_SCORE = 10;

    /** Vida máxima base (antes de multiplicadores). */
    public double baseHp;

    /** Velocidad base de movimiento (antes de multiplicadores). */
    public double speed;

    /** Daño de contacto al colisionar con el jugador (antes de multiplicadores). */
    public double contactDmg;

    /**
     * Cadencia de disparo en disparos por segundo.
     * <p>Usa {@code 0} para desactivar el disparo.</p>
     */
    public double fireRate;

    /**
     * Aleatoriedad del movimiento (porcentaje/escala usada por la IA).
     * <p>{@code 0} = sin variación; valores más altos = movimiento más errático.</p>
     */
    public double jitter;

    /** Velocidad del proyectil (unidades por segundo). */
    public double projSpeed;

    /**
     * Alcance del proyectil (unidades).
     * <p>Normalmente la vida útil se calcula como {@code projRange / projSpeed}.</p>
     */
    public double projRange;

    /** Daño del proyectil (antes de multiplicadores). */
    public double projDamage;

    /**
     * Si es {@code true}, el enemigo se comporta como estacionario (tipo torreta).
     * <p>La lógica de movimiento puede ignorarlo para ciertos tipos si así lo decides.</p>
     */
    public boolean stationary;

    /** Puntuación que otorga este enemigo al morir. */
    public int score = DEFAULT_SCORE;

    private EnemyProfile() {
        // Constructor privado: se instancia con métodos factory.
    }

    /**
     * Crea un perfil con los valores indicados.
     *
     * @param hp vida máxima base
     * @param spd velocidad base
     * @param contact daño de contacto
     * @param fire cadencia (disparos/segundo). {@code 0} = no dispara
     * @param jit aleatoriedad de movimiento
     * @param projectileSpeed velocidad del proyectil
     * @param projectileRange alcance del proyectil
     * @param projectileDamage daño del proyectil
     * @param stat {@code true} si es estacionario
     * @return un {@link EnemyProfile} configurado con los valores dados
     */
    public static EnemyProfile of(
            double hp,
            double spd,
            double contact,
            double fire,
            double jit,
            double projectileSpeed,
            double projectileRange,
            double projectileDamage,
            boolean stat
    ) {
        EnemyProfile p = new EnemyProfile();
        p.baseHp = hp;
        p.speed = spd;
        p.contactDmg = contact;
        p.fireRate = fire;
        p.jitter = jit;
        p.projSpeed = projectileSpeed;
        p.projRange = projectileRange;
        p.projDamage = projectileDamage;
        p.stationary = stat;
        // p.score ya vale DEFAULT_SCORE
        return p;
    }

    /**
     * Variante de {@link #of(double, double, double, double, double, double, double, double, boolean)}
     * que permite indicar la puntuación.
     *
     * @param hp vida máxima base
     * @param spd velocidad base
     * @param contact daño de contacto
     * @param fire cadencia (disparos/segundo). {@code 0} = no dispara
     * @param jit aleatoriedad de movimiento
     * @param projectileSpeed velocidad del proyectil
     * @param projectileRange alcance del proyectil
     * @param projectileDamage daño del proyectil
     * @param stat {@code true} si es estacionario
     * @param score puntuación al morir (si quieres, puedes usar {@link #DEFAULT_SCORE})
     * @return un {@link EnemyProfile} configurado con los valores dados
     */
    public static EnemyProfile of(
            double hp,
            double spd,
            double contact,
            double fire,
            double jit,
            double projectileSpeed,
            double projectileRange,
            double projectileDamage,
            boolean stat,
            int score
    ) {
        EnemyProfile p = of(hp, spd, contact, fire, jit, projectileSpeed, projectileRange, projectileDamage, stat);
        p.score = score;
        return p;
    }
}
