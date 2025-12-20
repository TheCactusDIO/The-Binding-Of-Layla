package com.layla.services;

import java.util.HashMap;
import java.util.Map;

import com.layla.core.AssetsManager;

import javafx.application.Platform;
import javafx.scene.media.AudioClip;

public final class SoundService {
    // Cache local de mapeo "clave" -> "AudioClip ya cargado"
    private final Map<String, AudioClip> cache = new HashMap<>();
    private final Map<String, Long> lastPlayNs = new HashMap<>();

    public void play(String key) {
        if (key == null) return;

        long now = System.nanoTime();
        long minIntervalNs = minIntervalFor(key);

        if (minIntervalNs > 0L) {
            long last = lastPlayNs.getOrDefault(key, 0L);
            // Si intentamos reproducir demasiado rápido, salimos
            if (now - last < minIntervalNs) {
                return;
            }
            lastPlayNs.put(key, now);
        }

        // Obtener del AssetsManager (que ya lo tiene en memoria)
        AudioClip clip = cache.computeIfAbsent(key, this::getFromAssets);
        if (clip != null) {
            // ✅ usar volumen global (0..1)
            clip.play(AssetsManager.getSfxVolume());
        }
    }

    /**
     * Precarga / "warmup" de clips para reducir el delay (sobre todo la primera vez).
     * Llamar idealmente al empezar la run (signalGameStart).
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
                    } catch (Exception ignore) { }
                }
            }
        });
    }

    private long minIntervalFor(String key) {
        return switch (key) {
            case "hurt"           -> 150_000_000L;   // 150ms
            case "hit"            -> 60_000_000L;
            case "enemy_death"    -> 80_000_000L;
            case "player_death"   -> 500_000_000L;
            case "shot"           -> 40_000_000L;
            case "item"           -> 200_000_000L;
            case "coin"           -> 00_000_000L;     // ráfagas de coin
            case "buy"            -> 120_000_000L;
            case "boss_death"     -> 500_000_000L;
            case "enemy_presence" -> 3_000_000_000L; // cada 3s
            case "victory"        -> 2_000_000_000L;
            case "trophy"         -> 2_000_000_000L;
            default               -> 0L;
        };
    }

    // Traduce nombres cortos a rutas y pide el recurso PRECARGADO al AssetsManager
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

            default -> null;
        };

        if (path == null) return null;

        // Esto accede al caché en memoria de AssetsManager (instantáneo)
        return AssetsManager.loadSound(path);
    }
}
