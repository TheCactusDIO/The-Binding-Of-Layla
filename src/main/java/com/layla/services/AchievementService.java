package com.layla.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.layla.AppContext;
import com.layla.db.DatabaseService;
import com.layla.db.DatabaseService.AchievementStatus;
import com.layla.model.AchievementDefinition;
import com.layla.ui.NotificationService;

import javafx.application.Platform;

/**
 * Manages achievement definitions, per-profile unlock status, and trigger evaluation.
 *
 * <p>This service maintains an in-memory cache of unlocked states for the currently active profile.
 * Whenever the active profile changes, {@link #reloadForCurrentProfile()} should be called to keep
 * per-profile state isolated.</p>
 *
 * <p>It also supports reconciling legacy progress: if a profile already meets an achievement condition
 * (e.g., it already has deaths recorded) but the DB does not yet contain the corresponding achievement
 * row/unlock, the service can silently backfill the unlock on load.</p>
 */
public final class AchievementService {

    // --- Achievement IDs (avoid typos across the codebase) ---
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
    private final Map<String, AchievementDefinition> definitions = new HashMap<>();
    private final Map<String, Boolean> unlockedStatusCache = new ConcurrentHashMap<>();
    private final NotificationService notificationService;
    private final ScheduledExecutorService backgroundExecutor = Executors.newSingleThreadScheduledExecutor();

    // Contadores de la sesión actual (o última conocida) para triggers
    private long lastTotalKills = 0;
    private int loadedProfileId = -1;

    /**
     * Creates the achievement service and loads the status for the current profile.
     *
     * @param db service used for persistence
     * @param notificationService service used to show unlock notifications
     */
    public AchievementService(DatabaseService db, NotificationService notificationService) {
        this.db = db;
        this.notificationService = notificationService;

        defineAllAchievements();
        loadInitialStatus();
    }

    /**
     * Reloads cached achievement status for the current profile.
     * Call this after profile changes to keep per-profile state isolated.
     */
    public void reloadForCurrentProfile() {
        loadInitialStatus();
    }

    /**
     * Defines all achievement metadata that can appear in the UI.
     */
    private void defineAllAchievements() {
        definitions.put(ACH_FIRST_KILL,
            new AchievementDefinition(ACH_FIRST_KILL, "First Blood", "Derrota a tu primer enemigo. ¡No te detengas ahora!",
                "assets/images/achievements/ach_first_kill.png", "Desbloquea el ítem: 'Pistola de Juguete'"));

        definitions.put(ACH_NOVICE_HUNTER,
            new AchievementDefinition(ACH_NOVICE_HUNTER, "Novice Hunter", "Derrota a 50 enemigos (Total).",
                "assets/images/achievements/ach_novice_hunter.png", "Desbloquea el ítem: 'Ojo de Águila'"));

        definitions.put(ACH_FLOOR_MASTER_1,
            new AchievementDefinition(ACH_FLOOR_MASTER_1, "Basement Clear", "Completa el Piso 1.",
                "assets/images/achievements/ach_floor_master_1.png", "Desbloquea el modo: 'Hard Mode'"));

        definitions.put(ACH_BOSS_SLAYER,
            new AchievementDefinition(ACH_BOSS_SLAYER, "Boss Slayer", "Derrota a tu primer Jefe.",
                "assets/images/achievements/ach_boss_slayer.png", "Desbloquea el ítem: 'Moneda de la Suerte'"));

        definitions.put(ACH_POCKET_MONEY,
            new AchievementDefinition(ACH_POCKET_MONEY, "Pocket Money", "Acumula 25 monedas en una sola partida.",
                "assets/images/achievements/ach_pocket_money.png", "Aumenta la probabilidad de aparición de Monedas."));

        definitions.put(ACH_BIG_SPENDER,
            new AchievementDefinition(ACH_BIG_SPENDER, "Big Spender", "Compra tu primer objeto en la tienda.",
                "assets/images/achievements/ach_big_spender.png", "Desbloquea el ítem: 'Carro de Compras'"));

        definitions.put(ACH_GEAR_UP,
            new AchievementDefinition(ACH_GEAR_UP, "Gear Up", "Ten 5 objetos pasivos simultáneamente.",
                "assets/images/achievements/ach_gear_up.png", "Aumenta el número de objetos que aparecen en la tienda."));

        definitions.put(ACH_THE_END,
            new AchievementDefinition(ACH_THE_END, "The End", "Gana una partida (completa el Piso 5).",
                "assets/images/achievements/ach_the_end.png", "Desbloquea el ítem: 'Golden Heart'"));

        definitions.put(ACH_SURVIVOR,
            new AchievementDefinition(ACH_SURVIVOR, "Survivor", "Gana una partida con 1 Corazón (2 HP) o menos.",
                "assets/images/achievements/ach_survivor.png", "Desbloquea el personaje: 'The Fragile'"));

        definitions.put(ACH_TRY_AGAIN,
            new AchievementDefinition(ACH_TRY_AGAIN, "Try Again", "Muere por primera vez.",
                "assets/images/achievements/ach_try_again.png", "Desbloquea el ítem: 'Doble Vida'"));
    }

