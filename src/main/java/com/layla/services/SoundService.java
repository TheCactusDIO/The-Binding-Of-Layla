package com.layla.services;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javafx.scene.media.AudioClip;

public final class SoundService {
    private final Map<String, AudioClip> cache = new HashMap<>();

    // 🔥 Anti-spam: último momento en que se intentó reproducir cada key
    private final Map<String, Long> lastPlayNs = new HashMap<>();

    public void play(String key) {
        if (key == null) return;

        long now = System.nanoTime();
        long minIntervalNs = minIntervalFor(key);

        // Si hay intervalo mínimo, miramos si ha pasado suficiente tiempo
        if (minIntervalNs > 0L) {
            long last = lastPlayNs.getOrDefault(key, 0L);
            if (now - last < minIntervalNs) {
                // Demasiado pronto: ignoramos esta reproducción
                return;
            }
            lastPlayNs.put(key, now);
        }

        AudioClip clip = cache.computeIfAbsent(key, this::load);
        if (clip != null) {
            clip.play();
        }
    }

    /**
     * Intervalos mínimos entre repeticiones de cada sonido.
     * Ajusta estos valores a gusto.
     */
    private long minIntervalFor(String key) {
        // nanosegundos (1_000_000 ns = 1 ms)
        return switch (key) {
            case "hurt"         -> 150_000_000L; // 150 ms entre gritos de daño del player
            case "hit"          -> 60_000_000L;  // 60 ms entre impactos a enemigos
            case "enemy_death"  -> 120_000_000L; // 120 ms entre muertes de enemigos
            case "player_death" -> 500_000_000L; // 500 ms, da igual que se intente varias veces
            case "shot"         -> 40_000_000L;  // 40 ms entre disparos → 25/seg máx
            case "item"         -> 200_000_000L; // 200 ms entre pickups de ítem
            default             -> 0L;           // sin límite especial
        };
    }

    private AudioClip load(String key) {
        // Claves esperadas: "hit","hurt","shot","enemy_death","player_death"
        // Alias de compatibilidad: "dead" -> enemy_death.mp3
        String file = switch (key) {
            case "hit"           -> "assets/sounds/hit.mp3";
            case "hurt"          -> "assets/sounds/hurt.mp3";
            case "shot"          -> "assets/sounds/shot.mp3";
            case "enemy_death"   -> "assets/sounds/enemy_death.mp3";
            case "player_death"  -> "assets/sounds/player_death.mp3";
            case "dead"          -> "assets/sounds/enemy_death.mp3";
            case "item"          -> "assets/sounds/item.mp3";
            default -> null;
        };
        if (file == null) return null;
        URL url = getClass().getClassLoader().getResource(file);
        if (url == null) {
            System.err.println("[SoundService] Missing resource: " + file);
            return null;
        }
        return new AudioClip(url.toExternalForm());
    }
}
