package com.layla.services;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.layla.core.AssetsManager;

public class ConfigService {

    private static final Path CONFIG_PATH = Path.of(System.getProperty("user.home"), ".layla", "config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigData data;

    public ConfigService() {
        load();
    }

    public void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    data = GSON.fromJson(reader, ConfigData.class);
                }
            } else {
                data = new ConfigData(); // Valores por defecto
            }
        } catch (Exception e) {
            System.err.println("[ConfigService] Error loading config: " + e.getMessage());
            data = new ConfigData();
        }
        applyConfig();
    }

    public void save() {
        try {
            if (data == null) return;
            if (CONFIG_PATH.getParent() != null) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(data, writer);
            }
            System.out.println("[ConfigService] Configuration saved.");
        } catch (Exception e) {
            System.err.println("[ConfigService] Error saving config: " + e.getMessage());
        }
    }

    public void applyConfig() {
        if (data == null) return;
        AssetsManager.setMusicVolume(data.musicVolume);
        AssetsManager.setSfxVolume(data.sfxVolume);
    }

    // Getters y Setters
    public double getMusicVolume() { return data.musicVolume; }
    public void setMusicVolume(double v) { data.musicVolume = v; applyConfig(); }

    public double getSfxVolume() { return data.sfxVolume; }
    public void setSfxVolume(double v) { data.sfxVolume = v; applyConfig(); }

    public boolean isFullscreen() { return data.fullscreen; }
    public void setFullscreen(boolean f) { data.fullscreen = f; }

    // DTO para JSON
    public static class ConfigData {
        public double musicVolume = 0.5;
        public double sfxVolume = 0.8;
        public boolean fullscreen = false;
    }
}