    /**
     * Reloads cached unlock status and totals for the current profile.
     *
     * <p>Also reconciles legacy progress: if the profile already satisfies some achievement
     * conditions (e.g., has deaths recorded) but the DB does not reflect it yet, it will
     * silently backfill the unlock in DB.</p>
     */
    private void loadInitialStatus() {
        int profileId = AppContext.getProfileId();
        Map<String, AchievementStatus> dbStatus = db.getAchievementsStatus(profileId);

        unlockedStatusCache.clear();
        for (String id : definitions.keySet()) {
            boolean unlocked = dbStatus.getOrDefault(id, new AchievementStatus(false, null)).unlocked();
            unlockedStatusCache.put(id, unlocked);
        }

        lastTotalKills = db.getStatTotal(profileId, "TOTAL_KILLS");
        long totalDeaths = db.getStatTotal(profileId, "TOTAL_DEATHS");

        loadedProfileId = profileId;

        // ✅ Fix legacy profiles: backfill unlocks based on already persisted stats.
        reconcileLegacyUnlocks(profileId, lastTotalKills, totalDeaths);

        System.out.println("[AchievementService] Loaded achievements for profile " + profileId
            + " (rows=" + dbStatus.size() + ", kills=" + lastTotalKills + ", deaths=" + totalDeaths + ").");
    }

    /**
     * Ensures the in-memory cache corresponds to the currently active profile.
     * If the profile changed since the last load, it reloads status from persistence.
     */
    private void ensureProfileLoaded() {
        int profileId = AppContext.getProfileId();
        if (profileId != loadedProfileId) {
            loadInitialStatus();
        }
    }

    /**
     * Reconciles achievements for profiles that already had progress before the achievement
     * system existed (or before some achievements were added).
     *
     * <p>This method silently unlocks achievements (no UI notification) when the persisted
     * stats already meet the requirement, but the DB does not reflect it yet.</p>
     *
     * @param profileId active profile id
     * @param totalKills persisted total kills for the profile
     * @param totalDeaths persisted total deaths for the profile
     */
    private void reconcileLegacyUnlocks(int profileId, long totalKills, long totalDeaths) {
        // If a profile already has deaths, "Try Again" should not be impossible to obtain.
        unlockSilentlyIfNeeded(profileId, ACH_TRY_AGAIN, totalDeaths >= 1);

        // Optional but safe: same idea for kill-based achievements.
        unlockSilentlyIfNeeded(profileId, ACH_FIRST_KILL, totalKills >= 1);
        unlockSilentlyIfNeeded(profileId, ACH_NOVICE_HUNTER, totalKills >= 50);
    }

    /**
     * Silently unlocks an achievement for a given profile when the condition is met,
     * without showing any UI notification.
     *
     * <p>Used for backfilling achievements when the profile already met the requirement
     * in older saves.</p>
     *
     * @param profileId owner profile id
     * @param id achievement identifier
     * @param condition whether the achievement should be unlocked
     */
    private void unlockSilentlyIfNeeded(int profileId, String id, boolean condition) {
        if (!condition) return;
        if (!definitions.containsKey(id)) return;
        if (unlockedStatusCache.getOrDefault(id, false)) return;

        unlockedStatusCache.put(id, true);
        backgroundExecutor.execute(() -> db.unlockAchievement(profileId, id));
    }

    /**
     * Shuts down background tasks used by the service.
     */
    public void shutdown() {
        backgroundExecutor.shutdownNow();
    }

