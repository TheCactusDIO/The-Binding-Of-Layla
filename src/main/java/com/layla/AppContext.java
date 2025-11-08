package com.layla;

import com.layla.services.StatsService;

public final class AppContext {
    private static final StatsService STATS = new StatsService();
    private static final GameBalance BALANCE = new GameBalance();

    private AppContext() {}

    public static StatsService stats() { return STATS; }
    public static GameBalance balance() { return BALANCE; }

    // ==== Balance global editable ====
    public static final class GameBalance {
        // Player
        public double startHp = 6.0;
        public double maxHp   = 6.0;

        // Enemy defaults (movimiento / puntuación)
        public double enemyBaseHp   = 6.0;
        public double enemySpeedAvg = 130.0;
        public double enemyScoreK   = 5.0;

        // Enemy projectiles (¡nuevo!)
        public double enemyFireRate       = 1.0;  // disparos/s por enemigo estándar
        public double enemyProjSpeed      = 220.0;
        public double enemyProjRange      = 1.2;  // segundos de vida
        public double enemyProjDamage     = 1.0;

        // Micro anti-tunneling proyectiles
        public double projectileMicroStepPx = 12.0;
        public int    projectileMaxSubSteps = 6;

        // Colisiones lógicas (fáciles de cambiar)
        public boolean playerShotsHitEnemies = false; // ← tu preferencia actual
        public boolean playerShotsHitPlayer  = false; // nunca
        public boolean enemyShotsHitPlayer   = true;  // sí

        public void resetDefaults() {
            startHp = 6.0;
            maxHp   = 6.0;
            enemyBaseHp   = 6.0;
            enemySpeedAvg = 130.0;
            enemyScoreK   = 5.0;

            enemyFireRate   = 1.0;
            enemyProjSpeed  = 220.0;
            enemyProjRange  = 1.2;
            enemyProjDamage = 1.0;

            projectileMicroStepPx = 12.0;
            projectileMaxSubSteps = 6;

            playerShotsHitEnemies = false;
            playerShotsHitPlayer  = false;
            enemyShotsHitPlayer   = true;
        }
    }
}
