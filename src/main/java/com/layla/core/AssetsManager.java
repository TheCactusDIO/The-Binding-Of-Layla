package com.layla.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
 * Gestor global de recursos del juego (Imágenes, Sonido, Música, Vídeo y Fuentes).
 * <p>
 * Responsabilidades:
 * <ul>
 * <li><strong>Caché:</strong> Evita recargar recursos desde disco manteniendo referencias en memoria.</li>
 * <li><strong>Precarga:</strong> Lee un archivo {@code manifest.json} para cargar recursos críticos al inicio.</li>
 * <li><strong>Gestión de Audio:</strong> Maneja la música de fondo (loop) y los efectos de sonido (SFX).</li>
 * <li><strong>Compatibilidad:</strong> Extrae recursos a archivos temporales si es necesario para reproductores nativos (GStreamer).</li>
 * </ul>
 */
public class AssetsManager {

    // =========================
    // CACHÉS EN MEMORIA
    // =========================

    /** Caché de imágenes cargadas (Ruta relativa -> Objeto Image). */
    private static final Map<String, Image> imageCache = new ConcurrentHashMap<>();

    /** Caché de efectos de sonido cortos (Ruta relativa -> AudioClip). */
    private static final Map<String, AudioClip> soundCache = new ConcurrentHashMap<>();

    /** Caché de fuentes tipográficas (Nombre+Tamaño -> Font). */
    private static final Map<String, Font> fontCache = new ConcurrentHashMap<>();

    /**
     * Caché de URIs de medios extraídos temporalmente.
     * Almacena la ruta del archivo temporal generado para recursos empaquetados en JAR.
     */
    private static final Map<String, String> extractedMediaUriCache = new ConcurrentHashMap<>();

    /**
     * Lista de reproductores de SFX activos (MediaPlayer).
     * <p>Se mantiene una referencia fuerte aquí para evitar que el Garbage Collector (GC)
     * detenga el sonido prematuramente si la variable local sale de ámbito.</p>
     */
    private static final List<MediaPlayer> activeSfx =
            Collections.synchronizedList(new ArrayList<>());

    // =========================
    // PLAYERS GLOBALES
    // =========================

    /** Reproductor único para la música de fondo. */
    private static MediaPlayer backgroundMusic;

    /** Nombre del archivo de música actual para evitar reinicios si se pide la misma pista. */
    private static String currentMusicFile;

    /** Reproductor único para vídeos (intros, cinemáticas). */
    private static MediaPlayer videoPlayer;

    // =========================
    // VOLUMEN (0.0 a 1.0)
    // =========================

    /** Volumen global para efectos de sonido. */
    private static double sfxVolume = 1.0;

    /** Volumen global para música. */
    private static double musicVolume = 0.6;

    /**
     * Establece el volumen global de los efectos de sonido.
     * @param v Valor entre 0.0 y 1.0.
     */
    public static void setSfxVolume(double v) {
        sfxVolume = clamp01(v);
        System.out.println("[AssetsManager] Volumen SFX = " + sfxVolume);
    }

    /** @return Volumen actual de SFX. */
    public static double getSfxVolume() {
        return sfxVolume;
    }

    /** @return Volumen actual de música. */
    public static double getMusicVolume() {
        return musicVolume;
    }

    /**
     * Establece el volumen global de la música.
     * Aplica el cambio inmediatamente si hay música reproduciéndose.
     * @param v Valor entre 0.0 y 1.0.
     */
    public static void setMusicVolume(double v) {
        musicVolume = clamp01(v);
        if (backgroundMusic != null) {
            backgroundMusic.setVolume(musicVolume);
        }
        System.out.println("[AssetsManager] Volumen música = " + musicVolume);
    }

    /**
     * Obtiene el nombre del archivo de música actual.
     * @return Nombre del archivo o null si no hay música cargada.
     */
    public static String getCurrentMusicFile() {
        return currentMusicFile;
    }

