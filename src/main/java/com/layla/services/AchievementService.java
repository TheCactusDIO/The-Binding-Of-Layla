package com.layla.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.layla.AppContext;
import com.layla.db.DatabaseService;
import com.layla.db.DatabaseService.AchievementStatus;
import com.layla.model.AchievementDefinition;
import com.layla.ui.NotificationService;

import javafx.application.Platform;

/**
 * Servicio que gestiona:
 * <ul>
 *   <li>Las definiciones (metadatos) de los logros.</li>
 *   <li>El estado desbloqueado/bloqueado por perfil.</li>
 *   <li>La evaluación de “triggers” (eventos) para desbloquear logros.</li>
 * </ul>
 *
 * <p>
 * Mantiene una caché en memoria del estado de logros del perfil activo. Cuando cambia el perfil,
 * debe llamarse a {@link #reloadForCurrentProfile()} para aislar el estado por perfil.
 * </p>
 *
 * <p>
 * También soporta “reconciliar” progreso antiguo: si un perfil ya cumplía condiciones (por ejemplo,
 * ya tenía muertes o kills guardadas) pero en base de datos no existía todavía el registro del logro,
 * el servicio puede rellenar/desbloquear silenciosamente al cargar.
 * </p>
 */
public final class AchievementService {

    // --- IDs de logros (evita typos en strings por el proyecto) ---
    private static final String ACH_FIRST_KILL = "ACH_FIRST_KILL";
    private static final String ACH_NOVICE_HUNTER = "ACH_NOVICE_HUNTER";
    private static final String ACH_FLOOR_MASTER_1 = "ACH_FLOOR_MASTER_1";
    private static final String ACH_BOSS_SLAYER = "ACH_BOSS_SLAYER";
    private static final String ACH_POCKET_MONEY = "ACH_POCKET_MONEY";
    private static final String ACH_BIG_SPENDER = "ACH_BIG_SPENDER";
    private static final String ACH_GEAR_UP = "ACH_GEAR_UP";
    private static final String ACH_THE_END = "ACH_THE_END";
    private static final String ACH_SURVIVOR = "ACH_SURVIVOR";
    private static final String ACH_TRY_AGAIN = "ACH_TRY_AGAIN";

    private final DatabaseService db;
    private final NotificationService notificationService;

    /** Definiciones/metadata de los logros (nombre, descripción, icono, recompensa, etc.). */
    private final Map<String, AchievementDefinition> definitions = new HashMap<>();

    /** Caché en memoria: id -> desbloqueado. Se mantiene por perfil cargado. */
    private final Map<String, Boolean> unlockedStatusCache = new ConcurrentHashMap<>();

    /**
     * Executor para persistencia y tareas en background (no se ejecuta en el hilo de JavaFX).
     * <p>OJO: Se apaga en {@link #shutdown()}.</p>
     */
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    // Contadores de la sesión actual (o última conocida) para triggers
    private long lastTotalKills = 0;
    private int loadedProfileId = -1;

    /**
     * Crea el servicio de logros, define los logros disponibles y carga el estado del perfil actual.
     *
     * @param db servicio de persistencia (BD)
     * @param notificationService servicio para mostrar notificaciones al desbloquear logros (puede ser null)
     */
    public AchievementService(DatabaseService db, NotificationService notificationService) {
        this.db = db;
        this.notificationService = notificationService;

        defineAllAchievements();
        loadInitialStatus();
    }

    /**
     * Recarga el estado de logros del perfil actual.
     * <p>Úsalo después de cambiar de perfil para mantener el estado aislado por perfil.</p>
     */
    public void reloadForCurrentProfile() {
        loadInitialStatus();
    }

