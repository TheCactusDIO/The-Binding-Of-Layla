package com.layla.services;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.layla.core.AssetsManager;

/**
 * Servicio de configuración del usuario.
 *
 * Responsabilidades:
 * - Cargar y guardar un archivo JSON con ajustes persistentes (volúmenes, fullscreen, etc.).
 * - Aplicar al runtime los ajustes que pueden aplicarse sin necesidad de UI (volúmenes).
 *
 * Nota:
 * - Fullscreen no se aplica aquí porque depende del Stage (se aplica en App.start o en el controlador de Settings).
 */
public final class ConfigService {

    private static final Path CONFIG_PATH =
            Path.of(System.getProperty("user.home"), ".layla", "config.json");

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    /** Estado actual de configuración en memoria (nunca null). */
    private ConfigData data = new ConfigData();

    /**
     * Crea el servicio y carga la configuración desde disco.
     * Si no existe, se crean valores por defecto.
     */
    public ConfigService() {
        load();
    }

    /**
     * Carga la configuración desde {@link #CONFIG_PATH}.
     *
     * Comportamiento:
     * - Si existe el fichero: lo parsea y lo usa.
     * - Si no existe o hay error: usa valores por defecto.
     * - Tras cargar, normaliza valores (clamp) y llama a {@link #applyConfig()} para aplicarlo al juego.
     */
    public void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    ConfigData loaded = GSON.fromJson(reader, ConfigData.class);
                    if (loaded != null) {
                        data = loaded;
                    } else {
                        data = new ConfigData();
                    }
                }
            } else {
                data = new ConfigData();
            }
        } catch (Exception e) {
            System.err.println("[ConfigService] Error cargando config: " + e.getMessage());
            data = new ConfigData();
        }

        // Asegurar rangos válidos incluso si el JSON viene “mal”.
        data.musicVolume = clamp01(data.musicVolume);
        data.sfxVolume = clamp01(data.sfxVolume);

        applyConfig();
    }

    /**
     * Guarda la configuración actual en {@link #CONFIG_PATH}.
     *
     * Comportamiento:
     * - Crea el directorio padre si no existe.
     * - Escribe el JSON en disco.
     * - No aplica nada al runtime (para eso usa {@link #applyConfig()} o los setters).
     */
    public void save() {
        try {
            if (CONFIG_PATH.getParent() != null) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(data, writer);
            }
            System.out.println("[ConfigService] Configuración guardada.");
        } catch (Exception e) {
            System.err.println("[ConfigService] Error guardando config: " + e.getMessage());
        }
    }

    /**
     * Aplica al runtime los ajustes que se pueden aplicar sin depender de UI.
     *
     * Actualmente:
     * - Volumen de música y SFX en {@link AssetsManager}.
     *
     * Importante:
     * - Fullscreen no se aplica aquí porque requiere acceso al Stage.
     */
    public void applyConfig() {
        AssetsManager.setMusicVolume(data.musicVolume);
        AssetsManager.setSfxVolume(data.sfxVolume);
    }

    /**
     * Devuelve el volumen de música persistido (0..1).
     */
    public double getMusicVolume() {
        return data.musicVolume;
    }

    /**
     * Cambia el volumen de música (0..1) y lo aplica inmediatamente al runtime.
     */
    public void setMusicVolume(double v) {
        data.musicVolume = clamp01(v);
        applyConfig();
    }

    /**
     * Devuelve el volumen de SFX persistido (0..1).
     */
    public double getSfxVolume() {
        return data.sfxVolume;
    }

    /**
     * Cambia el volumen de SFX (0..1) y lo aplica inmediatamente al runtime.
     */
    public void setSfxVolume(double v) {
        data.sfxVolume = clamp01(v);
        applyConfig();
    }

    /**
     * Indica si el usuario quiere fullscreen.
     * Nota: esto solo es el valor guardado, no aplica el fullscreen.
     */
    public boolean isFullscreen() {
        return data.fullscreen;
    }

    /**
     * Cambia el valor guardado de fullscreen.
     * Nota: aplicar fullscreen requiere Stage (se hace en App.start o en SettingsController).
     */
    public void setFullscreen(boolean fullscreen) {
        data.fullscreen = fullscreen;
    }

    /**
     * Devuelve una copia “simple” de los datos actuales por si la UI necesita mostrarlos.
     * (Si no lo usas, lo puedes borrar.)
     */
    public ConfigData snapshot() {
        ConfigData copy = new ConfigData();
        copy.musicVolume = data.musicVolume;
        copy.sfxVolume = data.sfxVolume;
        copy.fullscreen = data.fullscreen;
        return copy;
    }

    /**
     * Helper para mantener valores dentro de 0..1.
     */
    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    /**
     * DTO usado para serialización JSON.
     *
     * Contiene valores por defecto razonables si no existe config.json.
     */
    public static class ConfigData {
        public double musicVolume = 0.5;
        public double sfxVolume = 0.8;
        public boolean fullscreen = false;
    }
}