    // =========================
    // CARGA DESDE MANIFEST
    // =========================

    /**
     * Executor de un solo hilo (Daemon) para realizar la carga de assets en segundo plano.
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "assets-loader");
        t.setDaemon(true); // Permite que la JVM termine aunque este hilo siga vivo
        return t;
    });

    /**
     * Modelo de datos para mapear el archivo JSON de manifiesto.
     */
    public static class Manifest {
        public List<String> images = new ArrayList<>();
        public List<String> sounds = new ArrayList<>();
    }

    /**
     * Crea una tarea asíncrona (Task) para cargar los assets definidos en el manifiesto.
     * <p>Esta tarea actualiza su mensaje y progreso, ideal para vincular a una barra de carga.</p>
     *
     * @return Task lista para ser ejecutada.
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
     * Lee el archivo {@code /assets/manifest.json} del classpath.
     * @return Objeto Manifest o null si falla la lectura.
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
     * Envía una tarea al executor para su procesamiento asíncrono.
     * @param task Tarea a ejecutar.
     */
    public void submit(Task<?> task) {
        executor.submit(task);
    }

    /**
     * Cierra ordenadamente el executor de carga.
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
     * Carga una imagen y la almacena en caché.
     *
     * @param relativePath Ruta relativa (ej: "assets/images/logo.png").
     * @return Objeto Image o null si no se encuentra.
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
     * Carga un sonido corto en memoria (AudioClip).
     * Recomendado para efectos de sonido repetitivos y de baja latencia.
     *
     * @param relativePath Ruta relativa.
     * @return AudioClip o null si falla.
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
    // SFX (Híbrido AudioClip / MediaPlayer)
    // =========================

    /**
     * Reproduce un efecto de sonido.
     * <p>Intenta usar {@link AudioClip} para formatos RAW (wav, aiff) por rendimiento.
     * Si no es posible (mp3) o falla, usa {@link MediaPlayer} como fallback.</p>
     *
     * @param fileName Nombre del archivo en {@code assets/sounds/}.
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

            // Prioridad: AudioClip (Baja latencia)
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

            // Fallback: MediaPlayer (Mayor latencia, soporta MP3)
            MediaPlayer mp = new MediaPlayer(new Media(url.toExternalForm()));
            mp.setVolume(sfxVolume);
            mp.setCycleCount(1);

            activeSfx.add(mp); // Evitar GC

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
     * Reproduce un SFX devolviendo el control del MediaPlayer.
     * Útil si se necesita detener el sonido manualmente o saber cuándo termina.
     *
     * @param fileName Nombre del archivo.
     * @param volume   Volumen específico para este sonido.
     * @return MediaPlayer activo o null.
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
     * Carga una fuente TTF/OTF.
     * @param fileName Nombre del archivo en {@code assets/fonts/}.
     * @param size Tamaño en puntos.
     * @return Font cargada o la fuente por defecto si falla.
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
     * Reproduce una pista de música, reiniciándola si ya estaba sonando.
     */
    public static void playMusic(String fileName, boolean loop) {
        playMusicInternal(fileName, loop, true);
    }

    /**
     * Asegura que una pista esté sonando.
     * Si ya está sonando la misma pista, no hace nada (no la reinicia).
     */
    public static void ensureMusic(String fileName, boolean loop) {
        playMusicInternal(fileName, loop, false);
    }

    /** Pausa la música actual. */
    public static void pauseMusic() {
        if (backgroundMusic != null) backgroundMusic.pause();
    }

    /** Reanuda la música actual. */
    public static void resumeMusic() {
        if (backgroundMusic != null) backgroundMusic.play();
    }

