package com.layla;

import com.layla.services.StatsService;

public final class AppContext {

    // ===== STATS (global) =====
    private static final StatsService STATS = new StatsService();
    public static StatsService stats() { return STATS; }

    // ===== GAME BALANCE (global) =====
    public static final class GameBalance {
        // Player
        public double startHp = 6.0;
        public double maxHp   = 6.0;

        // Enemy defaults
        public double enemyBaseHp   = 6.0;   // vida base al spawnear
        public double enemySpeedAvg = 120.0; // velocidad media
        public double enemyScoreK   = 5.0;   // K en bonus: HP^0.2 * K
    }
    private static final GameBalance BALANCE = new GameBalance();
    public static GameBalance balance() { return BALANCE; }

    private AppContext() {}
}
