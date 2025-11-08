package com.layla.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

import com.layla.model.StatType;
import com.layla.services.StatsService;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;

public class StatsPanelController {
    // Player & player-projectile fields
    @FXML private TextField startHpField, maxHpField, moveSpeedField;
    @FXML private TextField fireRateField, projSpeedField, projRangeField, projDamageField;

    // Enemy balance fields
    @FXML private TextField enemyHpField, enemySpeedField, enemyScoreKField;
    @FXML private TextField enemyContactDmgField;

    // Enemy projectile fields (NEW)
    @FXML private TextField enemyProjSpeedField, enemyProjRangeField, enemyProjDamageField;

    @FXML private CheckBox persistCheck;

    private Runnable onClose = () -> {};
    /** Called whenever stats/balance change so caller (HUD/game) can refresh immediately. */
    private Runnable onStatsChanged = () -> {};
    private StatsService stats = com.layla.AppContext.stats();

    public void setOnClose(Runnable r)          { this.onClose = (r != null) ? r : () -> {}; }
    public void setOnStatsChanged(Runnable r)   { this.onStatsChanged = (r != null) ? r : () -> {}; }
    public void setStatsService(StatsService s) { if (s != null) this.stats = s; }

    @FXML
    private void initialize() {
        // Live bindings so valid numbers apply immediately while typing.
        installLiveBindings();
    }

    /** Preload UI from global balance/stats. */
    public void onShow() {
        var bal = com.layla.AppContext.balance();

        // Player HP
        put(startHpField, bal.startHp);
        put(maxHpField,   bal.maxHp);

        // Player stats (StatsService)
        put(moveSpeedField, stats.getStat(StatType.MOVE_SPEED));
        put(fireRateField,  stats.getStat(StatType.FIRE_RATE));
        put(projSpeedField, stats.getStat(StatType.PROJECTILE_SPEED));
        put(projRangeField, stats.getStat(StatType.PROJECTILE_RANGE));
        put(projDamageField,stats.getStat(StatType.PROJECTILE_DAMAGE));

        // Enemy base
        put(enemyHpField,     bal.enemyBaseHp);
        put(enemySpeedField,  bal.enemySpeedAvg);
        put(enemyScoreKField, bal.enemyScoreK);
        put(enemyContactDmgField, bal.enemyContactDamage);

        // Enemy projectiles
        put(enemyProjSpeedField,  bal.enemyProjSpeed);
        put(enemyProjRangeField,  bal.enemyProjRange);
        put(enemyProjDamageField, bal.enemyProjDamage);

        // Optionally override with user JSON if present
        loadUserJsonIfExists();
    }

    // --------- Actions ---------

