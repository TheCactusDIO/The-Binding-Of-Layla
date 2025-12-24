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
import com.layla.model.CharacterType;
import com.layla.model.EnemyProfile;
import com.layla.model.EnemyType;
import com.layla.services.AchievementService;
import com.layla.services.ConfigService;
import com.layla.services.StatsService;
import com.layla.ui.NotificationService;

/**
 * Contenedor estático de servicios y estado global de la aplicación.
 *
 * <p>Este contexto centraliza accesos a servicios "singletons" (estadísticas, balance, BD, configuración,
 * logros, notificaciones), además del estado actual del perfil y opciones de partida.</p>
 *
 * <p><b>Importante:</b> es un diseño deliberadamente global. Evita crear instancias nuevas en cada pantalla y
 * simplifica el acceso desde cualquier parte del juego, pero implica acoplamiento.</p>
 */
public final class AppContext {

    /** Servicio de estadísticas del jugador (base + modificadores runtime). */
    private static final StatsService STATS = new StatsService();

    /** Balance global del juego (perfiles de enemigos, pesos de aparición, etc.). */
    private static final GameBalance BALANCE = new GameBalance();

    /** Servicio de persistencia (SQLite / stats / perfiles / etc.). */
    private static final DatabaseService DB = new DatabaseService();

    /** Configuración persistente (volúmenes, fullscreen, etc.). */
    private static final ConfigService CONFIG = new ConfigService();

    /** Servicio de logros (lazy-init porque depende de notificaciones). */
    private static AchievementService achievementInstance;

    /** Servicio de notificaciones UI (lazy-init). */
    private static NotificationService notificationInstance;

    /** Perfil activo. Por defecto: 1. */
    private static int currentProfileId = 1;

    /** Personaje seleccionado. */
    private static CharacterType selectedCharacter = CharacterType.LAYLA;

    /** Indicador de modo difícil. */
    private static boolean hardMode = false;

    /** Modificadores de la run actual (Custom Run). */
    private static final RunModifiers RUN_MODIFIERS = new RunModifiers();

    private AppContext() {
        // No instanciable
    }

    /**
     * Devuelve el servicio global de estadísticas.
     *
     * @return instancia única de {@link StatsService}
     */
    public static StatsService stats() {
        return STATS;
    }

    /**
     * Devuelve el balance global del juego.
     *
     * @return instancia única de {@link GameBalance}
     */
    public static GameBalance balance() {
        return BALANCE;
    }

    /**
     * Devuelve el servicio global de base de datos.
     *
     * @return instancia única de {@link DatabaseService}
     */
    public static DatabaseService db() {
        return DB;
    }

    /**
     * Devuelve el servicio global de configuración persistente.
     *
     * @return instancia única de {@link ConfigService}
     */
    public static ConfigService config() {
        return CONFIG;
    }

    /**
     * Devuelve el servicio de notificaciones, creándolo si aún no existe.
     *
     * @return instancia de {@link NotificationService}
     */
    public static NotificationService notifications() {
        if (notificationInstance == null) {
            notificationInstance = new NotificationService();
        }
        return notificationInstance;
    }

    /**
     * Devuelve el servicio de logros, creándolo si aún no existe.
     *
     * <p>Depende de {@link #db()} y {@link #notifications()}.</p>
     *
     * @return instancia de {@link AchievementService}
     */
    public static AchievementService achievements() {
        if (achievementInstance == null) {
            achievementInstance = new AchievementService(db(), notifications());
        }
        return achievementInstance;
    }

    /**
     * Devuelve el ID del perfil activo.
     *
     * @return id del perfil
     */
    public static int getProfileId() {
        return currentProfileId;
    }

    /**
     * Establece el ID del perfil activo.
     *
     * <p>Nota: si hay servicios que cachean estado por perfil (por ejemplo logros),
     * su recarga debe gestionarse donde corresponda.</p>
     *
     * @param id id del perfil
     */
    public static void setProfileId(int id) {
        currentProfileId = id;
    }

    /**
     * Devuelve el personaje actualmente seleccionado.
     *
     * @return tipo de personaje
     */
    public static CharacterType getSelectedCharacter() {
        return selectedCharacter;
    }

    /**
     * Establece el personaje seleccionado.
     *
     * @param c tipo de personaje
     */
    public static void setSelectedCharacter(CharacterType c) {
        selectedCharacter = c;
    }

    /**
     * Indica si está activo el modo difícil.
     *
     * @return {@code true} si el modo difícil está activo
     */
    public static boolean isHardMode() {
        return hardMode;
    }

    /**
     * Activa o desactiva el modo difícil.
     *
     * @param hm {@code true} para activar hard mode
     */
    public static void setHardMode(boolean hm) {
        hardMode = hm;
    }

