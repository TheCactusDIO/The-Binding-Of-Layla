package com.layla.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import com.layla.model.EnemyProfile;
import com.layla.model.EnemyType;
import com.layla.model.PlayerStatId;
import com.layla.services.StatsService;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

public class StatsPanelController {
    // Player & player-projectile fields
    @FXML private TextField startHpField, maxHpField, moveSpeedField;
    @FXML private TextField fireRateField, projSpeedField, projRangeField, projDamageField;

    @FXML private CheckBox persistCheck;
    @FXML private ComboBox<EnemyType> enemyTypeCombo;
    @FXML private TextField profileHpField, profileSpeedField, profileContactField, profileFireField, profileJitterField,
                            profileProjSpeedField, profileProjRangeField, profileProjDmgField, profileScoreField;
    @FXML private CheckBox stationaryCheck;
    @FXML private Button saveBtn;

    private Runnable onClose = () -> {};
    /** Called whenever stats/balance change so caller (HUD/game) can refresh immediately. */
    private Runnable onStatsChanged = () -> {};
    private StatsService stats = com.layla.AppContext.stats();
    private final Map<TextField, ChangeListener<String>> profileBindings = new HashMap<>();
    private ChangeListener<Boolean> stationaryBinding;
    private final Path enemyBalancePath = Path.of(
            System.getProperty("user.home"),
            ".layla",
            "enemy_balance.json"
    );

    public void setOnClose(Runnable r)          { this.onClose = (r != null) ? r : () -> {}; }
    public void setOnStatsChanged(Runnable r)   { this.onStatsChanged = (r != null) ? r : () -> {}; }
    public void setStatsService(StatsService s) { if (s != null) this.stats = s; }

    @FXML
    private void initialize() {
        // Live bindings so valid numbers apply immediately while typing.
        installLiveBindings();
        setupEnemyProfilesPanel();
    }

