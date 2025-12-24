package com.layla.services;

import java.util.HashMap;
import java.util.Map;

import com.layla.core.AssetsManager;

import javafx.application.Platform;
import javafx.scene.media.AudioClip;

/**
 * Servicio de sonido (SFX) basado en {@link AudioClip}.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Reproducir sonidos por clave (por ejemplo: {@code "hit"}, {@code "hurt"}...).</li>
 *   <li>Cachear {@link AudioClip} ya cargados para evitar lecturas repetidas.</li>
 *   <li>Aplicar un "rate limit" por sonido para evitar spam (especialmente en eventos rápidos).</li>
 *   <li>Permitir precarga (warmup) para reducir el delay del primer play.</li>
 * </ul>
 *
 * <p>Notas importantes:</p>
 * <ul>
 *   <li>No se cambian nombres de métodos ni el funcionamiento general.</li>
 *   <li>El volumen usado es el global de SFX: {@link AssetsManager#getSfxVolume()}.</li>
 *   <li>Este servicio asume que {@link AssetsManager#loadSound(String)} cachea o carga eficientemente el audio.</li>
 * </ul>
 */
public final class SoundService {

    /** Cache local de mapeo: clave -> {@link AudioClip} ya cargado. */
    private final Map<String, AudioClip> cache = new HashMap<>();

    /**
     * Último instante de reproducción por clave, en nanosegundos (System.nanoTime()).
     * Se usa para aplicar rate limit por sonido.
     */
    private final Map<String, Long> lastPlayNs = new HashMap<>();

    /**
     * Reproduce un sonido identificado por una clave.
     *
     * <p>Comportamiento:</p>
     * <ul>
     *   <li>Si {@code key} es {@code null}, no hace nada.</li>
     *   <li>Aplica un intervalo mínimo entre reproducciones para ciertas claves (ver {@link #minIntervalFor(String)}).</li>
     *   <li>Resuelve la ruta a recurso y obtiene el clip desde {@link AssetsManager} (vía {@link #getFromAssets(String)}).</li>
     *   <li>Reproduce con el volumen global de SFX.</li>
     * </ul>
     *
     * @param key clave del sonido (ej: {@code "hit"}, {@code "coin"}, {@code "victory"}...)
     */
    public void play(String key) {
        if (key == null) return;

        long now = System.nanoTime();
        long minIntervalNs = minIntervalFor(key);

        if (minIntervalNs > 0L) {
            long last = lastPlayNs.getOrDefault(key, 0L);

            // Si intentamos reproducir demasiado rápido, salimos.
            if (now - last < minIntervalNs) {
                return;
            }
            lastPlayNs.put(key, now);
        }

        // Obtener del AssetsManager (que ya lo puede tener en memoria) y cachearlo localmente.
        AudioClip clip = cache.computeIfAbsent(key, this::getFromAssets);
        if (clip != null) {
            clip.play(AssetsManager.getSfxVolume());
        }
    }

    /**
     * Precarga ("warmup") de clips para reducir el delay del primer play.
     *
     * <p>Recomendación:</p>
     * <ul>
     *   <li>Llamar al inicio de la run o al entrar al gameplay.</li>
     * </ul>
     *
     * <p>Implementación:</p>
     * <ul>
     *   <li>Se ejecuta en el hilo de JavaFX con {@link Platform#runLater(Runnable)}.</li>
     *   <li>Para cada clave, obtiene el clip (y lo cachea) y realiza un play/stop a volumen 0.</li>
     * </ul>
     *
     * @param keys claves a precargar (puede ser {@code null} o vacío)
     */
    public void warmUp(String... keys) {
        if (keys == null || keys.length == 0) return;

        Platform.runLater(() -> {
            for (String k : keys) {
                if (k == null) continue;

                AudioClip clip = cache.computeIfAbsent(k, this::getFromAssets);
                if (clip != null) {
                    // Warmup silencioso: inicia/para con volumen 0
                    try {
                        clip.play(0.0);
                        clip.stop();
                    } catch (Exception ignore) {
                        // Si alguna plataforma lanza excepción por play/stop inmediato, lo ignoramos.
                    }
                }
            }
        });
    }

    /**
     * Devuelve el intervalo mínimo (en nanosegundos) permitido entre reproducciones del mismo sonido.
     *
     * <p>Objetivo:</p>
     * <ul>
     *   <li>Evitar saturar el audio con eventos muy frecuentes (hits, disparos, monedas...).</li>
     *   <li>Evitar que ciertos sonidos largos se pisen continuamente.</li>
     * </ul>
     *
     * @param key clave del sonido
     * @return intervalo mínimo en ns. Si devuelve 0, no se limita.
     */
    private long minIntervalFor(String key) {
        return switch (key) {
            case "hurt"           -> 150_000_000L;   // 150 ms
            case "hit"            -> 60_000_000L;    // 60 ms
            case "enemy_death"    -> 80_000_000L;    // 80 ms
            case "player_death"   -> 500_000_000L;   // 500 ms
            case "shot"           -> 40_000_000L;    // 40 ms
            case "item"           -> 200_000_000L;   // 200 ms
            case "coin"           -> 0L;             // sin limitación (ráfagas de coin)
            case "buy"            -> 120_000_000L;   // 120 ms
            case "boss_death"     -> 500_000_000L;   // 500 ms
            case "enemy_presence" -> 3_000_000_000L; // cada 3 s
            case "victory"        -> 2_000_000_000L; // cada 2 s
            case "trophy"         -> 2_000_000_000L; // cada 2 s
            default               -> 0L;
        };
    }

    /**
     * Traduce una clave corta (ej: {@code "hit"}) a una ruta de recurso y obtiene el clip desde
     * {@link AssetsManager}.
     *
     * <p>Notas:</p>
     * <ul>
     *   <li>Si la clave no está mapeada, devuelve {@code null}.</li>
     *   <li>La carga real se delega a {@link AssetsManager#loadSound(String)}.</li>
     * </ul>
     *
     * @param key clave del sonido
     * @return {@link AudioClip} correspondiente, o {@code null} si la clave no existe
     */
    private AudioClip getFromAssets(String key) {
        String path = switch (key) {
            case "hit"            -> "assets/sounds/hit.wav";
            case "hurt"           -> "assets/sounds/hurt.wav";
            case "shot"           -> "assets/sounds/shot.wav";
            case "enemy_death"    -> "assets/sounds/enemy_death.wav";
            case "player_death"   -> "assets/sounds/player_death.wav";
            case "item"           -> "assets/sounds/item.wav";
            case "coin"           -> "assets/sounds/coin.wav";
            case "buy"            -> "assets/sounds/buy.wav";
            case "boss_death"     -> "assets/sounds/boss_death.wav";
            case "enemy_presence" -> "assets/sounds/enemy_presence.wav";
            case "victory"        -> "assets/sounds/victory.wav";
            default               -> null;
        };

        if (path == null) return null;

        // Accede al caché en memoria de AssetsManager (instantáneo si ya estaba cargado).
        return AssetsManager.loadSound(path);
    }
}