    /**
     * Define todos los logros que pueden aparecer en la UI (metadatos).
     * <p>Solo registra definiciones; no modifica estado de desbloqueo.</p>
     */
    private void defineAllAchievements() {
        definitions.put(
                ACH_FIRST_KILL,
                new AchievementDefinition(
                        ACH_FIRST_KILL,
                        "Primera sangre",
                        "Derrota a tu primer enemigo. ¡No te detengas ahora!",
                        "assets/images/achievements/ach_first_kill.png",
                        "Desbloquea el ítem: 'Pistola de juguete'"
                )
        );

        definitions.put(
                ACH_NOVICE_HUNTER,
                new AchievementDefinition(
                        ACH_NOVICE_HUNTER,
                        "Cazador novato",
                        "Derrota a 50 enemigos (en total).",
                        "assets/images/achievements/ach_novice_hunter.png",
                        "Desbloquea el ítem: 'Ojo de águila'"
                )
        );

        definitions.put(
                ACH_FLOOR_MASTER_1,
                new AchievementDefinition(
                        ACH_FLOOR_MASTER_1,
                        "Sótano completado",
                        "Completa el piso 1.",
                        "assets/images/achievements/ach_floor_master_1.png",
                        "Desbloquea el modo: 'Modo difícil'"
                )
        );

        definitions.put(
                ACH_BOSS_SLAYER,
                new AchievementDefinition(
                        ACH_BOSS_SLAYER,
                        "Cazajefes",
                        "Derrota a tu primer jefe.",
                        "assets/images/achievements/ach_boss_slayer.png",
                        "Desbloquea el ítem: 'Moneda de la suerte'"
                )
        );

        definitions.put(
                ACH_POCKET_MONEY,
                new AchievementDefinition(
                        ACH_POCKET_MONEY,
                        "Dinero de bolsillo",
                        "Acumula 25 monedas en una sola partida.",
                        "assets/images/achievements/ach_pocket_money.png",
                        "Aumenta la probabilidad de aparición de monedas."
                )
        );

        definitions.put(
                ACH_BIG_SPENDER,
                new AchievementDefinition(
                        ACH_BIG_SPENDER,
                        "Gran gastador",
                        "Compra tu primer objeto en la tienda.",
                        "assets/images/achievements/ach_big_spender.png",
                        "Desbloquea el ítem: 'Carro de compras'"
                )
        );

        definitions.put(
                ACH_GEAR_UP,
                new AchievementDefinition(
                        ACH_GEAR_UP,
                        "Equípate",
                        "Ten 5 objetos pasivos simultáneamente.",
                        "assets/images/achievements/ach_gear_up.png",
                        "Aumenta el número de objetos que aparecen en la tienda."
                )
        );

        definitions.put(
                ACH_THE_END,
                new AchievementDefinition(
                        ACH_THE_END,
                        "El final",
                        "Gana una partida (completa el piso 5).",
                        "assets/images/achievements/ach_the_end.png",
                        "Desbloquea el ítem: 'Corazón dorado'"
                )
        );

        definitions.put(
                ACH_SURVIVOR,
                new AchievementDefinition(
                        ACH_SURVIVOR,
                        "Superviviente",
                        "Gana una partida con 1 corazón (2 HP) o menos.",
                        "assets/images/achievements/ach_survivor.png",
                        "Desbloquea el personaje: 'El Frágil'"
                )
        );

        definitions.put(
                ACH_TRY_AGAIN,
                new AchievementDefinition(
                        ACH_TRY_AGAIN,
                        "Inténtalo de nuevo",
                        "Muere por primera vez.",
                        "assets/images/achievements/ach_try_again.png",
                        "Desbloquea el ítem: 'Doble vida'"
                )
        );
    }

