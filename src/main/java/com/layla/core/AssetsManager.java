package com.layla.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;

import javafx.concurrent.Task;
import javafx.scene.image.Image;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.text.Font;

/**
 * Gestor global de assets del juego (imágenes, sonidos, fuentes, música y vídeo).
 *
 * - Mantiene cachés en memoria para evitar recargar recursos repetidamente.
 * - Permite precargar recursos leyendo un manifest (assets/manifest.json).
 * - Proporciona reproducción reutilizable de música/vídeo con un único MediaPlayer global.
 * - Para SFX usa AudioClip cuando es posible (latencia baja) y MediaPlayer como fallback.
 *
 * Nota importante:
 * - Para evitar que la música “se corte” al navegar entre pantallas, usa {@link #ensureMusic(String, boolean)}.
 * - Si quieres reiniciar una pista sí o sí, usa {@link #playMusic(String, boolean)}.
 */
public class AssetsManager {

    // =========================
    // CACHÉS EN MEMORIA
    // =========================

    /** Caché de imágenes ya cargadas: ruta relativa -> Image. */
    private static final Map<String, Image> imageCache = new HashMap<>();

    /** Caché de sonidos cortos ya cargados: ruta relativa -> AudioClip. */
    private static final Map<String, AudioClip> soundCache = new HashMap<>();

    /** Caché de fuentes: "archivo@size" -> Font. */
    private static final Map<String, Font> fontCache = new HashMap<>();

    /**
     * Lista de MediaPlayers activos usados para SFX (fallback).
     * Se guarda una referencia fuerte para evitar que el GC corte el sonido antes de tiempo.
     */
    private static final List<MediaPlayer> activeSfx =
            Collections.synchronizedList(new ArrayList<>());

    // =========================
    // PLAYERS GLOBALES
    // =========================

    /** Reproductor global de música de fondo (solo uno a la vez). */
    private static MediaPlayer backgroundMusic;

    /** Último archivo de música cargado en el reproductor global (para evitar reinicios innecesarios). */
    private static String currentMusicFile;

    /** Reproductor global de vídeo (solo uno a la vez). */
    private static MediaPlayer videoPlayer;

    // =========================
    // VOLUMEN (0..1)
    // =========================

    /** Volumen global para efectos de sonido (SFX), rango 0..1. */
    private static double sfxVolume = 1.0;

    /**
     * Volumen global para música, rango 0..1.
     * Importante: se guarda aunque no haya música sonando, para aplicarlo a futuras reproducciones.
     */
    private static double musicVolume = 0.6;

    /**
     * Ajusta el volumen global de SFX (0..1).
     * Se aplica a reproducciones futuras de AudioClip y a MediaPlayer en playSfx/playSfxWithPlayer.
     */
    public static void setSfxVolume(double v) {
        sfxVolume = clamp01(v);
        System.out.println("[AssetsManager] Volumen SFX = " + sfxVolume);
    }

    /** Devuelve el volumen global actual de SFX (0..1). */
    public static double getSfxVolume() {
        return sfxVolume;
    }

    /** Devuelve el volumen global actual de música (0..1). */
    public static double getMusicVolume() {
        return musicVolume;
    }

    /**
     * Ajusta el volumen global de música (0..1).
     * Si ya hay música sonando, también aplica el cambio al MediaPlayer actual.
     */
    public static void setMusicVolume(double v) {
        musicVolume = clamp01(v);
        if (backgroundMusic != null) {
            backgroundMusic.setVolume(musicVolume);
        }
        System.out.println("[AssetsManager] Volumen música = " + musicVolume);
    }

    /**
     * Devuelve el nombre de archivo de la música actualmente cargada (por ejemplo "menu.mp3"),
     * o null si no hay ninguna pista cargada.
     */
    public static String getCurrentMusicFile() {
        return currentMusicFile;
    }

    // =========================
    // CARGA DESDE MANIFEST
    // =========================

    /**
     * Executor dedicado para carga de assets.
     * Es de un único hilo y daemon para no bloquear el cierre de la app.
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "assets-loader");
        t.setDaemon(true);
        return t;
    });

    /**
     * Estructura del manifest (assets/manifest.json).
     * Contiene rutas relativas (respecto a / en resources) de imágenes y sonidos.
     */
    public static class Manifest {
        public List<String> images = new ArrayList<>();
        public List<String> sounds = new ArrayList<>();
    }

