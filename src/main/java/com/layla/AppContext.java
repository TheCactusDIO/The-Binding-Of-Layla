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

        // Enemy defaults
        public double enemyBaseHp   = 6.0;
        public double enemySpeedAvg = 130.0;
        public double enemyScoreK   = 5.0;

        // Enemy projectile stats (configurables)
        public double enemyProjSpeed  = 180.0; // px/s
        public double enemyProjRange  = 2.2;   // segundos de vida
        public double enemyProjDamage = 1.0;   // HP
        public double enemyContactDamage = 1.0; // daño por contacto (en HP)

        // Micro anti-tunneling
        public double projectileMicroStepPx = 12.0;
        public int    projectileMaxSubSteps = 6;

        public void resetDefaults() {
            this.startHp = 6.0;
            this.maxHp   = 6.0;

            this.enemyBaseHp   = 6.0;
            this.enemySpeedAvg = 130.0;
            this.enemyScoreK   = 5.0;

            this.enemyProjSpeed  = 180.0;
            this.enemyProjRange  = 2.2;
            this.enemyProjDamage = 1.0;
            this.enemyContactDamage = 1.0;

            this.projectileMicroStepPx = 12.0;
            this.projectileMaxSubSteps = 6;
        }
    }
}