    private void setupEnemyProfilesPanel() {
        if (enemyTypeCombo == null) return;
        enemyTypeCombo.getItems().setAll(EnemyType.values());
        enemyTypeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldT, newT) -> {
            if (newT != null) {
                bindProfile(com.layla.AppContext.balance().profile(newT));
            }
        });
        if (!enemyTypeCombo.getItems().isEmpty()) {
            enemyTypeCombo.getSelectionModel().select(EnemyType.SHOOTER);
            refreshSelectedProfile();
        }

        if (saveBtn != null) {
            saveBtn.setOnAction(e -> saveProfilesAsync());
        }

        loadProfilesAsync();
    }

    /** Preload UI from global balance/stats. */
    public void onShow() {
        var bal = com.layla.AppContext.balance();

        // Player HP
        put(startHpField, bal.startHp);
        put(maxHpField,   bal.maxHp);

        // Player stats (StatsService)
        var baseStats = stats.getBaseStats();
        put(moveSpeedField, baseStats.getBase(PlayerStatId.MOVE_SPEED));
        put(fireRateField,  baseStats.getBase(PlayerStatId.FIRE_RATE));
        put(projSpeedField, baseStats.getBase(PlayerStatId.PROJECTILE_SPEED));
        put(projRangeField, baseStats.getBase(PlayerStatId.PROJECTILE_RANGE));
        put(projDamageField,baseStats.getBase(PlayerStatId.PROJECTILE_DAMAGE));

        // Optionally override with user JSON if present
        loadUserJsonIfExists();
        refreshSelectedProfile();
    }

    private void bindProfile(EnemyProfile profile) {
        if (profile == null) return;
        bindNumberField(profileHpField,        () -> profile.baseHp,     v -> profile.baseHp = v);
        bindNumberField(profileSpeedField,     () -> profile.speed,      v -> profile.speed = v);
        bindNumberField(profileContactField,   () -> profile.contactDmg, v -> profile.contactDmg = v);
        bindNumberField(profileFireField,      () -> profile.fireRate,   v -> profile.fireRate = v);
        bindNumberField(profileJitterField,    () -> profile.jitter,     v -> profile.jitter = v);
        bindNumberField(profileProjSpeedField, () -> profile.projSpeed,  v -> profile.projSpeed = v);
        bindNumberField(profileProjRangeField, () -> profile.projRange,  v -> profile.projRange = v);
        bindNumberField(profileProjDmgField,   () -> profile.projDamage, v -> profile.projDamage = v);

        // Score por enemigo (int internamente, editable como número)
        bindNumberField(
            profileScoreField,
            () -> (double) profile.score,
            v  -> profile.score = (int) Math.round(v)
        );

        bindStationary(profile);
    }

    private void bindNumberField(TextField tf, DoubleSupplier getter, DoubleConsumer setter) {
        if (tf == null || getter == null || setter == null) return;
        ChangeListener<String> prev = profileBindings.remove(tf);
        if (prev != null) tf.textProperty().removeListener(prev);
        tf.setText(Double.toString(getter.getAsDouble()));
        ChangeListener<String> listener = (obs, oldV, newV) -> {
            Double val = tryParse(newV);
            if (val != null) {
                setter.accept(val);
            }
        };
        tf.textProperty().addListener(listener);
        profileBindings.put(tf, listener);
    }

    private void bindStationary(EnemyProfile profile) {
        if (stationaryCheck == null) return;
        if (stationaryBinding != null) {
            stationaryCheck.selectedProperty().removeListener(stationaryBinding);
        }
        stationaryCheck.setSelected(profile.stationary);
        stationaryBinding = (obs, oldV, newV) -> profile.stationary = Boolean.TRUE.equals(newV);
        stationaryCheck.selectedProperty().addListener(stationaryBinding);
    }

    private void refreshSelectedProfile() {
        if (enemyTypeCombo == null) return;
        EnemyType selected = enemyTypeCombo.getSelectionModel().getSelectedItem();
        if (selected != null) {
            bindProfile(com.layla.AppContext.balance().profile(selected));
        }
    }

    private void loadProfilesAsync() {
        CompletableFuture
            .runAsync(() -> com.layla.AppContext.balance().loadFromJson(enemyBalancePath))
            .thenRun(() -> Platform.runLater(this::refreshSelectedProfile));
    }

    private void saveProfilesAsync() {
        CompletableFuture.runAsync(() -> com.layla.AppContext.balance().saveToJson(enemyBalancePath));
    }

    // --------- Actions ---------

    @FXML
    private void onApply() {
        var bal = com.layla.AppContext.balance();

        // Player HP (balance)
        bal.startHp = get(startHpField, bal.startHp);
        bal.maxHp   = get(maxHpField,   bal.maxHp);
        stats.setBaseStat(PlayerStatId.MAX_HEALTH, bal.maxHp);

        // Player/shooting stats (StatsService)
        stats.setBaseStat(PlayerStatId.MOVE_SPEED,        get(moveSpeedField, stats.getBaseStat(PlayerStatId.MOVE_SPEED)));
        stats.setBaseStat(PlayerStatId.FIRE_RATE,         get(fireRateField,  stats.getBaseStat(PlayerStatId.FIRE_RATE)));
        stats.setBaseStat(PlayerStatId.PROJECTILE_SPEED,  get(projSpeedField, stats.getBaseStat(PlayerStatId.PROJECTILE_SPEED)));
        stats.setBaseStat(PlayerStatId.PROJECTILE_RANGE,  get(projRangeField, stats.getBaseStat(PlayerStatId.PROJECTILE_RANGE)));
        stats.setBaseStat(PlayerStatId.PROJECTILE_DAMAGE, get(projDamageField,stats.getBaseStat(PlayerStatId.PROJECTILE_DAMAGE)));

        if (persistCheck.isSelected()) saveUserJson();

        onStatsChanged.run();
        onClose.run();
    }

    @FXML
    private void onReset() {
        // Restore defaults for both player stats and balance
        stats.getBaseStats().resetDefaults();
        var bal = com.layla.AppContext.balance();
        bal.resetDefaults();

        // Repaint fields
        onShow();

        if (persistCheck.isSelected()) saveUserJson();

        onStatsChanged.run();
    }

    @FXML
    private void onCancel() { onClose.run(); }

    // ---------- Live bindings ----------
    private void installLiveBindings() {
        Consumer<TextField> selectAllOnFocus = tf -> tf.focusedProperty().addListener((o, oldV, newV) -> {
            if (Boolean.TRUE.equals(newV)) tf.selectAll();
        });

        // Focus helpers
        for (TextField tf : new TextField[] {
                startHpField, maxHpField,
                moveSpeedField, fireRateField, projSpeedField, projRangeField, projDamageField,
                profileHpField, profileSpeedField, profileContactField, profileFireField,
                profileJitterField, profileProjSpeedField, profileProjRangeField, profileProjDmgField,
                profileScoreField
        }) {
            if (tf != null) selectAllOnFocus.accept(tf);
        }

        // Player stats (StatsService)
        liveNumber(moveSpeedField, v -> { stats.setBaseStat(PlayerStatId.MOVE_SPEED, v); onStatsChanged.run(); });
        liveNumber(fireRateField,  v -> { stats.setBaseStat(PlayerStatId.FIRE_RATE, v); onStatsChanged.run(); });
        liveNumber(projSpeedField, v -> { stats.setBaseStat(PlayerStatId.PROJECTILE_SPEED, v); onStatsChanged.run(); });
        liveNumber(projRangeField, v -> { stats.setBaseStat(PlayerStatId.PROJECTILE_RANGE, v); onStatsChanged.run(); });
        liveNumber(projDamageField,v -> { stats.setBaseStat(PlayerStatId.PROJECTILE_DAMAGE, v); onStatsChanged.run(); });

        // Balance (player HP)
        liveNumber(startHpField, v -> { var b = com.layla.AppContext.balance(); b.startHp = v; onStatsChanged.run(); });
        liveNumber(maxHpField,   v -> {
            var b = com.layla.AppContext.balance();
            b.maxHp = v;
            stats.setBaseStat(PlayerStatId.MAX_HEALTH, v);
            onStatsChanged.run();
        });
    }

    private void liveNumber(TextField tf, Consumer<Double> onValidNumber) {
        if (tf == null) return;
        tf.textProperty().addListener((obs, oldV, newV) -> {
            Double val = tryParse(newV);
            if (val != null) {
                onValidNumber.accept(val);
            }
        });
    }

    private static Double tryParse(String s) {
        try { return Double.parseDouble(s.trim()); }
        catch (Exception e) { return null; }
    }

    // ---------- IO helpers ----------
    private static void put(TextField f, double v) { if (f != null) f.setText(Double.toString(v)); }
    private static double get(TextField f, double def) {
        try { return Double.parseDouble(f.getText().trim()); } catch (Exception e) { return def; }
    }

    private Path cfgPath() {
        String home = System.getProperty("user.home");
        return Path.of(System.getProperty("user.home"), ".layla", "stats.json");
    }

    private void loadUserJsonIfExists() {
        try {
            Path p = cfgPath();
            if (!Files.exists(p)) return;
            String json = Files.readString(p);
            Map<String, Double> m = Json.minimalParse(json);
            if (m == null) return;

            var bal = com.layla.AppContext.balance();

            if (m.containsKey("startHp")) bal.startHp = m.get("startHp");
            if (m.containsKey("maxHp")) {
                bal.maxHp = m.get("maxHp");
                stats.setBaseStat(PlayerStatId.MAX_HEALTH, bal.maxHp);
            }

            if (m.containsKey("moveSpeed"))  stats.setBaseStat(PlayerStatId.MOVE_SPEED,       m.get("moveSpeed"));
            if (m.containsKey("fireRate"))   stats.setBaseStat(PlayerStatId.FIRE_RATE,        m.get("fireRate"));
            if (m.containsKey("projSpeed"))  stats.setBaseStat(PlayerStatId.PROJECTILE_SPEED, m.get("projSpeed"));
            if (m.containsKey("projRange"))  stats.setBaseStat(PlayerStatId.PROJECTILE_RANGE, m.get("projRange"));
            if (m.containsKey("projDamage")) stats.setBaseStat(PlayerStatId.PROJECTILE_DAMAGE,m.get("projDamage"));

            // Back-compat keys
            if (m.containsKey("fireCooldown")) {
                double cooldown = m.get("fireCooldown");
                double rate = cooldown > 0.0 ? 1.0 / cooldown : 0.0;
                stats.setBaseStat(PlayerStatId.FIRE_RATE, rate);
            }
            if (m.containsKey("rangePixels")) {
                double pixels = m.get("rangePixels");
                double speed = stats.getBaseStat(PlayerStatId.PROJECTILE_SPEED);
                if (speed > 0.0) stats.setBaseStat(PlayerStatId.PROJECTILE_RANGE, pixels / speed);
            }
            if (m.containsKey("damage")) {
                stats.setBaseStat(PlayerStatId.PROJECTILE_DAMAGE, m.get("damage"));
            }

            // Las claves enemy* se ignoran ahora (los enemigos se configuran por perfil)
        } catch (Exception ignored) {}
    }

    private void saveUserJson() {
        try {
            var bal = com.layla.AppContext.balance();

            Path p = cfgPath();
            Files.createDirectories(p.getParent());

            java.util.LinkedHashMap<String, Double> m = new java.util.LinkedHashMap<>();
            m.put("startHp",       bal.startHp);
            m.put("maxHp",         bal.maxHp);
            m.put("moveSpeed",     stats.getBaseStat(PlayerStatId.MOVE_SPEED));
            m.put("fireRate",      stats.getBaseStat(PlayerStatId.FIRE_RATE));
            m.put("projSpeed",     stats.getBaseStat(PlayerStatId.PROJECTILE_SPEED));
            m.put("projRange",     stats.getBaseStat(PlayerStatId.PROJECTILE_RANGE));
            m.put("projDamage",    stats.getBaseStat(PlayerStatId.PROJECTILE_DAMAGE));
            // No guardamos enemy* aquí; los enemigos van en enemy_balance.json

            String json = Json.toJson(m);
            Files.writeString(p, json);
        } catch (Exception ignored) {}
    }

    /** Minimal JSON (no libs) */
    static final class Json {
        static Map<String, Double> minimalParse(String json) {
            try {
                java.util.HashMap<String, Double> out = new java.util.HashMap<>();
                String s = json.trim();
                if (!s.startsWith("{") || !s.endsWith("}")) return null;
                s = s.substring(1, s.length() - 1).trim();
                if (s.isEmpty()) return out;
                for (String part : s.split(",")) {
                    String[] kv = part.split(":");
                    String k = kv[0].trim().replaceAll("^\"|\"$", "");
                    Double v = Double.parseDouble(kv[1].trim());
                    out.put(k, v);
                }
                return out;
            } catch (Exception e) { return null; }
        }
        static String toJson(Map<String, Double> m) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(e.getKey()).append('"').append(':').append(e.getValue());
            }
            return sb.append('}').toString();
        }
    }
}
