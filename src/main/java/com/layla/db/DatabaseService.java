package com.layla.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio de acceso a datos (SQLite).
 *
 * Responsabilidades:
 * - Crear la base de datos y tablas si no existen (migración básica).
 * - Lectura/escritura de perfiles, runs, estadísticas de enemigos y logros.
 * - Operaciones de escritura importantes en async para no bloquear la UI.
 *
 * Nota: se abre una conexión por operación (patrón simple y suficiente en SQLite para este juego).
 */
public class DatabaseService {

    private static final String DB_FOLDER = "data";
    private static final String DB_NAME = "laila.db";
    private static final String CONNECTION_STRING = "jdbc:sqlite:" + DB_FOLDER + "/" + DB_NAME;

    /**
     * Creates the database service and initializes the schema if needed.
     */
    public DatabaseService() {
        initializeDatabase();
    }

    /**
     * Inicializa el fichero SQLite y crea las tablas principales si no existen.
     * También activa foreign keys y asegura que existan los 3 perfiles base.
     */
    private void initializeDatabase() {
        try {
            File folder = new File(DB_FOLDER);
            if (!folder.exists()) folder.mkdirs();

            try (Connection conn = DriverManager.getConnection(CONNECTION_STRING);
                 Statement stmt = conn.createStatement()) {

                stmt.execute("PRAGMA foreign_keys = ON");

                // =========================
                // 1) Perfiles
                // =========================
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS profiles (
                        id INTEGER PRIMARY KEY,
                        name TEXT,
                        run_count INTEGER DEFAULT 0,
                        win_count INTEGER DEFAULT 0,
                        death_count INTEGER DEFAULT 0,
                        current_streak INTEGER DEFAULT 0,
                        best_streak INTEGER DEFAULT 0,
                        last_played DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // (Compat) Si vienes de una versión antigua sin columna name
                try {
                    stmt.execute("ALTER TABLE profiles ADD COLUMN name TEXT");
                } catch (SQLException ignored) {}

                // =========================
                // 2) Estadísticas de enemigos
                // =========================
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS enemy_stats (
                        profile_id INTEGER,
                        enemy_type TEXT,
                        seen INTEGER DEFAULT 0,
                        killed INTEGER DEFAULT 0,
                        killed_by INTEGER DEFAULT 0,
                        PRIMARY KEY (profile_id, enemy_type),
                        FOREIGN KEY(profile_id) REFERENCES profiles(id) ON DELETE CASCADE
                    )
                """);

                // =========================
                // 3) Historial de runs
                // =========================
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS run_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        profile_id INTEGER,
                        score INTEGER,
                        floor INTEGER,
                        is_win INTEGER,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY(profile_id) REFERENCES profiles(id) ON DELETE CASCADE
                    )
                """);

                // =========================
                // 4) Logros
                // =========================
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS achievements (
                        profile_id INTEGER NOT NULL,
                        id TEXT NOT NULL,
                        unlocked INTEGER NOT NULL DEFAULT 0,
                        unlock_date DATETIME,
                        PRIMARY KEY (profile_id, id),
                        FOREIGN KEY(profile_id) REFERENCES profiles(id) ON DELETE CASCADE
                    )
                """);

                // =========================
                // 5) Settings (NUEVO)
                // =========================
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS settings (
                        profile_id INTEGER PRIMARY KEY,
                        music_volume REAL NOT NULL DEFAULT 0.6,
                        sfx_volume   REAL NOT NULL DEFAULT 1.0,
                        virtual_width  INTEGER NOT NULL DEFAULT 1920,
                        virtual_height INTEGER NOT NULL DEFAULT 1080,
                        FOREIGN KEY(profile_id) REFERENCES profiles(id) ON DELETE CASCADE
                    )
                """);

                // Asegurar perfiles base y settings base para cada perfil
                for (int i = 1; i <= 3; i++) {
                    stmt.execute("INSERT OR IGNORE INTO profiles (id) VALUES (" + i + ")");
                    stmt.execute("INSERT OR IGNORE INTO settings (profile_id) VALUES (" + i + ")");
                }
            }
        } catch (Exception e) {
            System.err.println("[DatabaseService] Error inicializando DB: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================
    // PERFIL
    // =========================================================

    /**
     * Devuelve un resumen del perfil (nombre + contadores principales).
     * Se usa para pintar pantallas de selección/estadísticas de perfil.
     */
    public ProfileSummary getProfileSummary(int profileId) {
        String sql = """
            SELECT name, run_count, win_count, death_count, current_streak, best_streak
            FROM profiles WHERE id = ?
        """;

        try (Connection conn = DriverManager.getConnection(CONNECTION_STRING);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, profileId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new ProfileSummary(
                    rs.getString("name"),
                    rs.getInt("run_count"),
                    rs.getInt("win_count"),
                    rs.getInt("death_count"),
                    rs.getInt("current_streak"),
                    rs.getInt("best_streak")
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ProfileSummary(null, 0, 0, 0, 0, 0);
    }

    /**
     * Cambia el nombre de un perfil. No crea perfiles nuevos, solo actualiza el existente (1..3).
     */
    public void setProfileName(int profileId, String name) {
        String sql = "UPDATE profiles SET name = ? WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(CONNECTION_STRING);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            pstmt.setInt(2, profileId);
            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Resetea un perfil: borra run_history, enemy_stats y achievements, y reinicia contadores del perfil.
     * Se hace en transacción para que no quede el perfil a medias si algo falla.
     */
    public void resetProfile(int profileId) {
        try (Connection conn = DriverManager.getConnection(CONNECTION_STRING)) {
            conn.setAutoCommit(false);

            try (PreparedStatement p1 = conn.prepareStatement("DELETE FROM run_history WHERE profile_id = ?");
                 PreparedStatement p2 = conn.prepareStatement("DELETE FROM enemy_stats WHERE profile_id = ?");
                 PreparedStatement p3 = conn.prepareStatement("DELETE FROM achievements WHERE profile_id = ?")) {

                p1.setInt(1, profileId); p1.executeUpdate();
                p2.setInt(1, profileId); p2.executeUpdate();
                p3.setInt(1, profileId); p3.executeUpdate();
            }

            String resetSql = """
                UPDATE profiles SET
                  name = NULL,
                  run_count = 0,
                  win_count = 0,
                  death_count = 0,
                  current_streak = 0,
                  best_streak = 0,
                  last_played = CURRENT_TIMESTAMP
                WHERE id = ?
            """;

            try (PreparedStatement p4 = conn.prepareStatement(resetSql)) {
                p4.setInt(1, profileId);
                p4.executeUpdate();
            }

            // Opcional: resetear settings a defaults (si lo deseas)
            try (PreparedStatement p5 = conn.prepareStatement("""
                UPDATE settings SET
                  music_volume = 0.6,
                  sfx_volume = 1.0,
                  virtual_width = 1920,
                  virtual_height = 1080
                WHERE profile_id = ?
            """)) {
                p5.setInt(1, profileId);
                p5.executeUpdate();
            }

            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================================================
    // SETTINGS (NUEVO)
    // =========================================================

    /**
     * Devuelve los ajustes guardados para un perfil.
     * Si no existe fila (raro, pero posible), devuelve defaults.
     */
    public SettingsData getSettings(int profileId) {
        String sql = """
            SELECT music_volume, sfx_volume, virtual_width, virtual_height
            FROM settings WHERE profile_id = ?
        """;

        try (Connection conn = DriverManager.getConnection(CONNECTION_STRING);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, profileId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new SettingsData(
                    rs.getDouble("music_volume"),
                    rs.getDouble("sfx_volume"),
                    rs.getInt("virtual_width"),
                    rs.getInt("virtual_height")
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return SettingsData.defaults();
    }

    /**
     * Guarda los ajustes de un perfil (upsert).
     * Ideal para llamarlo desde el botón "Save & Apply".
     */
    public void saveSettings(int profileId, SettingsData s) {
        String sql = """
            INSERT INTO settings (profile_id, music_volume, sfx_volume, virtual_width, virtual_height)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(profile_id) DO UPDATE SET
              music_volume = excluded.music_volume,
              sfx_volume = excluded.sfx_volume,
              virtual_width = excluded.virtual_width,
              virtual_height = excluded.virtual_height
        """;

        try (Connection conn = DriverManager.getConnection(CONNECTION_STRING);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, profileId);
            pstmt.setDouble(2, clamp01(s.musicVolume()));
            pstmt.setDouble(3, clamp01(s.sfxVolume()));
            pstmt.setInt(4, Math.max(1, s.virtualWidth()));
            pstmt.setInt(5, Math.max(1, s.virtualHeight()));
            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Variante async para guardar settings sin bloquear la UI.
     */
    public void saveSettingsAsync(int profileId, SettingsData s) {
        CompletableFuture.runAsync(() -> saveSettings(profileId, s));
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // =========================================================
    // RUNS / RANKING / ESTADÍSTICAS
    // =========================================================

    /**
     * Registra el final de una run (historial) y actualiza contadores del perfil.
     * Se hace async para no congelar el juego cuando termina la partida.
     */
    public void recordRunEndAsync(int profileId, boolean isWin, int score, int floor) {
        CompletableFuture.runAsync(() -> recordRunEndInternal(profileId, isWin, score, floor, false));
    }

    /**
     * Registra el final de una run y devuelve el total de muertes actualizado para el perfil.
     * Solo es relevante para runs terminadas en muerte; en victoria devuelve el total actual.
     *
     * @param profileId id del perfil
     * @param isWin true si la run es victoria
     * @param score puntuacion final
     * @param floor piso final alcanzado
     * @return future con el total de muertes del perfil tras el update
     */
    public CompletableFuture<Long> recordRunEndAsyncWithDeathTotal(int profileId, boolean isWin, int score, int floor) {
        return CompletableFuture.supplyAsync(() -> recordRunEndInternal(profileId, isWin, score, floor, true));
    }

    /**
     * Performs the run end write in a single transaction and optionally reads the updated death total.
     *
     * @param profileId id del perfil
     * @param isWin true si la run es victoria
     * @param score puntuacion final
     * @param floor piso final alcanzado
     * @param fetchDeaths cuando es true, lee death_count tras el update
     * @return total de muertes actualizado si fetchDeaths es true; 0 en caso contrario o error
     */
    private long recordRunEndInternal(int profileId, boolean isWin, int score, int floor, boolean fetchDeaths) {
        try (Connection conn = DriverManager.getConnection(CONNECTION_STRING)) {
            conn.setAutoCommit(false);

            try (PreparedStatement ph = conn.prepareStatement(
                    "INSERT INTO run_history (profile_id, score, floor, is_win) VALUES (?, ?, ?, ?)")) {

                ph.setInt(1, profileId);
                ph.setInt(2, score);
                ph.setInt(3, floor);
                ph.setInt(4, isWin ? 1 : 0);
                ph.executeUpdate();
            }

            String updateProfile = isWin
                ? """
                  UPDATE profiles SET
                    run_count = run_count + 1,
                    win_count = win_count + 1,
                    current_streak = current_streak + 1,
                    best_streak = MAX(best_streak, current_streak + 1),
                    last_played = CURRENT_TIMESTAMP
                  WHERE id = ?
                  """
                : """
                  UPDATE profiles SET
                    run_count = run_count + 1,
                    death_count = death_count + 1,
                    current_streak = 0,
                    last_played = CURRENT_TIMESTAMP
                  WHERE id = ?
                  """;

            try (PreparedStatement pp = conn.prepareStatement(updateProfile)) {
                pp.setInt(1, profileId);
                pp.executeUpdate();
            }

            long deathTotal = 0;
            if (fetchDeaths) {
                try (PreparedStatement pd = conn.prepareStatement(
                        "SELECT death_count FROM profiles WHERE id = ?")) {
                    pd.setInt(1, profileId);
                    ResultSet rs = pd.executeQuery();
                    if (rs.next()) deathTotal = rs.getLong(1);
                }
            }

            conn.commit();
            return deathTotal;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Recupera el Top N de mejores puntuaciones (run_history) cruzado con el nombre del perfil.
     */
    public List<LeaderboardEntry> getLeaderboard(int limit) {
        List<LeaderboardEntry> list = new ArrayList<>();
        String sql = """
            SELECT p.name, r.score, r.floor, r.is_win, r.created_at
            FROM run_history r
            JOIN profiles p ON r.profile_id = p.id
            ORDER BY r.score DESC
            LIMIT ?
        """;

        try (Connection conn = DriverManager.getConnection(CONNECTION_STRING);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();

            int rank = 1;
            while (rs.next()) {
                String pName = rs.getString("name");
                if (pName == null) pName = "Unknown";

                list.add(new LeaderboardEntry(
                    rank++,
                    pName,
                    rs.getInt("score"),
                    rs.getInt("floor"),
                    rs.getBoolean("is_win"),
                    rs.getString("created_at")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Incrementa (async) una estadística concreta de un enemigo para un perfil.
     * Usa upsert (ON CONFLICT) para crear la fila si no existía.
     */
    public long incrementEnemyStatAsync(int profileId, String enemyType, StatType type) {
        CompletableFuture.runAsync(() -> {
            String column = switch (type) {
                case SEEN -> "seen";
                case KILLED -> "killed";
                case KILLED_BY -> "killed_by";
            };

            String sql = "INSERT INTO enemy_stats (profile_id, enemy_type, " + column + ") VALUES (?, ?, 1) " +
                         "ON CONFLICT(profile_id, enemy_type) DO UPDATE SET " + column + " = " + column + " + 1";

            try (Connection conn = DriverManager.getConnection(CONNECTION_STRING);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setInt(1, profileId);
                pstmt.setString(2, enemyType);
                pstmt.executeUpdate();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return 0;
    }

    /**
     * Devuelve contadores agregados útiles para logros/estadísticas globales.
     *
     * <p>Valores soportados: TOTAL_KILLS (suma enemy_stats) y TOTAL_DEATHS
     * (death_count del perfil).</p>
     *
     * @param profileId id del perfil
     * @param statType tipo de estadistica a consultar
     * @return total acumulado segun el tipo solicitado
     */
    public long getStatTotal(int profileId, String statType) {
        String sql;
        if ("TOTAL_KILLS".equals(statType)) {
            sql = "SELECT COALESCE(SUM(killed),0) FROM enemy_stats WHERE profile_id = ?";
        } else if ("TOTAL_DEATHS".equals(statType)) {
            sql = "SELECT death_count FROM profiles WHERE id = ?";
        } else {
            return 0;
        }

        try (Connection conn = DriverManager.getConnection(CONNECTION_STRING);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, profileId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getLong(1);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Devuelve el mapa completo de estadísticas por tipo de enemigo para un perfil.
     * Se usa para pantallas de estadísticas o para lógica de logros.
     */
    public Map<String, EnemyStatEntry> getAllEnemyStats(int profileId) {
        Map<String, EnemyStatEntry> map = new HashMap<>();
        String sql = "SELECT enemy_type, seen, killed, killed_by FROM enemy_stats WHERE profile_id = ?";

        try (Connection conn = DriverManager.getConnection(CONNECTION_STRING);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, profileId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String type = rs.getString("enemy_type");
                map.put(type, new EnemyStatEntry(
                    rs.getInt("seen"),
                    rs.getInt("killed"),
                    rs.getInt("killed_by")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }

    // =========================================================
    // LOGROS
    // =========================================================

    /**
     * Marca un logro como desbloqueado (si aún estaba bloqueado).
     * Usa un upsert y solo actualiza si unlocked era 0.
     *
     * @param profileId id del perfil propietario del logro
     * @param achievementId id del logro a desbloquear
     */
    public void unlockAchievement(int profileId, String achievementId) {
        String sql = """
            INSERT INTO achievements (profile_id, id, unlocked, unlock_date)
            VALUES (?, ?, 1, datetime('now'))
            ON CONFLICT(profile_id, id) DO UPDATE SET
                unlocked = excluded.unlocked,
                unlock_date = excluded.unlock_date
            WHERE unlocked = 0
        """;

        try (Connection conn = DriverManager.getConnection(CONNECTION_STRING);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, profileId);
            pstmt.setString(2, achievementId);
            pstmt.executeUpdate();

        } catch (Exception e) {
            System.err.println("Error desbloqueando logro: " + achievementId + " - " + e.getMessage());
        }
    }

    /**
     * Devuelve el estado de todos los logros de un perfil.
     * Útil para dibujar la lista de logros y para desbloqueos condicionales.
     *
     * @param profileId id del perfil
     * @return mapa de id -> estado para el perfil solicitado
     */
    public Map<String, AchievementStatus> getAchievementsStatus(int profileId) {
        Map<String, AchievementStatus> statusMap = new HashMap<>();
        String sql = "SELECT id, unlocked, unlock_date FROM achievements WHERE profile_id = ?";

        try (Connection conn = DriverManager.getConnection(CONNECTION_STRING);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, profileId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String id = rs.getString("id");
                boolean unlocked = rs.getInt("unlocked") == 1;
                String date = rs.getString("unlock_date");
                statusMap.put(id, new AchievementStatus(unlocked, date));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return statusMap;
    }

    // =========================================================
    // TIPOS / RECORDS
    // =========================================================

    /**
     * Tipos de estadística.
     */
    public enum StatType {
        /** Visto (aparece/registrado como “visto”). */
        SEEN,
        /** Eliminado (muerto por el jugador u otra causa registrada como “kill”). */
        KILLED,
        /** Muerto a manos de (el enemigo mató al jugador). */
        KILLED_BY
    }

    /**
     * Resumen de contadores del perfil usados en menús y pantallas de estadísticas.
     *
     * @param name nombre del perfil/jugador
     * @param runs número total de partidas (runs) registradas
     * @param wins número total de victorias
     * @param deaths número total de muertes
     * @param streak racha actual (por ejemplo, de victorias)
     * @param bestStreak mejor racha registrada
     */
    public record ProfileSummary(String name, int runs, int wins, int deaths, int streak, int bestStreak) {}

    /**
     * Estadísticas agregadas de un enemigo para un tipo concreto.
     *
     * @param seen número de veces que se ha visto
     * @param killed número de veces que se ha matado
     * @param killedBy número de veces que ha matado al jugador
     */
    public record EnemyStatEntry(int seen, int killed, int killedBy) {}

    /**
     * Estado persistido de un logro para un perfil.
     *
     * @param unlocked si el logro está desbloqueado
     * @param unlockDate fecha de desbloqueo (formato guardado en BD)
     */
    public record AchievementStatus(boolean unlocked, String unlockDate) {}

    /**
     * Fila de leaderboard con metadatos de la partida y posición en el ranking.
     *
     * @param rank posición en el ranking
     * @param playerName nombre del jugador/perfil
     * @param score puntuación obtenida
     * @param floor piso alcanzado
     * @param isWin si la run fue victoria
     * @param date fecha de la run (formato guardado)
     */
    public record LeaderboardEntry(int rank, String playerName, int score, int floor, boolean isWin, String date) {}

    /**
     * Ajustes guardables por perfil.
     *
     * <p>Puedes ampliarlo más adelante (hardMode, partículas, pantalla completa, etc.).</p>
     *
     * @param musicVolume volumen de música (0.0–1.0)
     * @param sfxVolume volumen de efectos (0.0–1.0)
     * @param virtualWidth ancho virtual de renderizado
     * @param virtualHeight alto virtual de renderizado
     */
    public record SettingsData(double musicVolume, double sfxVolume, int virtualWidth, int virtualHeight) {

        /**
         * Devuelve los ajustes por defecto para un perfil nuevo.
         *
         * @return snapshot con los ajustes por defecto
         */
        public static SettingsData defaults() {
            return new SettingsData(0.6, 1.0, 1920, 1080);
        }
    }
}