    /**
     * Carga el estado inicial de logros y totales del perfil actual.
     *
     * <p>
     * Además reconcilia progreso “legacy”: si el perfil ya cumple requisitos por stats persistidas
     * pero el logro aún no está reflejado en BD, lo desbloquea silenciosamente (sin notificación UI).
     * </p>
     */
    private void loadInitialStatus() {
        int profileId = AppContext.getProfileId();
        Map<String, AchievementStatus> dbStatus = db.getAchievementsStatus(profileId);

        unlockedStatusCache.clear();
        for (String id : definitions.keySet()) {
            boolean unlocked = dbStatus
                    .getOrDefault(id, new AchievementStatus(false, null))
                    .unlocked();
            unlockedStatusCache.put(id, unlocked);
        }

        lastTotalKills = db.getStatTotal(profileId, "TOTAL_KILLS");
        long totalDeaths = db.getStatTotal(profileId, "TOTAL_DEATHS");

        loadedProfileId = profileId;

        // Arreglo para perfiles antiguos: desbloqueo “silencioso” basado en stats ya guardadas.
        reconcileLegacyUnlocks(profileId, lastTotalKills, totalDeaths);

        System.out.println("[AchievementService] Loaded achievements for profile " + profileId
                + " (rows=" + dbStatus.size() + ", kills=" + lastTotalKills + ", deaths=" + totalDeaths + ").");
    }

    /**
     * Asegura que la caché en memoria corresponde al perfil activo.
     * <p>Si el perfil ha cambiado desde la última carga, recarga el estado desde persistencia.</p>
     */
    private void ensureProfileLoaded() {
        int profileId = AppContext.getProfileId();
        if (profileId != loadedProfileId) {
            loadInitialStatus();
        }
    }

    /**
     * Reconciliación de logros para perfiles que ya tenían progreso antes de existir el sistema
     * de logros (o antes de añadir ciertos logros).
     *
     * <p>
     * Desbloquea silenciosamente (sin notificación UI) cuando las stats persistidas ya cumplen
     * el requisito, pero la BD aún no lo refleja.
     * </p>
     *
     * @param profileId id del perfil activo
     * @param totalKills kills totales persistidas del perfil
     * @param totalDeaths muertes totales persistidas del perfil
     */
    private void reconcileLegacyUnlocks(int profileId, long totalKills, long totalDeaths) {
        // Si ya tiene muertes, “Try Again” no debería ser imposible de conseguir.
        unlockSilentlyIfNeeded(profileId, ACH_TRY_AGAIN, totalDeaths >= 1);

        // Opcional y seguro: logros por kills totales.
        unlockSilentlyIfNeeded(profileId, ACH_FIRST_KILL, totalKills >= 1);
        unlockSilentlyIfNeeded(profileId, ACH_NOVICE_HUNTER, totalKills >= 50);
    }

    /**
     * Desbloquea silenciosamente un logro si se cumple la condición, sin notificación UI.
     * <p>Se usa para backfilling en perfiles antiguos.</p>
     *
     * @param profileId id del perfil propietario
     * @param id identificador del logro
     * @param condition si se cumple la condición para desbloquear
     */
    private void unlockSilentlyIfNeeded(int profileId, String id, boolean condition) {
        if (!condition) return;
        if (!definitions.containsKey(id)) return;
        if (unlockedStatusCache.getOrDefault(id, false)) return;

        unlockedStatusCache.put(id, true);
        backgroundExecutor.execute(() -> db.unlockAchievement(profileId, id));
    }

    /**
     * Apaga las tareas en background usadas por el servicio.
     * <p>Llamar cuando se cierre la app o al destruir el contexto.</p>
     */
    public void shutdown() {
        backgroundExecutor.shutdownNow();
    }

    /**
     * Indica si un logro está desbloqueado para el perfil activo.
     *
     * @param id identificador del logro
     * @return true si está desbloqueado
     */
    public boolean isUnlocked(String id) {
        ensureProfileLoaded();
        return unlockedStatusCache.getOrDefault(id, false);
    }

    /**
     * Devuelve la definición (metadatos) de un logro.
     *
     * @param id identificador del logro
     * @return definición o null si no existe
     */
    public AchievementDefinition getDefinition(String id) {
        return definitions.get(id);
    }

    /**
     * Devuelve todas las definiciones de logros conocidas por el servicio.
     *
     * @return lista inmutable con todas las definiciones
     */
    public List<AchievementDefinition> getAllDefinitions() {
        return definitions.values().stream().toList();
    }

