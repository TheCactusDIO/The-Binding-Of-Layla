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

/**
 * Servicio para gestionar el ranking de puntuaciones (high scores) usando SQLite.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Crear (si no existe) la carpeta y la tabla de puntuaciones.</li>
 *   <li>Guardar puntuaciones de forma asíncrona para no bloquear la UI.</li>
 *   <li>Leer el Top N de puntuaciones (consulta síncrona).</li>
 * </ul>
 *
 * <p>Notas:</p>
 * <ul>
 *   <li>La base de datos se guarda en {@code ./data/laila.db}.</li>
 *   <li>El guardado asíncrono usa el {@link CompletableFuture} por defecto (common pool).</li>
 * </ul>
 */
public class HighScoreService {

    /** Carpeta donde se guarda el fichero SQLite. */
    private static final String DB_FOLDER = "data";

    /** Nombre del fichero SQLite. */
    private static final String DB_NAME = "laila.db";

    /** Cadena de conexión JDBC para SQLite. */
    private static final String CONNECTION_STRING = "jdbc:sqlite:" + DB_FOLDER + "/" + DB_NAME;

    /**
     * Crea el servicio e inicializa la base de datos (carpeta y tabla).
     */
    public HighScoreService() {
        initializeDatabase();
    }

    /**
     * Inicializa la base de datos:
     * <ol>
     *   <li>Crea la carpeta {@link #DB_FOLDER} si no existe.</li>
     *   <li>Crea la tabla {@code scores} si no existe.</li>
     * </ol>
     *
     * <p>No lanza excepción hacia fuera: ante error, lo registra por consola.</p>
     */
    private void initializeDatabase() {
        try {
            // 1) Crear carpeta 'data' si no existe
            File folder = new File(DB_FOLDER);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            // 2) Crear tabla si no existe
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
            System.err.println("[HighScoreService] Error inicializando DB: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Guarda una puntuación de forma asíncrona para no congelar la UI.
     *
     * <p>Inserta una fila en la tabla {@code scores} con el nombre del jugador, puntuación,
     * piso alcanzado y si fue victoria.</p>
     *
     * @param playerName nombre del jugador (debe ser no nulo; se guarda tal cual)
     * @param score puntuación obtenida
     * @param floor piso alcanzado
     * @param isWin {@code true} si la partida terminó en victoria
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
                System.out.println("[HighScoreService] Score guardado: " + score);

            } catch (Exception e) {
                System.err.println("[HighScoreService] Error guardando score: " + e.getMessage());
            }
        });
    }

    /**
     * Recupera el Top N de puntuaciones.
     *
     * <p>Este método es síncrono. Lo normal es llamarlo antes de cargar la vista de ranking,
     * o desde un hilo de background si vas a hacerlo en caliente.</p>
     *
     * @param limit número máximo de entradas a devolver
     * @return lista con las mejores puntuaciones (ordenadas descendentemente por score)
     */
    public List<ScoreEntry> getTopScores(int limit) {
        List<ScoreEntry> list = new ArrayList<>();
        String sql = "SELECT player_name, score, floor, is_win, created_at FROM scores ORDER BY score DESC LIMIT ?";

        try (Connection conn = DriverManager.getConnection(CONNECTION_STRING);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new ScoreEntry(
                            rs.getString("player_name"),
                            rs.getInt("score"),
                            rs.getInt("floor"),
                            rs.getBoolean("is_win"),
                            rs.getString("created_at")
                    ));
                }
            }
        } catch (Exception e) {
            System.err.println("[HighScoreService] Error leyendo scores: " + e.getMessage());
        }

        return list;
    }

    /**
     * DTO para transportar datos de una entrada del ranking.
     *
     * @param name nombre del jugador
     * @param score puntuación
     * @param floor piso alcanzado
     * @param win si fue victoria
     * @param date fecha/hora guardada (texto tal cual lo devuelve SQLite)
     */
    public record ScoreEntry(String name, int score, int floor, boolean win, String date) { }
}