    /**
     * Crea un Task de JavaFX que:
     * - Lee el manifest (assets/manifest.json).
     * - Precarga imágenes y sonidos en caché.
     * - Actualiza progreso y mensajes para UI (pantalla de carga).
     *
     * Nota: este método SOLO crea el Task; para ejecutarlo, envíalo al executor con {@link #submit(Task)}.
     */
    public Task<Void> createLoadTask() {
        return new Task<>() {
            @Override
            protected Void call() {
                System.out.println("[AssetsManager] Iniciando carga de assets");
                updateMessage("Leyendo manifest...");

                Manifest manifest = readManifest();
                if (manifest == null) {
                    throw new IllegalStateException("assets/manifest.json no encontrado o inválido");
                }

                List<String> images = Optional.ofNullable(manifest.images).orElse(List.of());
                List<String> sounds = Optional.ofNullable(manifest.sounds).orElse(List.of());
                int total = Math.max(1, images.size() + sounds.size());
                int done = 0;

                for (String path : images) {
                    loadImage(path);
                    done++;
                    updateProgress(done, total);
                    updateMessage("Imagen cargada: " + path);
                }

                for (String path : sounds) {
                    loadSound(path);
                    done++;
                    updateProgress(done, total);
                    updateMessage("Sonido cargado: " + path);
                }

                updateMessage("Assets cargados.");
                updateProgress(1, 1);
                return null;
            }

            @Override protected void failed()    { super.failed();    shutdownExecutor(); }
            @Override protected void cancelled() { super.cancelled(); shutdownExecutor(); }
            @Override protected void succeeded() { super.succeeded(); shutdownExecutor(); }
        };
    }