    /**
     * Checks whether an achievement is unlocked for the current profile.
     *
     * @param id achievement identifier
     * @return true when unlocked for the active profile
     */
    public boolean isUnlocked(String id) {
        ensureProfileLoaded();
        return unlockedStatusCache.getOrDefault(id, false);
    }

    /**
     * Returns the definition metadata for a given achievement.
     *
     * @param id achievement identifier
     * @return definition metadata, or null if unknown
     */
    public AchievementDefinition getDefinition(String id) {
        return definitions.get(id);
    }

    /**
     * Returns every achievement definition known by the service.
     *
     * @return immutable list of achievement definitions
     */
    public List<AchievementDefinition> getAllDefinitions() {
        return definitions.values().stream().toList();
    }

    /**
     * Unlocks an achievement for the current profile if still locked.
     *
     * @param id achievement identifier
     */
    public void unlock(String id) {
        ensureProfileLoaded();
        if (!definitions.containsKey(id)) return;
        if (unlockedStatusCache.getOrDefault(id, false)) return;

        // 1) Update cache
        unlockedStatusCache.put(id, true);
        int profileId = loadedProfileId;

        // 2) Persist to DB asynchronously
        backgroundExecutor.execute(() -> db.unlockAchievement(profileId, id));

        // 3) Notify UI
        Platform.runLater(() -> {
            AchievementDefinition def = getDefinition(id);
            if (notificationService != null && def != null) {
                notificationService.showNotification("LOGRO DESBLOQUEADO!", def.getName(), 3.0);
            }
        });
    }

    /**
     * Unlocks an achievement if a condition is satisfied and it is still locked.
     *
     * @param id achievement identifier
     * @param condition condition that must be true to unlock
     */
    private void checkAndUnlock(String id, boolean condition) {
        if (condition && !isUnlocked(id)) {
            unlock(id);
        }
    }

    // --- Event Handlers (Triggers) ---

    /**
     * Evaluates enemy kill achievements using the persisted total for the profile.
     *
     * @param totalKills current total enemy kills for the active profile
     */
    public void onEnemyKilled(long totalKills) {
        ensureProfileLoaded();
        lastTotalKills = totalKills;

        if (totalKills >= 1) checkAndUnlock(ACH_FIRST_KILL, true);
        if (totalKills >= 50) checkAndUnlock(ACH_NOVICE_HUNTER, true);
    }

    /**
     * Evaluates death-related achievements using the persisted total for the profile.
     *
     * <p>Uses {@code >= 1} instead of {@code == 1} so that legacy profiles (or missed triggers)
     * are still able to unlock the achievement.</p>
     *
     * @param currentDeaths current total deaths for the active profile
     */
    public void onDeath(long currentDeaths) {
        ensureProfileLoaded();
        if (currentDeaths >= 1) checkAndUnlock(ACH_TRY_AGAIN, true);
    }

    /**
     * Evaluates boss-related achievements.
     */
    public void onBossKilled() {
        ensureProfileLoaded();
        checkAndUnlock(ACH_BOSS_SLAYER, true);
    }

    /**
     * Evaluates shop purchase achievements for the active profile.
     */
    public void onItemBought() {
        ensureProfileLoaded();
        checkAndUnlock(ACH_BIG_SPENDER, true);
    }

    /**
     * Evaluates end-of-run achievements using the final run summary.
     *
     * @param victory whether the run ended with a win
     * @param finalFloor final floor reached
     * @param itemsCount total items held at end of run
     * @param maxCoins highest coin count reached during the run
     * @param finalHealth health value at the end of the run
     */
    public void onGameEnd(boolean victory, int finalFloor, int itemsCount, int maxCoins, double finalHealth) {
        ensureProfileLoaded();

        // Hitos de Progreso
        checkAndUnlock(ACH_FLOOR_MASTER_1, finalFloor >= 1);
        if (victory) checkAndUnlock(ACH_THE_END, finalFloor >= 5);

        // Items y Economía
        checkAndUnlock(ACH_GEAR_UP, itemsCount >= 5);
        checkAndUnlock(ACH_POCKET_MONEY, maxCoins >= 25);

        // Survivor (2 HP o menos al ganar, asumiendo maxHealth es 6.0)
        if (victory && finalHealth <= 2.0) checkAndUnlock(ACH_SURVIVOR, true);
    }
}
