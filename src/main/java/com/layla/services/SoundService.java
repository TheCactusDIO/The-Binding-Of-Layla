package com.layla.services;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javafx.scene.media.AudioClip;

public final class SoundService {
    private final Map<String, AudioClip> cache = new HashMap<>();

    public void play(String key) {
        AudioClip clip = cache.computeIfAbsent(key, this::load);
        if (clip != null) clip.play();
    }

    private AudioClip load(String key) {
        // Claves esperadas: "hit","hurt","dead","shot"
        String file = switch (key) {
            case "hit"  -> "assets/sounds/hit.mp3";
            case "hurt" -> "assets/sounds/hurt.mp3";
            case "dead" -> "assets/sounds/dead.mp3";
            case "shot" -> "assets/sounds/shot.mp3";
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
