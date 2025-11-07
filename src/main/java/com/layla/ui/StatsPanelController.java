package com.layla.ui;

import com.layla.model.StatType;
import com.layla.services.StatsService;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

public class StatsPanelController {
    @FXML private TextField startHpField, maxHpField, moveSpeedField;
    @FXML private TextField fireRateField, projSpeedField, projRangeField, projDamageField;
    @FXML private TextField enemyHpField, enemySpeedField, enemyScoreKField;
    @FXML private CheckBox persistCheck;

    private Runnable onClose = () -> {};
    /** Se llama cuando cambian stats/balance (para refrescar HUD, clamping de HP, etc.). */
    private Runnable onStatsChanged = () -> {};
    private StatsService stats = com.layla.AppContext.stats();

    public void setOnClose(Runnable r)            { this.onClose = (r!=null)? r : ()->{}; }
    public void setOnStatsChanged(Runnable r)     { this.onStatsChanged = (r!=null)? r : ()->{}; }
    public void setStatsService(StatsService s)   { if (s!=null) this.stats = s; }

    @FXML
    private void initialize() {
        // Enlazados "live": al escribir valores válidos se aplican al momento.
        installLiveBindings();
    }

    /** Precarga campos desde el balance/stats globales. */
    public void onShow() {
        var bal = com.layla.AppContext.balance();

        put(startHpField, bal.startHp);
        put(maxHpField,   bal.maxHp);

        put(moveSpeedField, stats.getStat(StatType.MOVE_SPEED));
        put(fireRateField,  stats.getStat(StatType.FIRE_RATE));
        put(projSpeedField, stats.getStat(StatType.PROJECTILE_SPEED));
        put(projRangeField, stats.getStat(StatType.PROJECTILE_RANGE));
        put(projDamageField,stats.getStat(StatType.PROJECTILE_DAMAGE));

        put(enemyHpField,     bal.enemyBaseHp);
        put(enemySpeedField,  bal.enemySpeedAvg);
        put(enemyScoreKField, bal.enemyScoreK);

        loadUserJsonIfExists(); // opcional: sobreescribe con fichero del usuario
    }

    // --------- ACCIONES ---------

    @FXML
    private void onApply() {
        var bal = com.layla.AppContext.balance();

        // Player HP (balance global)
        bal.startHp = get(startHpField, bal.startHp);
        bal.maxHp   = get(maxHpField,   bal.maxHp);

        // Player/shooting stats (StatsService global)
        stats.setStat(StatType.MOVE_SPEED,        get(moveSpeedField, stats.getStat(StatType.MOVE_SPEED)));
        stats.setStat(StatType.FIRE_RATE,         get(fireRateField,  stats.getStat(StatType.FIRE_RATE)));
        stats.setStat(StatType.PROJECTILE_SPEED,  get(projSpeedField, stats.getStat(StatType.PROJECTILE_SPEED)));
        stats.setStat(StatType.PROJECTILE_RANGE,  get(projRangeField, stats.getStat(StatType.PROJECTILE_RANGE)));
        stats.setStat(StatType.PROJECTILE_DAMAGE, get(projDamageField,stats.getStat(StatType.PROJECTILE_DAMAGE)));

        // Enemy defaults (balance global)
        bal.enemyBaseHp   = get(enemyHpField,    bal.enemyBaseHp);
        bal.enemySpeedAvg = get(enemySpeedField, bal.enemySpeedAvg);
        bal.enemyScoreK   = get(enemyScoreKField,bal.enemyScoreK);

        if (persistCheck.isSelected()) saveUserJson();

        onStatsChanged.run(); // notifica para HUD/HP clamp etc.
        onClose.run();
    }

    @FXML
    private void onReset() {
        // Restaurar por defecto
        stats.getBaseStats().resetDefaults();
        var bal = com.layla.AppContext.balance();
        bal.resetDefaults();

        // Repintar campos
        onShow();

        if (persistCheck.isSelected()) saveUserJson();

        onStatsChanged.run(); // refresco inmediato
    }

    @FXML
    private void onCancel() { onClose.run(); }