    /**
     * Lee y parsea el manifest JSON desde resources (/assets/manifest.json).
     *
     * @return Manifest con listas de imágenes/sonidos, o null si no existe o hay error.
     */
    private Manifest readManifest() {
        System.out.println("[AssetsManager] Leyendo manifest.json...");
        try (InputStream is = getClass().getResourceAsStream("/assets/manifest.json")) {
            if (is == null) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return new Gson().fromJson(reader, Manifest.class);
            }
        } catch (Exception e) {
            System.err.println("[AssetsManager] Error leyendo manifest: " + e.getMessage());
            return null;
        }
    }

    /**
     * Envía un Task al hilo de carga de assets.
     * Útil para ejecutar {@link #createLoadTask()} sin bloquear la UI.
     */
    public void submit(Task<?> task) {
        executor.submit(task);
    }

    /**
     * Detiene el executor interno de carga.
     * Se llama normalmente al finalizar la precarga para liberar recursos.
     */
    public void shutdownExecutor() {
        try {
            if (!executor.isShutdown()) {
                System.out.println("[AssetsManager] Cerrando executor de carga");
                executor.shutdownNow();
            } else {
                System.out.println("[AssetsManager] Executor ya estaba cerrado");
            }
        } catch (Exception e) {
            System.err.println("[AssetsManager] Error al cerrar executor: " + e.getMessage());
        }
    }

    // =========================
    // IMÁGENES
    // =========================

    /**
     * Carga una imagen desde resources y la guarda en caché.
     *
     * @param relativePath ruta relativa dentro de resources, por ejemplo "assets/images/foo.png"
     * @return Image cargada o null si no existe / hay error.
     */
    public static Image loadImage(String relativePath) {
        return imageCache.computeIfAbsent(relativePath, path -> {
            try {
                URL url = AssetsManager.class.getResource("/" + path);
                if (url == null) throw new Exception("Imagen no encontrada: " + path);

                Image img = new Image(url.toExternalForm(), false);
                System.out.println("[AssetsManager] Imagen cacheada: " + path);
                return img;
            } catch (Exception e) {
                System.err.println("[AssetsManager] Error cargando imagen: " + e.getMessage());
                return null;
            }
        });
    }

    // =========================
    // SONIDOS (AudioClip)
    // =========================

    /**
     * Carga un sonido corto como AudioClip y lo guarda en caché.
     * AudioClip es preferible para SFX por su baja latencia (especialmente WAV/AIFF/AU).
     *
     * @param relativePath ruta relativa dentro de resources, por ejemplo "assets/sounds/coin.wav"
     * @return AudioClip o null si no existe / hay error.
     */
    public static AudioClip loadSound(String relativePath) {
        return soundCache.computeIfAbsent(relativePath, path -> {
            try {
                URL url = AssetsManager.class.getResource("/" + path);
                if (url == null) throw new Exception("Sonido no encontrado: " + path);

                System.out.println("[AssetsManager] Sonido cacheado: " + path);
                return new AudioClip(url.toExternalForm());
            } catch (Exception e) {
                System.err.println("[AssetsManager] Error cargando sonido: " + e.getMessage());
                return null;
            }
        });
    }

    // =========================
    // SFX (corto) AudioClip + fallback MediaPlayer
    // =========================

    /**
     * Reproduce un efecto de sonido desde /assets/sounds/.
     *
     * - Si el archivo es WAV/AIFF/AU intenta AudioClip (más rápido y con menos delay).
     * - Si no, usa MediaPlayer como fallback (sirve para mp3, etc.).
     * - Cuando usa MediaPlayer, se guarda en {@link #activeSfx} para evitar que el GC lo corte.
     *
     * @param fileName nombre del archivo (ej: "coin.wav", "victory.mp3")
     */
    public static void playSfx(String fileName) {
        final String rel = "assets/sounds/" + fileName;

        try {
            URL url = AssetsManager.class.getResource("/" + rel);
            if (url == null) {
                System.err.println("[AssetsManager] Ruta SFX no encontrada: " + rel);
                return;
            }

            String lower = fileName.toLowerCase();
            boolean canUseAudioClip = lower.endsWith(".wav") || lower.endsWith(".aiff") || lower.endsWith(".au");

            // Camino rápido: AudioClip (normalmente SFX cortos)
            if (canUseAudioClip) {
                AudioClip clip = loadSound(rel);
                if (clip != null) {
                    clip.setVolume(sfxVolume);
                    clip.play();
                    System.out.println("[AssetsManager] SFX (AudioClip): " + fileName + " vol=" + sfxVolume);
                    return;
                }
                System.err.println("[AssetsManager] AudioClip falló: " + fileName + " -> fallback MediaPlayer");
            }

            // Fallback universal: MediaPlayer
            MediaPlayer mp = new MediaPlayer(new Media(url.toExternalForm()));
            mp.setVolume(sfxVolume);
            mp.setCycleCount(1);

            activeSfx.add(mp);

            mp.setOnEndOfMedia(() -> {
                try { mp.stop(); mp.dispose(); } catch (Exception ignore) {}
                activeSfx.remove(mp);
            });

            mp.setOnError(() -> {
                System.err.println("[AssetsManager] Error MediaPlayer SFX: " + mp.getError());
                try { mp.stop(); mp.dispose(); } catch (Exception ignore) {}
                activeSfx.remove(mp);
            });

            mp.play();
            System.out.println("[AssetsManager] SFX (MediaPlayer): " + fileName + " vol=" + sfxVolume);

        } catch (Exception e) {
            System.err.println("[AssetsManager] Error reproduciendo SFX: " + e.getMessage());
        }
    }

    /**
     * Reproduce un SFX devolviendo el MediaPlayer, por si necesitas enganchar callbacks externos
     * o consultar estado desde fuera.
     *
     * Importante: también se protege contra GC guardándolo en {@link #activeSfx}.
     *
     * @param fileName nombre del archivo dentro de /assets/sounds/
     * @param volume volumen específico 0..1 (no usa el global)
     * @return MediaPlayer activo o null si falla la carga.
     */
    public static MediaPlayer playSfxWithPlayer(String fileName, double volume) {
        try {
            String rel = "/assets/sounds/" + fileName;
            URL url = AssetsManager.class.getResource(rel);
            if (url == null) {
                System.err.println("[AssetsManager] SFX no encontrado: " + rel);
                return null;
            }

            MediaPlayer mp = new MediaPlayer(new Media(url.toExternalForm()));
            mp.setVolume(clamp01(volume));
            mp.setCycleCount(1);

            activeSfx.add(mp);

            mp.setOnEndOfMedia(() -> {
                try { mp.stop(); mp.dispose(); } catch (Exception ignore) {}
                activeSfx.remove(mp);
            });

            mp.setOnError(() -> {
                System.err.println("[AssetsManager] Error MediaPlayer SFX: " + mp.getError());
                try { mp.stop(); mp.dispose(); } catch (Exception ignore) {}
                activeSfx.remove(mp);
            });

            mp.play();
            System.out.println("[AssetsManager] SFX (MP): " + fileName + " vol=" + volume);
            return mp;

        } catch (Exception e) {
            System.err.println("[AssetsManager] playSfxWithPlayer error: " + e.getMessage());
            return null;
        }
    }

    // =========================
    // FUENTES
    // =========================

    /**
     * Carga una fuente desde /assets/fonts/ y la guarda en caché por tamaño.
     * Si falla, devuelve la fuente por defecto de JavaFX.
     *
     * @param fileName archivo de fuente (ej: "pixel.ttf")
     * @param size tamaño de fuente en puntos
     * @return Font cargada o Font.getDefault() si falla
     */
    public static Font loadFont(String fileName, double size) {
        String key = fileName + "@" + size;

        return fontCache.computeIfAbsent(key, k -> {
            try (InputStream is = AssetsManager.class.getResourceAsStream("/assets/fonts/" + fileName)) {
                if (is == null) throw new Exception("Fuente no encontrada: " + fileName);

                Font f = Font.loadFont(is, size);
                System.out.println("[AssetsManager] Fuente cargada: " + fileName);
                return f;

            } catch (Exception e) {
                System.err.println("[AssetsManager] Error cargando fuente: " + e.getMessage());
                return Font.getDefault();
            }
        });
    }

    // =========================
    // MÚSICA
    // =========================

    /**
     * Reproduce una pista de música desde /assets/music/ FORZANDO reinicio:
     * - Detiene la música anterior (aunque sea la misma).
     * - Crea un MediaPlayer nuevo y lo guarda como música global.
     * - Usa el volumen global {@link #musicVolume}.
     *
     * Úsalo cuando realmente quieras reiniciar una pista desde 0.
     */
    public static void playMusic(String fileName, boolean loop) {
        playMusicInternal(fileName, loop, true);
    }

    /**
     * Asegura que una pista esté sonando, pero SIN reiniciarla si ya está puesta.
     *
     * Caso típico:
     * - Entras a ProfileSelect -> ensureMusic("menu.mp3", true)
     * - Pasas a MainMenu -> ensureMusic("menu.mp3", true) (no se corta)
     * - Vuelves al menú desde el juego -> ensureMusic("menu.mp3", true) (arranca si no estaba)
     *
     * Si hay música pausada, la reanuda. Si hay otra pista distinta, cambia a la nueva.
     */
    public static void ensureMusic(String fileName, boolean loop) {
        playMusicInternal(fileName, loop, false);
    }

    /**
     * Pausa la música actual si existe.
     */
    public static void pauseMusic() {
        if (backgroundMusic != null) backgroundMusic.pause();
    }

    /**
     * Reanuda la música actual si existe.
     */
    public static void resumeMusic() {
        if (backgroundMusic != null) backgroundMusic.play();
    }

    /**
     * Detiene y libera el MediaPlayer de música actual.
     * Es importante llamar a dispose() para evitar fugas de recursos en JavaFX Media.
     */
    public static void stopMusic() {
        if (backgroundMusic != null) {
            try { backgroundMusic.stop(); } catch (Exception ignore) {}
            try { backgroundMusic.dispose(); } catch (Exception ignore) {}
            backgroundMusic = null;
        }
        currentMusicFile = null;
        System.out.println("[AssetsManager] Música detenida");
    }

    /**
     * Implementación interna de la lógica de música.
     *
     * @param fileName nombre de archivo dentro de /assets/music/
     * @param loop si true, bucle infinito; si false, una reproducción
     * @param forceRestart si true, reinicia aunque sea el mismo archivo
     */
    private static void playMusicInternal(String fileName, boolean loop, boolean forceRestart) {
        String f = normalizeFileName(fileName);
        if (f == null) return;

        boolean sameTrack = (currentMusicFile != null && currentMusicFile.equals(f) && backgroundMusic != null);

        // Si es la misma pista y no queremos reiniciar, solo aseguramos estado/volumen/bucle.
        if (!forceRestart && sameTrack) {
            try {
                backgroundMusic.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
                backgroundMusic.setVolume(musicVolume);
                backgroundMusic.play(); // si estaba en pausa, reanuda; si estaba sonando, no pasa nada grave
            } catch (Exception e) {
                System.err.println("[AssetsManager] Error asegurando música: " + e.getMessage());
            }
            return;
        }

        // En cualquier otro caso, cambiamos la pista (o reiniciamos).
        stopMusic();

        try {
            URL url = AssetsManager.class.getResource("/assets/music/" + f);
            if (url == null) throw new Exception("Música no encontrada: " + f);

            MediaPlayer mp = new MediaPlayer(new Media(url.toExternalForm()));
            mp.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
            mp.setVolume(musicVolume);

            // Si NO es loop, al terminar liberamos recursos y limpiamos estado.
            mp.setOnEndOfMedia(() -> {
                if (mp.getCycleCount() == 1) {
                    try { mp.stop(); } catch (Exception ignore) {}
                    try { mp.dispose(); } catch (Exception ignore) {}
                    if (backgroundMusic == mp) {
                        backgroundMusic = null;
                        currentMusicFile = null;
                    }
                }
            });

            mp.setOnError(() -> {
                System.err.println("[AssetsManager] Error música MediaPlayer: " + mp.getError());
                try { mp.stop(); mp.dispose(); } catch (Exception ignore) {}
                if (backgroundMusic == mp) {
                    backgroundMusic = null;
                    currentMusicFile = null;
                }
            });

            backgroundMusic = mp;
            currentMusicFile = f;

            mp.play();
            System.out.println("[AssetsManager] Música: " + f + " (loop=" + loop + ")");
        } catch (Exception e) {
            System.err.println("[AssetsManager] Error reproduciendo música: " + e.getMessage());
            backgroundMusic = null;
            currentMusicFile = null;
        }
    }

    // =========================
    // VÍDEO
    // =========================

    /**
     * Reproduce un vídeo desde /assets/videos/.
     * - Detiene el vídeo anterior si existía.
     * - Devuelve el MediaPlayer para que la UI lo conecte a un MediaView.
     * - Por defecto lo deja en mute (útil si el vídeo es decorativo).
     *
     * @param fileName nombre del archivo de vídeo en /assets/videos/
     * @param loop si true, repite indefinidamente
     * @return MediaPlayer del vídeo o null si falla
     */
    public static MediaPlayer playVideo(String fileName, boolean loop) {
        stopVideo();
        try {
            String f = normalizeFileName(fileName);
            if (f == null) return null;

            URL url = AssetsManager.class.getResource("/assets/videos/" + f);
            if (url == null) throw new Exception("Vídeo no encontrado: " + f);

            videoPlayer = new MediaPlayer(new Media(url.toExternalForm()));
            videoPlayer.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
            videoPlayer.setMute(true);
            videoPlayer.play();

            System.out.println("[AssetsManager] Vídeo: " + f);
            return videoPlayer;

        } catch (Exception e) {
            System.err.println("[AssetsManager] Error reproduciendo vídeo: " + e.getMessage());
            return null;
        }
    }

    /**
     * Detiene y libera el MediaPlayer del vídeo actual.
     */
    public static void stopVideo() {
        if (videoPlayer != null) {
            try { videoPlayer.stop(); } catch (Exception ignore) {}
            try { videoPlayer.dispose(); } catch (Exception ignore) {}
            videoPlayer = null;
            System.out.println("[AssetsManager] Vídeo detenido");
        }
    }

    // =========================
    // UTILIDADES
    // =========================

    /** Normaliza un nombre de archivo simple y evita strings vacíos. */
    private static String normalizeFileName(String fileName) {
        if (fileName == null) return null;
        String f = fileName.trim();
        return f.isEmpty() ? null : f;
    }

    /** Clampa un valor al rango [0..1]. */
    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