    /**
     * Desbloquea un logro para el perfil actual si aún no lo está.
     * <p>
     * Flujo:
     * <ol>
     *   <li>Actualiza caché en memoria.</li>
     *   <li>Persiste en BD en background.</li>
     *   <li>Muestra notificación en UI (JavaFX) si procede.</li>
     * </ol>
     * </p>
     *
     * @param id identificador del logro
     */
    public void unlock(String id) {
        ensureProfileLoaded();
        if (!definitions.containsKey(id)) return;
        if (unlockedStatusCache.getOrDefault(id, false)) return;

        // 1) Caché
        unlockedStatusCache.put(id, true);
        int profileId = loadedProfileId;

        // 2) Persistencia en background
        backgroundExecutor.execute(() -> db.unlockAchievement(profileId, id));

        // 3) Notificación UI
        Platform.runLater(() -> {
            AchievementDefinition def = getDefinition(id);
            if (notificationService != null && def != null) {
                notificationService.showNotification("¡LOGRO DESBLOQUEADO!", def.getName(), 3.0);
            }
        });
    }

    /**
     * Desbloquea un logro si se cumple la condición y todavía está bloqueado.
     *
     * @param id identificador del logro
     * @param condition condición necesaria para desbloquear
     */
    private void checkAndUnlock(String id, boolean condition) {
        if (condition && !isUnlocked(id)) {
            unlock(id);
        }
    }

    // =========================================================
    // Triggers / eventos
    // =========================================================

    /**
     * Evalúa logros relacionados con kills usando el total persistido del perfil.
     *
     * @param totalKills kills totales actuales del perfil activo
     */
    public void onEnemyKilled(long totalKills) {
        ensureProfileLoaded();
        lastTotalKills = totalKills;

        if (totalKills >= 1) checkAndUnlock(ACH_FIRST_KILL, true);
        if (totalKills >= 50) checkAndUnlock(ACH_NOVICE_HUNTER, true);
    }

    /**
     * Evalúa logros relacionados con muertes usando el total persistido del perfil.
     *
     * <p>
     * Usa {@code >= 1} en vez de {@code == 1} para que perfiles legacy o triggers perdidos
     * puedan desbloquear igualmente el logro.
     * </p>
     *
     * @param currentDeaths muertes totales actuales del perfil activo
     */
    public void onDeath(long currentDeaths) {
        ensureProfileLoaded();
        if (currentDeaths >= 1) checkAndUnlock(ACH_TRY_AGAIN, true);
    }

    /**
     * Evalúa logros de jefe (boss).
     */
    public void onBossKilled() {
        ensureProfileLoaded();
        checkAndUnlock(ACH_BOSS_SLAYER, true);
    }

    /**
     * Evalúa logros de compra en tienda.
     */
    public void onItemBought() {
        ensureProfileLoaded();
        checkAndUnlock(ACH_BIG_SPENDER, true);
    }

    /**
     * Evalúa logros al finalizar una partida con el resumen final.
     *
     * @param victory si la run termina en victoria
     * @param finalFloor piso final alcanzado
     * @param itemsCount número total de ítems al final de la run
     * @param maxCoins máximo de monedas alcanzado durante la run
     * @param finalHealth vida al final de la run (HP)
     */
    public void onGameEnd(boolean victory, int finalFloor, int itemsCount, int maxCoins, double finalHealth) {
        ensureProfileLoaded();

        // Hitos de progreso
        checkAndUnlock(ACH_FLOOR_MASTER_1, finalFloor >= 1);
        if (victory) checkAndUnlock(ACH_THE_END, finalFloor >= 5);

        // Ítems y economía
        checkAndUnlock(ACH_GEAR_UP, itemsCount >= 5);
        checkAndUnlock(ACH_POCKET_MONEY, maxCoins >= 25);

        // Survivor: ganar con 2 HP o menos (1 corazón).
        if (victory && finalHealth <= 2.0) checkAndUnlock(ACH_SURVIVOR, true);
    }
}