    /**
     * Devuelve los modificadores de la run actual (Custom Run).
     *
     * @return instancia única de {@link RunModifiers}
     */
    public static RunModifiers getRunModifiers() {
        return RUN_MODIFIERS;
    }

    /**
     * Contenedor simple para los modificadores numéricos de la partida actual.
     *
     * <p>Se usa como estado runtime (no persistente por defecto) para ajustar dificultad/cantidad de enemigos
     * u otros parámetros específicos de una run.</p>
     */
    public static class RunModifiers {

        /** Multiplicador de vida de enemigos. */
        public double enemyHpMult = 1.0;

        /** Multiplicador de daño de enemigos. */
        public double enemyDmgMult = 1.0;

        /** Multiplicador de ritmo/cantidad de enemigos (spawn). */
        public double spawnRateMult = 1.0;

        /** Oleadas por piso. */
        public int wavesPerFloor = 5;

        /**
         * Resetea los modificadores a valores por defecto.
         */
        public void reset() {
            enemyHpMult = 1.0;
            enemyDmgMult = 1.0;
            spawnRateMult = 1.0;
            wavesPerFloor = 5;
        }
    }

    // ==== Global balance ====

    /**
     * Balance global del juego.
     *
     * <p>Incluye valores base del jugador/enemigos, parámetros de proyectiles y colecciones
     * de perfiles por tipo de enemigo, además de pesos de aparición.</p>
     */
    public static final class GameBalance {

        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

        /** Vida inicial del jugador (HP). */
        public double startHp = 6.0;

        /** Vida máxima del jugador (HP). */
        public double maxHp = 6.0;

        /** Monedas iniciales. */
        public int startCoins = 0;

        /** Valores base medios para enemigos (referencias generales). */
        public double enemyBaseHp = 6.0;
        public double enemySpeedAvg = 130.0;
        public double enemyScoreK = 5.0;

        /** Proyectiles enemigos (valores por defecto). */
        public double enemyProjSpeed = 180.0;
        public double enemyProjRange = 2.2;
        public double enemyProjDamage = 1.0;

        /** Daño de contacto por defecto. */
        public double enemyContactDamage = 1.0;

        /** Cadencia y "jitter" por defecto de disparo enemigo. */
        public double enemyFireRate = 0.8;
        public double enemyFireJitter = 0.35;

        /** Micro-step para detección de colisiones de proyectiles. */
        public double projectileMicroStepPx = 12.0;

        /** Máximo de sub-steps por frame para colisiones de proyectiles. */
        public int projectileMaxSubSteps = 6;

        /** Perfiles detallados por tipo de enemigo. */
        private final EnumMap<EnemyType, EnemyProfile> enemyProfiles = new EnumMap<>(EnemyType.class);

        /** Pesos de aparición por tipo de enemigo. */
        private final EnumMap<EnemyType, Integer> spawnWeights = new EnumMap<>(EnemyType.class);

        /**
         * Crea el balance y carga valores por defecto.
         */
        public GameBalance() {
            resetDefaults();
        }

        /**
         * Devuelve el perfil de un tipo de enemigo.
         *
         * <p>Si no existe, se crea con valores por defecto.</p>
         *
         * @param type tipo de enemigo
         * @return perfil asociado o {@code null} si {@code type} es {@code null}
         */
        public EnemyProfile profile(EnemyType type) {
            if (type == null) return null;
            return enemyProfiles.computeIfAbsent(type, this::defaultProfile);
        }

        /**
         * Devuelve el mapa de perfiles de enemigos.
         *
         * <p>Nota: devuelve el mapa interno. Si necesitas inmutabilidad, haz copia fuera.</p>
         *
         * @return mapa de perfiles
         */
        public Map<EnemyType, EnemyProfile> profiles() {
            return enemyProfiles;
        }

        /**
         * Devuelve el mapa de pesos de aparición.
         *
         * <p>Nota: devuelve el mapa interno. Si necesitas inmutabilidad, haz copia fuera.</p>
         *
         * @return mapa de pesos
         */
        public Map<EnemyType, Integer> spawnWeights() {
            return spawnWeights;
        }

        /**
         * Resetea los valores del balance a los valores por defecto.
         *
         * <p>Incluye también reset de perfiles y pesos.</p>
         */
        public void resetDefaults() {
            this.startHp = 6.0;
            this.maxHp = 6.0;
            this.startCoins = 0;

            this.enemyBaseHp = 6.0;
            this.enemySpeedAvg = 130.0;
            this.enemyScoreK = 5.0;

            this.enemyProjSpeed = 180.0;
            this.enemyProjRange = 2.2;
            this.enemyProjDamage = 1.0;
            this.enemyContactDamage = 1.0;

            this.enemyFireRate = 0.8;
            this.enemyFireJitter = 0.35;

            this.projectileMicroStepPx = 12.0;
            this.projectileMaxSubSteps = 6;

            resetEnemyProfiles();
        }

