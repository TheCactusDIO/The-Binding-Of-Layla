package com.layla.model;

public final class EnemyProfile {
    public double baseHp;
    public double speed;
    public double contactDmg;
    public double fireRate;   // shots per second; 0 = no shooting
    public double jitter;     // movement randomness
    public double projSpeed;
    public double projRange;
    public double projDamage;
    public boolean stationary; // turret-like behavior when true

    // NUEVO: puntuación que da este enemigo al morir
    public int score = 10;     // valor por defecto

    public static EnemyProfile of(double hp, double spd, double contact,
                                  double fire, double jit,
                                  double pSpd, double pRange, double pDmg,
                                  boolean stat) {
        EnemyProfile p = new EnemyProfile();
        p.baseHp = hp;
        p.speed = spd;
        p.contactDmg = contact;
        p.fireRate = fire;
        p.jitter = jit;
        p.projSpeed = pSpd;
        p.projRange = pRange;
        p.projDamage = pDmg;
        p.stationary = stat;

        // Valor por defecto para score cuando se crean perfiles desde código
        p.score = 10;

        return p;
    }
}
