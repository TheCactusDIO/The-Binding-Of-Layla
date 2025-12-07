package com.layla.services;

import java.util.HashMap;
import java.util.Map;

import com.layla.core.AssetsManager; // Importante: Usamos el gestor central

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
            // Opcional: ajustar volumen global si lo tuvieras
            // clip.setVolume(AssetsManager.getSfxVolume());
            clip.play();
        }
    }

    private long minIntervalFor(String key) {
        return switch (key) {
            case "hurt"         -> 150_000_000L; // 150ms
            case "hit"          -> 60_000_000L;
            case "enemy_death"  -> 100_000_000L;
            case "player_death" -> 500_000_000L;
            case "shot"         -> 40_000_000L;
            case "item"         -> 200_000_000L;
            case "coin"         -> 5_000_000L; // ⚡ FIX: Bajamos a 5ms para que las monedas suenen fluidas en ráfaga
            default             -> 0L;
        };
    }

    // Traduce nombres cortos a rutas y pide el recurso PRECARGADO al AssetsManager
    private AudioClip getFromAssets(String key) {
        String path = switch (key) {
            case "hit"           -> "assets/sounds/hit.mp3";
            case "hurt"          -> "assets/sounds/hurt.mp3";
            case "shot"          -> "assets/sounds/shot.mp3";
            case "enemy_death"   -> "assets/sounds/enemy_death.mp3";
            case "player_death"  -> "assets/sounds/player_death.mp3";
            case "dead"          -> "assets/sounds/enemy_death.mp3";
            case "item"          -> "assets/sounds/item.mp3";
            case "coin"          -> "assets/sounds/coin.mp3";
            default -> null;
        };

        if (path == null) return null;

        // Esto accede al caché en memoria de AssetsManager (instantáneo)
        return AssetsManager.loadSound(path);
    }
}