        /**
         * Resetea perfiles y pesos de aparición a sus valores por defecto.
         */
        private void resetEnemyProfiles() {
            enemyProfiles.clear();
            spawnWeights.clear();
            for (EnemyType type : EnemyType.values()) {
                enemyProfiles.put(type, defaultProfile(type));
                spawnWeights.put(type, defaultWeight(type));
            }
        }

        /**
         * Devuelve el perfil por defecto para un tipo de enemigo.
         *
         * <p>Si {@code type} es {@code null}, devuelve un perfil genérico.</p>
         *
         * @param type tipo de enemigo
         * @return perfil por defecto
         */
        private EnemyProfile defaultProfile(EnemyType type) {
            if (type == null) {
                return EnemyProfile.of(20, 110, 5, 1.2, 10, 260, 500, 6, false);
            }
            return switch (type) {
                case SHOOTER -> EnemyProfile.of(20, 110, 5, 1.2, 10, 260, 500, 6, false);
                case MELEE -> EnemyProfile.of(25, 140, 8, 0.0, 12, 0, 0, 0, false);
                case TURRET -> EnemyProfile.of(30, 0, 3, 0.6, 0, 300, 650, 7, true);
                case TANK -> EnemyProfile.of(60, 80, 10, 0.3, 4, 220, 450, 8, false);
                case KAMIKAZE -> EnemyProfile.of(15, 170, 14, 0.0, 18, 0, 0, 0, false);
            };
        }

        /**
         * Devuelve el peso de aparición por defecto de un tipo de enemigo.
         *
         * @param type tipo de enemigo
         * @return peso por defecto
         */
        private int defaultWeight(EnemyType type) {
            if (type == null) return 10;
            return switch (type) {
                case SHOOTER -> 40;
                case MELEE -> 30;
                case TURRET -> 10;
                case TANK -> 10;
                case KAMIKAZE -> 10;
            };
        }

        /**
         * Carga perfiles y pesos desde un JSON.
         *
         * <p>Comportamiento:</p>
         * <ul>
         *   <li>Si {@code path} es {@code null} o no existe, no hace nada.</li>
         *   <li>Si el JSON contiene mapas, reemplaza los actuales.</li>
         *   <li>Al final, asegura que todos los {@link EnemyType} existan (defaults).</li>
         * </ul>
         *
         * @param path ruta del fichero JSON
         */
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
            } catch (Exception ignored) {
                // Mantengo el comportamiento: fallo silencioso.
            }
        }

        /**
         * Guarda perfiles y pesos a un JSON.
         *
         * <p>Comportamiento:</p>
         * <ul>
         *   <li>Si {@code path} es {@code null}, no hace nada.</li>
         *   <li>Crea el directorio padre si es necesario.</li>
         *   <li>Serializa usando {@link GsonBuilder#setPrettyPrinting()}.</li>
         * </ul>
         *
         * @param path ruta destino del fichero JSON
         */
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
            } catch (Exception ignored) {
                // Mantengo el comportamiento: fallo silencioso.
            }
        }

        /**
         * Asegura que todos los {@link EnemyType} tienen:
         * <ul>
         *   <li>Perfil en {@link #enemyProfiles}</li>
         *   <li>Peso en {@link #spawnWeights}</li>
         * </ul>
         *
         * <p>Se usa después de cargar JSON para evitar valores incompletos.</p>
         */
        private void ensureEnemyDefaults() {
            for (EnemyType type : EnemyType.values()) {
                enemyProfiles.computeIfAbsent(type, this::defaultProfile);
                spawnWeights.putIfAbsent(type, defaultWeight(type));
            }
        }

        /**
         * DTO para serializar/deserializar el balance a JSON.
         *
         * <p>Se guarda únicamente lo que interesa para edición externa:
         * perfiles de enemigos y pesos de aparición.</p>
         */
        public static final class BalanceDTO {

            /** Perfiles por tipo de enemigo. */
            public Map<EnemyType, EnemyProfile> enemyProfiles;

            /** Pesos por tipo de enemigo. */
            public Map<EnemyType, Integer> spawnWeights;

            /** Constructor vacío requerido por Gson. */
            public BalanceDTO() {}

            /**
             * Crea el DTO copiando los mapas recibidos.
             *
             * @param profiles mapa de perfiles
             * @param weights mapa de pesos
             */
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
