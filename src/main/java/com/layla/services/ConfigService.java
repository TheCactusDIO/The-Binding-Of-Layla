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
 * <p><b>Responsabilidades</b>:</p>
 * <ul>
 *   <li>Cargar y guardar un archivo JSON con ajustes persistentes (volúmenes, fullscreen, etc.).</li>
 *   <li>Aplicar en runtime los ajustes que no dependen de la UI (por ejemplo, volúmenes).</li>
 * </ul>
 *
 * <p><b>Nota</b>:</p>
 * <ul>
 *   <li>El modo fullscreen no se aplica aquí porque depende de un {@code Stage}
 *       (se aplica en {@code App.start} o en el controlador de Settings).</li>
 * </ul>
 */
public final class ConfigService {

    /**
     * Ruta del fichero de configuración.
     * <p>Se guarda en el home del usuario: {@code ~/.layla/config.json}</p>
     */
    private static final Path CONFIG_PATH =
            Path.of(System.getProperty("user.home"), ".layla", "config.json");

    /** Instancia de Gson configurada con pretty printing para un JSON legible. */
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    /**
     * Estado actual de configuración en memoria (nunca debería ser {@code null}).
     * <p>Se carga desde disco en {@link #load()} y se inicializa con valores por defecto si falla.</p>
     */
    private ConfigData data = new ConfigData();

    /**
     * Crea el servicio y carga la configuración desde disco.
     * <p>Si no existe el fichero o hay error de lectura, se usan valores por defecto.</p>
     */
    public ConfigService() {
        load();
    }

    /**
     * Carga la configuración desde {@link #CONFIG_PATH}.
     *
     * <p><b>Comportamiento</b>:</p>
     * <ul>
     *   <li>Si existe el fichero: lo parsea y lo usa.</li>
     *   <li>Si no existe o hay error: usa valores por defecto.</li>
     *   <li>Tras cargar, normaliza valores (clamp) y llama a {@link #applyConfig()}.</li>
     * </ul>
     */
    public void load() {
        ConfigData loaded = null;

        try {
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    loaded = GSON.fromJson(reader, ConfigData.class);
                }
            }
        } catch (Exception e) {
            System.err.println("[ConfigService] Error cargando config: " + e.getMessage());
        }

        data = (loaded != null) ? loaded : new ConfigData();

        // Asegurar rangos válidos incluso si el JSON viene “mal”.
        data.musicVolume = clamp01(data.musicVolume);
        data.sfxVolume = clamp01(data.sfxVolume);

        applyConfig();
    }

    /**
     * Guarda la configuración actual en {@link #CONFIG_PATH}.
     *
     * <p><b>Comportamiento</b>:</p>
     * <ul>
     *   <li>Crea el directorio padre si no existe.</li>
     *   <li>Escribe el JSON en disco.</li>
     *   <li>No aplica nada al runtime (para eso usa {@link #applyConfig()} o los setters).</li>
     * </ul>
     */
    public void save() {
        try {
            Path parent = CONFIG_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
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
     * <p>Actualmente:</p>
     * <ul>
     *   <li>Volumen de música y SFX en {@link AssetsManager}.</li>
     * </ul>
     *
     * <p><b>Importante</b>:</p>
     * <ul>
     *   <li>El fullscreen no se aplica aquí porque requiere acceso al {@code Stage}.</li>
     * </ul>
     */
    public void applyConfig() {
        AssetsManager.setMusicVolume(data.musicVolume);
        AssetsManager.setSfxVolume(data.sfxVolume);
    }

    /**
     * Devuelve el volumen de música persistido.
     *
     * @return volumen en rango {@code 0..1}
     */
    public double getMusicVolume() {
        return data.musicVolume;
    }

    /**
     * Cambia el volumen de música y lo aplica inmediatamente al runtime.
     *
     * @param v volumen en rango {@code 0..1} (se clampa automáticamente)
     */
    public void setMusicVolume(double v) {
        data.musicVolume = clamp01(v);
        applyConfig();
    }

    /**
     * Devuelve el volumen de efectos (SFX) persistido.
     *
     * @return volumen en rango {@code 0..1}
     */
    public double getSfxVolume() {
        return data.sfxVolume;
    }

    /**
     * Cambia el volumen de efectos (SFX) y lo aplica inmediatamente al runtime.
     *
     * @param v volumen en rango {@code 0..1} (se clampa automáticamente)
     */
    public void setSfxVolume(double v) {
        data.sfxVolume = clamp01(v);
        applyConfig();
    }

    /**
     * Indica si el usuario quiere fullscreen.
     *
     * <p>Nota: esto solo devuelve el valor guardado; no aplica el fullscreen.</p>
     *
     * @return {@code true} si está activado en la configuración guardada
     */
    public boolean isFullscreen() {
        return data.fullscreen;
    }

    /**
     * Cambia el valor guardado de fullscreen.
     *
     * <p>Nota: aplicar fullscreen requiere {@code Stage} (se hace en {@code App.start}
     * o en {@code SettingsController}).</p>
     *
     * @param fullscreen nuevo valor a guardar
     */
    public void setFullscreen(boolean fullscreen) {
        data.fullscreen = fullscreen;
    }

    /**
     * Devuelve una copia simple de los datos actuales, útil para que la UI los muestre
     * sin exponer el objeto interno.
     *
     * @return copia de {@link ConfigData} con los valores actuales
     */
    public ConfigData snapshot() {
        ConfigData copy = new ConfigData();
        copy.musicVolume = data.musicVolume;
        copy.sfxVolume = data.sfxVolume;
        copy.fullscreen = data.fullscreen;
        return copy;
    }

    /**
     * Helper para mantener valores dentro de {@code 0..1}.
     *
     * @param v valor de entrada
     * @return valor clamped al rango {@code 0..1}
     */
    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    /**
     * DTO usado para serialización JSON.
     *
     * <p>Contiene valores por defecto razonables si no existe {@code config.json}.</p>
     */
    public static class ConfigData {
        /** Volumen de música (0..1). */
        public double musicVolume = 0.5;

        /** Volumen de efectos (SFX) (0..1). */
        public double sfxVolume = 0.8;

        /** Preferencia de pantalla completa (solo persistencia; no se aplica aquí). */
        public boolean fullscreen = false;
    }
}