    /** Detiene y libera la música actual. */
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
     * Lógica interna para reproducir música.
     *
     * @param fileName      Archivo de música.
     * @param loop          Si debe repetirse.
     * @param forceRestart  Si true, reinicia la pista aunque sea la misma que suena.
     */
    private static void playMusicInternal(String fileName, boolean loop, boolean forceRestart) {
        String f = normalizeFileName(fileName);
        if (f == null) return;

        boolean sameTrack = (currentMusicFile != null && currentMusicFile.equals(f) && backgroundMusic != null);

        if (!forceRestart && sameTrack) {
            try {
                backgroundMusic.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
                backgroundMusic.setVolume(musicVolume);
                backgroundMusic.play();
            } catch (Exception e) {
                System.err.println("[AssetsManager] Error asegurando música: " + e.getMessage());
            }
            return;
        }

        stopMusic();

        try {
            URL url = AssetsManager.class.getResource("/assets/music/" + f);
            if (url == null) throw new Exception("Música no encontrada: " + f);

            MediaPlayer mp = new MediaPlayer(new Media(url.toExternalForm()));
            mp.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
            mp.setVolume(musicVolume);

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
     * Reproduce un vídeo.
     * <p>Si el recurso está dentro de un JAR, se extrae a un archivo temporal para que GStreamer pueda leerlo.</p>
     *
     * @param fileName Archivo en {@code assets/videos/}.
     * @param loop     Si debe repetirse.
     * @return MediaPlayer del vídeo.
     */
    public static MediaPlayer playVideo(String fileName, boolean loop) {
        stopVideo();
        try {
            String f = normalizeFileName(fileName);
            if (f == null) return null;

            String resourcePath = "/assets/videos/" + f;
            URL url = AssetsManager.class.getResource(resourcePath);
            if (url == null) throw new Exception("Vídeo no encontrado: " + f);

            String mediaUri = toPlayableMediaUri(resourcePath, url);

            videoPlayer = new MediaPlayer(new Media(mediaUri));
            videoPlayer.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
            videoPlayer.setMute(true); // Se silencia por defecto (intro)
            videoPlayer.play();

            System.out.println("[AssetsManager] Vídeo: " + f + " (" + url.getProtocol() + ")");
            return videoPlayer;

        } catch (Exception e) {
            System.err.println("[AssetsManager] Error reproduciendo vídeo: " + e.getMessage());
            return null;
        }
    }

    /** Detiene el vídeo actual. */
    public static void stopVideo() {
        if (videoPlayer != null) {
            try { videoPlayer.stop(); } catch (Exception ignore) {}
            try { videoPlayer.dispose(); } catch (Exception ignore) {}
            videoPlayer = null;
            System.out.println("[AssetsManager] Vídeo detenido");
        }
    }

    /**
     * Convierte la URL del recurso a una URI válida para JavaFX Media.
     * Si la URL es interna (jar:), extrae el archivo a temporal.
     */
    private static String toPlayableMediaUri(String cacheKey, URL resourceUrl) {
        String protocol = resourceUrl.getProtocol();
        if ("file".equalsIgnoreCase(protocol)) {
            return resourceUrl.toExternalForm();
        }

        return extractedMediaUriCache.computeIfAbsent(cacheKey, k -> {
            try (InputStream is = AssetsManager.class.getResourceAsStream(cacheKey)) {
                if (is == null) return resourceUrl.toExternalForm();

                String suffix = "";
                int dot = cacheKey.lastIndexOf('.');
                if (dot >= 0 && dot < cacheKey.length() - 1) {
                    suffix = cacheKey.substring(dot);
                    if (suffix.length() > 10) suffix = "";
                }

                Path tmp = Files.createTempFile("layla_media_", suffix);
                Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
                tmp.toFile().deleteOnExit();

                return tmp.toUri().toString();

            } catch (Exception e) {
                return resourceUrl.toExternalForm();
            }
        });
    }

    // =========================
    // UTILIDADES
    // =========================

    private static String normalizeFileName(String fileName) {
        if (fileName == null) return null;
        String f = fileName.trim();
        return f.isEmpty() ? null : f;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
