package com.layla.services;

import com.layla.AppContext;
import com.layla.db.DatabaseService;
import com.layla.model.AchievementDefinition;
import com.layla.db.DatabaseService.AchievementStatus;
import com.layla.ui.NotificationService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;

public final class AchievementService {

    private final DatabaseService db;
    private final Map<String, AchievementDefinition> definitions = new HashMap<>();
    private final Map<String, Boolean> unlockedStatusCache = new ConcurrentHashMap<>();
    private final NotificationService notificationService;
    private final ScheduledExecutorService backgroundExecutor = Executors.newSingleThreadScheduledExecutor();

    // Contadores de la sesión actual (o última conocida) para triggers
    private long lastTotalKills = 0;

    public AchievementService(DatabaseService db, NotificationService notificationService) {
        this.db = db;
        this.notificationService = notificationService;

        defineAllAchievements();
        loadInitialStatus();
    }

    private void defineAllAchievements() {
        // --- 10 Logros Propuestos ---
        definitions.put("ACH_FIRST_KILL",
            new AchievementDefinition("ACH_FIRST_KILL", "First Blood", "Derrota a tu primer enemigo. ¡No te detengas ahora!",
                                      "assets/images/achievements/ach_first_kill.png", "Desbloquea el ítem: 'Pistola de Juguete'"));
        definitions.put("ACH_NOVICE_HUNTER",
            new AchievementDefinition("ACH_NOVICE_HUNTER", "Novice Hunter", "Derrota a 50 enemigos (Total).",
                                      "assets/images/achievements/ach_novice_hunter.png", "Desbloquea el ítem: 'Ojo de Águila'"));
        definitions.put("ACH_FLOOR_MASTER_1",
            new AchievementDefinition("ACH_FLOOR_MASTER_1", "Basement Clear", "Completa el Piso 1.",
                                      "assets/images/achievements/ach_floor_master_1.png", "Desbloquea el modo: 'Hard Mode'"));
        definitions.put("ACH_BOSS_SLAYER",
            new AchievementDefinition("ACH_BOSS_SLAYER", "Boss Slayer", "Derrota a tu primer Jefe.",
                                      "assets/images/achievements/ach_boss_slayer.png", "Desbloquea el ítem: 'Moneda de la Suerte'"));
        definitions.put("ACH_POCKET_MONEY",
            new AchievementDefinition("ACH_POCKET_MONEY", "Pocket Money", "Acumula 25 monedas en una sola partida.",
                                      "assets/images/achievements/ach_pocket_money.png", "Aumenta la probabilidad de aparición de Monedas."));
        definitions.put("ACH_BIG_SPENDER",
            new AchievementDefinition("ACH_BIG_SPENDER", "Big Spender", "Compra tu primer objeto en la tienda.",
                                      "assets/images/achievements/ach_big_spender.png", "Desbloquea el ítem: 'Carro de Compras'"));
        definitions.put("ACH_GEAR_UP",
            new AchievementDefinition("ACH_GEAR_UP", "Gear Up", "Ten 5 objetos pasivos simultáneamente.",
                                      "assets/images/achievements/ach_gear_up.png", "Aumenta el número de objetos que aparecen en la tienda."));
        definitions.put("ACH_THE_END",
            new AchievementDefinition("ACH_THE_END", "The End", "Gana una partida (completa el Piso 5).",
                                      "assets/images/achievements/ach_the_end.png", "Desbloquea el ítem: 'Golden Heart'"));
        definitions.put("ACH_SURVIVOR",
            new AchievementDefinition("ACH_SURVIVOR", "Survivor", "Gana una partida con 1 Corazón (2 HP) o menos.",
                                      "assets/images/achievements/ach_survivor.png", "Desbloquea el personaje: 'The Fragile'"));
        definitions.put("ACH_TRY_AGAIN",
            new AchievementDefinition("ACH_TRY_AGAIN", "Try Again", "Muere por primera vez.",
                                      "assets/images/achievements/ach_try_again.png", "Desbloquea el ítem: 'Doble Vida'"));
    }

    private void loadInitialStatus() {
        int profileId = AppContext.getProfileId();
        Map<String, AchievementStatus> dbStatus = db.getAchievementsStatus(profileId);
        unlockedStatusCache.clear();
        for (String id : definitions.keySet()) {
            boolean unlocked = dbStatus.getOrDefault(id, new AchievementStatus(false, null)).unlocked();
            unlockedStatusCache.put(id, unlocked);
        }

        lastTotalKills = db.getStatTotal(profileId, "TOTAL_KILLS");
    }

    public void shutdown() {
        backgroundExecutor.shutdownNow();
    }

    public boolean isUnlocked(String id) {
        return unlockedStatusCache.getOrDefault(id, false);
    }

    public AchievementDefinition getDefinition(String id) {
        return definitions.get(id);
    }

    public List<AchievementDefinition> getAllDefinitions() {
        return definitions.values().stream().toList();
    }

    public void unlock(String id) {
        if (!definitions.containsKey(id)) return;
        if (isUnlocked(id)) return;

        // 1. Update cache
        unlockedStatusCache.put(id, true);

        // 2. Persist to DB asynchronously
        backgroundExecutor.execute(() -> db.unlockAchievement(AppContext.getProfileId(), id));

        // 3. Notify UI
        Platform.runLater(() -> {
            AchievementDefinition def = getDefinition(id);
            if (notificationService != null) {
                notificationService.showNotification("LOGRO DESBLOQUEADO!", def.getName(), 3.0);
            }
        });
    }

    private void checkAndUnlock(String id, boolean condition) {
        if (condition && !isUnlocked(id)) {
            unlock(id);
        }
    }

    // --- Event Handlers (Triggers) ---

    public void onEnemyKilled(long totalKills) {
        lastTotalKills = totalKills;
        if (totalKills >= 1) checkAndUnlock("ACH_FIRST_KILL", true);
        if (totalKills >= 50) checkAndUnlock("ACH_NOVICE_HUNTER", true);
    }

    public void onDeath(long currentDeaths) {
        if (currentDeaths == 1) checkAndUnlock("ACH_TRY_AGAIN", true);
    }

    public void onBossKilled() {
        checkAndUnlock("ACH_BOSS_SLAYER", true);
    }

    public void onItemBought() {
        checkAndUnlock("ACH_BIG_SPENDER", true);
    }

    public void onGameEnd(boolean victory, int finalFloor, int itemsCount, int maxCoins, double finalHealth) {
        // Hitos de Progreso
        checkAndUnlock("ACH_FLOOR_MASTER_1", finalFloor >= 1);
        if (victory) checkAndUnlock("ACH_THE_END", finalFloor >= 5);

        // Items y Economía
        checkAndUnlock("ACH_GEAR_UP", itemsCount >= 5);
        checkAndUnlock("ACH_POCKET_MONEY", maxCoins >= 25);

        // Survivor (2 HP o menos al ganar, asumiendo maxHealth es 6.0)
        if (victory && finalHealth <= 2.0) checkAndUnlock("ACH_SURVIVOR", true);
    }
}