    // ---------- LIVE BINDINGS (actualiza al escribir si el valor es válido) ----------
    private void installLiveBindings() {
        // Helper para stats numéricos
        Consumer<TextField> selectAllOnFocus = tf -> tf.focusedProperty().addListener((o, oldV, newV) -> {
            if (Boolean.TRUE.equals(newV)) tf.selectAll();
        });

        selectAllOnFocus.accept(startHpField);
        selectAllOnFocus.accept(maxHpField);
        selectAllOnFocus.accept(moveSpeedField);
        selectAllOnFocus.accept(fireRateField);
        selectAllOnFocus.accept(projSpeedField);
        selectAllOnFocus.accept(projRangeField);
        selectAllOnFocus.accept(projDamageField);
        selectAllOnFocus.accept(enemyHpField);
        selectAllOnFocus.accept(enemySpeedField);
        selectAllOnFocus.accept(enemyScoreKField);

        // Stats del jugador/armas (se aplican si el texto es parseable)
        liveNumber(moveSpeedField, v -> { stats.setStat(StatType.MOVE_SPEED, v); onStatsChanged.run(); });
        liveNumber(fireRateField,  v -> { stats.setStat(StatType.FIRE_RATE, v); onStatsChanged.run(); });
        liveNumber(projSpeedField, v -> { stats.setStat(StatType.PROJECTILE_SPEED, v); onStatsChanged.run(); });
        liveNumber(projRangeField, v -> { stats.setStat(StatType.PROJECTILE_RANGE, v); onStatsChanged.run(); });
        liveNumber(projDamageField,v -> { stats.setStat(StatType.PROJECTILE_DAMAGE, v); onStatsChanged.run(); });

        // Balance (HP y enemigos)
        liveNumber(startHpField, v -> { var b = com.layla.AppContext.balance(); b.startHp = v; onStatsChanged.run(); });
        liveNumber(maxHpField,   v -> { var b = com.layla.AppContext.balance(); b.maxHp   = v; onStatsChanged.run(); });
        liveNumber(enemyHpField, v -> { var b = com.layla.AppContext.balance(); b.enemyBaseHp   = v; onStatsChanged.run(); });
        liveNumber(enemySpeedField, v -> { var b = com.layla.AppContext.balance(); b.enemySpeedAvg = v; onStatsChanged.run(); });
        liveNumber(enemyScoreKField, v -> { var b = com.layla.AppContext.balance(); b.enemyScoreK   = v; onStatsChanged.run(); });
    }

    private void liveNumber(TextField tf, Consumer<Double> onValidNumber) {
        tf.textProperty().addListener((obs, oldV, newV) -> {
            Double val = tryParse(newV);
            if (val != null) {
                onValidNumber.accept(val);
            }
        });
    }

    private static Double tryParse(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- Helpers ----------
    private static void put(TextField f, double v) { f.setText(Double.toString(v)); }
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

            if (m.containsKey("moveSpeed"))    stats.setStat(StatType.MOVE_SPEED,    m.get("moveSpeed"));
            if (m.containsKey("fireRate"))     stats.setStat(StatType.FIRE_RATE,     m.get("fireRate"));
            if (m.containsKey("projSpeed"))    stats.setStat(StatType.PROJECTILE_SPEED, m.get("projSpeed"));
            if (m.containsKey("projRange"))    stats.setStat(StatType.PROJECTILE_RANGE, m.get("projRange"));
            if (m.containsKey("projDamage"))   stats.setStat(StatType.PROJECTILE_DAMAGE, m.get("projDamage"));

            // Compatibilidad antigua
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
        } catch (Exception ignored) {}
    }

    private void saveUserJson() {
        try {
            var bal = com.layla.AppContext.balance();

            Path p = cfgPath();
            Files.createDirectories(p.getParent());
            String json = Json.toJson(Map.of(
                "startHp",     bal.startHp,
                "maxHp",       bal.maxHp,
                "moveSpeed",   stats.getStat(StatType.MOVE_SPEED),
                "fireRate",    stats.getStat(StatType.FIRE_RATE),
                "projSpeed",   stats.getStat(StatType.PROJECTILE_SPEED),
                "projRange",   stats.getStat(StatType.PROJECTILE_RANGE),
                "projDamage",  stats.getStat(StatType.PROJECTILE_DAMAGE),
                "enemyBaseHp", bal.enemyBaseHp,
                "enemySpeed",  bal.enemySpeedAvg,
                "enemyScoreK", bal.enemyScoreK
            ));
            Files.writeString(p, json);
        } catch (Exception ignored) {}
    }

    /** JSON minimalista para no añadir librerías */
    static final class Json {
        static Map<String, Double> minimalParse(String json) {
            try {
                java.util.HashMap<String, Double> out = new java.util.HashMap<>();
                String s = json.trim();
                if (!s.startsWith("{") || !s.endsWith("}")) return null;
                s = s.substring(1, s.length()-1).trim();
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
