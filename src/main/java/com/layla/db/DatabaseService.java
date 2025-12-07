package com.layla.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;

public class DatabaseService {

    private static final String DB_FOLDER = "data";
    private static final String DB_NAME = "laila.db";
    private static final String CONNECTION_STRING = "jdbc:sqlite:" + DB_FOLDER + "/" + DB_NAME;

    public DatabaseService() {
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            File folder = new File(DB_FOLDER);
            if (!folder.exists()) folder.mkdirs();

            try (Connection conn = DriverManager.getConnection(CONNECTION_STRING);
                 Statement stmt = conn.createStatement()) {

                // 1. Tabla de Perfiles (Estadísticas Globales)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS profiles (
                        id INTEGER PRIMARY KEY,
                        run_count INTEGER DEFAULT 0,
                        win_count INTEGER DEFAULT 0,
                        death_count INTEGER DEFAULT 0,
                        current_streak INTEGER DEFAULT 0,
                        best_streak INTEGER DEFAULT 0,
                        last_played DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // 2. Tabla de Estadísticas de Enemigos
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS enemy_stats (
                        profile_id INTEGER,
                        enemy_type TEXT,
                        seen INTEGER DEFAULT 0,
                        killed INTEGER DEFAULT 0,
                        killed_by INTEGER DEFAULT 0,
                        PRIMARY KEY (profile_id, enemy_type),
                        FOREIGN KEY(profile_id) REFERENCES profiles(id)
                    )
                """);

                // 3. Tabla de Puntuaciones (Historial)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS run_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        profile_id INTEGER,
                        score INTEGER,
                        floor INTEGER,
                        is_win INTEGER,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY(profile_id) REFERENCES profiles(id)
                    )
                """);

                // Inicializar los 3 perfiles si no existen
                for (int i = 1; i <= 3; i++) {
                    stmt.execute("INSERT OR IGNORE INTO profiles (id) VALUES (" + i + ")");
                }
            }
        } catch (Exception e) {
            System.err.println("[DatabaseService] Error initializing DB: " + e.getMessage());
        }
    }

    // --- MÉTODOS DE PERFIL ---

    public ProfileSummary getProfileSummary(int profileId) {
        String sql = "SELECT run_count, win_count, death_count, current_streak, best_streak FROM profiles WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(CONNECTION_STRING);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, profileId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new ProfileSummary(
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
        return new ProfileSummary(0,0,0,0,0);
    }

    // --- MÉTODOS DE ACTUALIZACIÓN DE ESTADÍSTICAS ---

    public void recordRunEndAsync(int profileId, boolean isWin, int score, int floor) {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = DriverManager.getConnection(CONNECTION_STRING)) {
                conn.setAutoCommit(false); // Transacción

                // 1. Guardar historial
                try (PreparedStatement ph = conn.prepareStatement(
                        "INSERT INTO run_history (profile_id, score, floor, is_win) VALUES (?, ?, ?, ?)")) {
                    ph.setInt(1, profileId);
                    ph.setInt(2, score);
                    ph.setInt(3, floor);
                    ph.setInt(4, isWin ? 1 : 0);
                    ph.executeUpdate();
                }

                // 2. Actualizar perfil (racha, contadores)
                String updateProfile = isWin
                    ? "UPDATE profiles SET run_count = run_count + 1, win_count = win_count + 1, " +
                      "current_streak = current_streak + 1, " +
                      "best_streak = MAX(best_streak, current_streak + 1), last_played = CURRENT_TIMESTAMP WHERE id = ?"
                    : "UPDATE profiles SET run_count = run_count + 1, death_count = death_count + 1, " +
                      "current_streak = 0, last_played = CURRENT_TIMESTAMP WHERE id = ?";

                try (PreparedStatement pp = conn.prepareStatement(updateProfile)) {
                    pp.setInt(1, profileId);
                    pp.executeUpdate();
                }

                conn.commit();
                System.out.println("[Database] Run saved for Profile " + profileId);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void incrementEnemyStatAsync(int profileId, String enemyType, StatType type) {
        CompletableFuture.runAsync(() -> {
            String column = switch(type) {
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
                e.printStackTrace(); // En prod, loguear menos agresivo para no spammear
            }
        });
    }

    public enum StatType { SEEN, KILLED, KILLED_BY }

    public record ProfileSummary(int runs, int wins, int deaths, int streak, int bestStreak) {}
}