    @FXML
    private void onApply() {
        var bal = com.layla.AppContext.balance();

        // Player HP (balance)
        bal.startHp = get(startHpField, bal.startHp);
        bal.maxHp   = get(maxHpField,   bal.maxHp);

        // Player/shooting stats (StatsService)
        stats.setStat(StatType.MOVE_SPEED,        get(moveSpeedField, stats.getStat(StatType.MOVE_SPEED)));
        stats.setStat(StatType.FIRE_RATE,         get(fireRateField,  stats.getStat(StatType.FIRE_RATE)));
        stats.setStat(StatType.PROJECTILE_SPEED,  get(projSpeedField, stats.getStat(StatType.PROJECTILE_SPEED)));
        stats.setStat(StatType.PROJECTILE_RANGE,  get(projRangeField, stats.getStat(StatType.PROJECTILE_RANGE)));
        stats.setStat(StatType.PROJECTILE_DAMAGE, get(projDamageField,stats.getStat(StatType.PROJECTILE_DAMAGE)));

        // Enemy base (balance)
        bal.enemyBaseHp   = get(enemyHpField,    bal.enemyBaseHp);
        bal.enemySpeedAvg = get(enemySpeedField, bal.enemySpeedAvg);
        bal.enemyScoreK   = get(enemyScoreKField,bal.enemyScoreK);
        bal.enemyContactDamage = get(enemyContactDmgField, bal.enemyContactDamage);

        // Enemy projectiles (balance) (NEW)
        bal.enemyProjSpeed  = get(enemyProjSpeedField,  bal.enemyProjSpeed);
        bal.enemyProjRange  = get(enemyProjRangeField,  bal.enemyProjRange);
        bal.enemyProjDamage = get(enemyProjDamageField, bal.enemyProjDamage);

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
                enemyHpField, enemySpeedField, enemyScoreKField,
                enemyProjSpeedField, enemyProjRangeField, enemyProjDamageField
        }) {
            if (tf != null) selectAllOnFocus.accept(tf);
        }

        // Player stats (StatsService)
        liveNumber(moveSpeedField, v -> { stats.setStat(StatType.MOVE_SPEED, v); onStatsChanged.run(); });
        liveNumber(fireRateField,  v -> { stats.setStat(StatType.FIRE_RATE, v); onStatsChanged.run(); });
        liveNumber(projSpeedField, v -> { stats.setStat(StatType.PROJECTILE_SPEED, v); onStatsChanged.run(); });
        liveNumber(projRangeField, v -> { stats.setStat(StatType.PROJECTILE_RANGE, v); onStatsChanged.run(); });
        liveNumber(projDamageField,v -> { stats.setStat(StatType.PROJECTILE_DAMAGE, v); onStatsChanged.run(); });

        // Balance (player HP + enemy base)
        liveNumber(startHpField, v -> { var b = com.layla.AppContext.balance(); b.startHp = v; onStatsChanged.run(); });
        liveNumber(maxHpField,   v -> { var b = com.layla.AppContext.balance(); b.maxHp   = v; onStatsChanged.run(); });
        liveNumber(enemyHpField, v -> { var b = com.layla.AppContext.balance(); b.enemyBaseHp   = v; onStatsChanged.run(); });
        liveNumber(enemySpeedField, v -> { var b = com.layla.AppContext.balance(); b.enemySpeedAvg = v; onStatsChanged.run(); });
        liveNumber(enemyScoreKField, v -> { var b = com.layla.AppContext.balance(); b.enemyScoreK   = v; onStatsChanged.run(); });
        liveNumber(enemyContactDmgField, v -> {var b = com.layla.AppContext.balance(); b.enemyContactDamage = v; onStatsChanged.run();});


        // Enemy projectiles (NEW)
        liveNumber(enemyProjSpeedField,  v -> { var b = com.layla.AppContext.balance(); b.enemyProjSpeed  = v; onStatsChanged.run(); });
        liveNumber(enemyProjRangeField,  v -> { var b = com.layla.AppContext.balance(); b.enemyProjRange  = v; onStatsChanged.run(); });
        liveNumber(enemyProjDamageField, v -> { var b = com.layla.AppContext.balance(); b.enemyProjDamage = v; onStatsChanged.run(); });
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
        return Path.of(home, ".layla", "stats.json");
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
            if (m.containsKey("maxHp"))   bal.maxHp   = m.get("maxHp");

            if (m.containsKey("moveSpeed"))  stats.setStat(StatType.MOVE_SPEED,       m.get("moveSpeed"));
            if (m.containsKey("fireRate"))   stats.setStat(StatType.FIRE_RATE,        m.get("fireRate"));
            if (m.containsKey("projSpeed"))  stats.setStat(StatType.PROJECTILE_SPEED, m.get("projSpeed"));
            if (m.containsKey("projRange"))  stats.setStat(StatType.PROJECTILE_RANGE, m.get("projRange"));
            if (m.containsKey("projDamage")) stats.setStat(StatType.PROJECTILE_DAMAGE,m.get("projDamage"));

            // Back-compat keys
            if (m.containsKey("fireCooldown")) {
                double cooldown = m.get("fireCooldown");
                double rate = cooldown > 0.0 ? 1.0 / cooldown : 0.0;
                stats.setStat(StatType.FIRE_RATE, rate);
            }
            if (m.containsKey("rangePixels")) {
                double pixels = m.get("rangePixels");
                double speed = stats.getStat(StatType.PROJECTILE_SPEED);
                if (speed > 0.0) stats.setStat(StatType.PROJECTILE_RANGE, pixels / speed);
            }
            if (m.containsKey("damage")) {
                stats.setStat(StatType.PROJECTILE_DAMAGE, m.get("damage"));
            }

            if (m.containsKey("enemyBaseHp"))  bal.enemyBaseHp   = m.get("enemyBaseHp");
            if (m.containsKey("enemySpeed"))   bal.enemySpeedAvg = m.get("enemySpeed");
            if (m.containsKey("enemyScoreK"))  bal.enemyScoreK   = m.get("enemyScoreK");

            // Enemy projectile keys (NEW)
            if (m.containsKey("enemyProjSpeed"))  bal.enemyProjSpeed  = m.get("enemyProjSpeed");
            if (m.containsKey("enemyProjRange"))  bal.enemyProjRange  = m.get("enemyProjRange");
            if (m.containsKey("enemyProjDamage")) bal.enemyProjDamage = m.get("enemyProjDamage");
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
            m.put("moveSpeed",     stats.getStat(StatType.MOVE_SPEED));
            m.put("fireRate",      stats.getStat(StatType.FIRE_RATE));
            m.put("projSpeed",     stats.getStat(StatType.PROJECTILE_SPEED));
            m.put("projRange",     stats.getStat(StatType.PROJECTILE_RANGE));
            m.put("projDamage",    stats.getStat(StatType.PROJECTILE_DAMAGE));
            m.put("enemyBaseHp",   bal.enemyBaseHp);
            m.put("enemySpeed",    bal.enemySpeedAvg);
            m.put("enemyScoreK",   bal.enemyScoreK);
            m.put("enemyContactDamage", bal.enemyContactDamage);
            // NEW enemy projectile fields
            m.put("enemyProjSpeed",  bal.enemyProjSpeed);
            m.put("enemyProjRange",  bal.enemyProjRange);
            m.put("enemyProjDamage", bal.enemyProjDamage);

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
