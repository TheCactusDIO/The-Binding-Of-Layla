package com.layla;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.layla.db.DatabaseService;
import com.layla.model.EnemyProfile;
import com.layla.model.EnemyType;
import com.layla.services.AchievementService;
import com.layla.services.StatsService;
import com.layla.ui.NotificationService;

public final class AppContext {
    private static final StatsService STATS = new StatsService();
    private static final GameBalance BALANCE = new GameBalance();
    private static final DatabaseService DB = new DatabaseService();

    private static AchievementService achievementInstance;
    private static NotificationService notificationInstance;

    private static int currentProfileId = 1;

    private AppContext() {}

    public static StatsService stats()   { return STATS; }
    public static GameBalance balance()  { return BALANCE; }
    public static DatabaseService db()   { return DB; }

    public static NotificationService notifications() {
        if (notificationInstance == null) {
            notificationInstance = new NotificationService();
        }
        return notificationInstance;
    }

    public static AchievementService achievements() {
        if (achievementInstance == null) {
            achievementInstance = new AchievementService(db(), notifications());
        }
        return achievementInstance;
    }

    public static int getProfileId() { return currentProfileId; }
    public static void setProfileId(int id) {
        currentProfileId = id;
    }

    // ==== Global balance ====
    public static final class GameBalance {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

        // Player
        public double startHp = 6.0;
        public double maxHp   = 6.0;
        public int startCoins = 0;

        // Enemy defaults
        public double enemyBaseHp   = 6.0;
        public double enemySpeedAvg = 130.0;
        public double enemyScoreK   = 5.0;

        // Enemy projectile stats
        public double enemyProjSpeed  = 180.0;
        public double enemyProjRange  = 2.2;
        public double enemyProjDamage = 1.0;
        public double enemyContactDamage = 1.0;
        public double enemyFireRate = 0.8;
        public double enemyFireJitter = 0.35;

        public double projectileMicroStepPx = 12.0;
        public int    projectileMaxSubSteps = 6;

        private final EnumMap<EnemyType, EnemyProfile> enemyProfiles = new EnumMap<>(EnemyType.class);
        private final EnumMap<EnemyType, Integer> spawnWeights = new EnumMap<>(EnemyType.class);

        public GameBalance() {
            resetDefaults();
        }

        public EnemyProfile profile(EnemyType type) {
            if (type == null) return null;
            return enemyProfiles.computeIfAbsent(type, this::defaultProfile);
        }

        public Map<EnemyType, EnemyProfile> profiles() {
            return enemyProfiles;
        }

        public Map<EnemyType, Integer> spawnWeights() {
            return spawnWeights;
        }

        public void resetDefaults() {
            this.startHp = 6.0;
            this.maxHp   = 6.0;
            this.startCoins = 0;

            this.enemyBaseHp   = 6.0;
            this.enemySpeedAvg = 130.0;
            this.enemyScoreK   = 5.0;

            this.enemyProjSpeed  = 180.0;
            this.enemyProjRange  = 2.2;
            this.enemyProjDamage = 1.0;
            this.enemyContactDamage = 1.0;

            this.enemyFireRate    = 0.8;
            this.enemyFireJitter  = 0.35;

            this.projectileMicroStepPx = 12.0;
            this.projectileMaxSubSteps = 6;

            resetEnemyProfiles();
        }

        private void resetEnemyProfiles() {
            enemyProfiles.clear();
            spawnWeights.clear();
            for (EnemyType type : EnemyType.values()) {
                enemyProfiles.put(type, defaultProfile(type));
                spawnWeights.put(type, defaultWeight(type));
            }
        }

        private EnemyProfile defaultProfile(EnemyType type) {
            if (type == null) {
                return EnemyProfile.of(20, 110, 5, 1.2, 10, 260, 500, 6, false);
            }
            return switch (type) {
                case SHOOTER  -> EnemyProfile.of(20, 110, 5, 1.2, 10, 260, 500, 6, false);
                case MELEE    -> EnemyProfile.of(25, 140, 8, 0.0, 12, 0,   0,   0, false);
                case TURRET   -> EnemyProfile.of(30, 0,   3, 0.6, 0,  300, 650, 7, true);
                case TANK     -> EnemyProfile.of(60, 80, 10, 0.3, 4,  220, 450, 8, false);
                case KAMIKAZE -> EnemyProfile.of(15, 170, 14, 0.0, 18, 0,   0,   0, false);
            };
        }

        private int defaultWeight(EnemyType type) {
            if (type == null) return 10;
            return switch (type) {
                case SHOOTER  -> 40;
                case MELEE    -> 30;
                case TURRET   -> 10;
                case TANK     -> 10;
                case KAMIKAZE -> 10;
            };
        }

        public void loadFromJson(Path path) {
            try {
                if (path == null || !Files.exists(path)) return;
                try (Reader reader = Files.newBufferedReader(path)) {
                    BalanceDTO dto = GSON.fromJson(reader, BalanceDTO.class);
                    if (dto == null) return;
                    if (dto.enemyProfiles != null && !dto.enemyProfiles.isEmpty()) {
                        enemyProfiles.clear();
                        dto.enemyProfiles.forEach((type, profile) -> {
                            if (type != null && profile != null) {
                                enemyProfiles.put(type, profile);
                            }
                        });
                    }
                    if (dto.spawnWeights != null && !dto.spawnWeights.isEmpty()) {
                        spawnWeights.clear();
                        dto.spawnWeights.forEach((type, weight) -> {
                            if (type != null && weight != null) {
                                spawnWeights.put(type, Math.max(0, weight));
                            }
                        });
                    }
                    ensureEnemyDefaults();
                }
            } catch (Exception ignored) {}
        }

        public void saveToJson(Path path) {
            try {
                if (path == null) return;
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                try (Writer writer = Files.newBufferedWriter(path)) {
                    BalanceDTO dto = new BalanceDTO(enemyProfiles, spawnWeights);
                    GSON.toJson(dto, writer);
                }
            } catch (Exception ignored) {}
        }

        private void ensureEnemyDefaults() {
            for (EnemyType type : EnemyType.values()) {
                enemyProfiles.computeIfAbsent(type, this::defaultProfile);
                spawnWeights.putIfAbsent(type, defaultWeight(type));
            }
        }

        public static final class BalanceDTO {
            public Map<EnemyType, EnemyProfile> enemyProfiles;
            public Map<EnemyType, Integer> spawnWeights;

            public BalanceDTO() {}

            public BalanceDTO(Map<EnemyType, EnemyProfile> profiles,
                              Map<EnemyType, Integer> weights) {
                this.enemyProfiles = (profiles != null)
                        ? new EnumMap<>(profiles)
                        : new EnumMap<>(EnemyType.class);
                this.spawnWeights = (weights != null)
                        ? new EnumMap<>(weights)
                        : new EnumMap<>(EnemyType.class);
            }
        }
    }
}
