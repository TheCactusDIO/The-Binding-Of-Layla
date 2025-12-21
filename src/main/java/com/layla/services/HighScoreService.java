package com.layla.services;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class HighScoreService {

    // Ruta de la base de datos: ./data/laila.db
    private static final String DB_FOLDER = "data";
    private static final String DB_NAME = "laila.db";
    private static final String CONNECTION_STRING = "jdbc:sqlite:" + DB_FOLDER + "/" + DB_NAME;

    public HighScoreService() {
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            // 1. Crear carpeta 'data' si no existe
            File folder = new File(DB_FOLDER);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            // 2. Crear tabla si no existe
            try (Connection conn = DriverManager.getConnection(CONNECTION_STRING);
                 Statement stmt = conn.createStatement()) {

                String sql = """
                    CREATE TABLE IF NOT EXISTS scores (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_name TEXT NOT NULL,
                        score INTEGER NOT NULL,
                        floor INTEGER,
                        is_win INTEGER,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """;
                stmt.execute(sql);
            }
        } catch (Exception e) {
            System.err.println("[HighScoreService] Error initializing DB: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Guarda la puntuación de forma asíncrona para no congelar la UI.
     */
    public void saveScoreAsync(String playerName, int score, int floor, boolean isWin) {
        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO scores(player_name, score, floor, is_win) VALUES(?, ?, ?, ?)";

            try (Connection conn = DriverManager.getConnection(CONNECTION_STRING);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, playerName);
                pstmt.setInt(2, score);
                pstmt.setInt(3, floor);
                pstmt.setInt(4, isWin ? 1 : 0);

                pstmt.executeUpdate();
                System.out.println("[HighScoreService] Score saved: " + score);

            } catch (Exception e) {
                System.err.println("[HighScoreService] Error saving score: " + e.getMessage());
            }
        });
    }

    /**
     * Recupera el Top N de puntuaciones (Síncrono, llamar antes de cargar la vista de ranking).
     */
    public List<ScoreEntry> getTopScores(int limit) {
        List<ScoreEntry> list = new ArrayList<>();
        String sql = "SELECT player_name, score, floor, is_win, created_at FROM scores ORDER BY score DESC LIMIT ?";

        try (Connection conn = DriverManager.getConnection(CONNECTION_STRING);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                list.add(new ScoreEntry(
                    rs.getString("player_name"),
                    rs.getInt("score"),
                    rs.getInt("floor"),
                    rs.getBoolean("is_win"),
                    rs.getString("created_at")
                ));
            }
        } catch (Exception e) {
            System.err.println("[HighScoreService] Error reading scores: " + e.getMessage());
        }
        return list;
    }

    // Clase interna para transportar datos (DTO)
    public record ScoreEntry(String name, int score, int floor, boolean win, String date) {}
}
