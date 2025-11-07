package com.layla.ui;

import com.layla.model.StatType;
import com.layla.services.StatsService;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class StatsPanelController {
    @FXML private TextField startHpField, maxHpField, moveSpeedField;
    @FXML private TextField fireRateField, projSpeedField, projRangeField, projDamageField;
    @FXML private TextField enemyHpField, enemySpeedField, enemyScoreKField;
    @FXML private CheckBox persistCheck;

    private Runnable onClose = () -> {};
    private StatsService stats;

    // Parámetros de enemigo / jugador que también usa GameController
    private double enemyScoreK  = 5.0;   // K por defecto (EnemyBonus = HP^0.2 * K)
    private double enemySpeedAvg = 120;  // velocidad media enemigo
    private double enemyBaseHp   = 6.0;  // vida base enemigo
    private double startHp       = 6.0;  // vida inicial jugador
    private double maxHp         = 6.0;  // vida máxima jugador

    public void setOnClose(Runnable r) { this.onClose = (r != null) ? r : () -> {}; }
    public void setStatsService(StatsService s) { this.stats = s; }

    // Getters para GameController (leer tras Apply)
    public double getEnemyScoreK()   { return enemyScoreK; }
    public double getEnemySpeedAvg() { return enemySpeedAvg; }
    public double getEnemyBaseHp()   { return enemyBaseHp; }
    public double getStartHp()       { return startHp; }
    public double getMaxHp()         { return maxHp; }

    @FXML
    private void initialize() {
        // Si se abre antes de setStatsService, onShow() se encargará.
    }

    /** Llamar tras setStatsService() desde GameController para precargar campos. */
    public void onShow() {
        // 1) Cargar valores persistidos (si existen) ANTES de pintar
        loadUserJsonIfExists();

        // 2) Rellenar campos desde el estado actual
        put(startHpField, startHp);
        put(maxHpField,   maxHp);

        if (stats != null) {
            put(moveSpeedField, stats.getStat(StatType.MOVE_SPEED));
            put(fireRateField,  stats.getStat(StatType.FIRE_RATE));
            put(projSpeedField, stats.getStat(StatType.PROJECTILE_SPEED));
            put(projRangeField, stats.getStat(StatType.PROJECTILE_RANGE));
            put(projDamageField,stats.getStat(StatType.PROJECTILE_DAMAGE));
        } else {
            // Valores de reserva si aún no inyectaron stats
            put(moveSpeedField, 160.0);
            put(fireRateField,  3.0);
            put(projSpeedField, 400.0);
            put(projRangeField, 1.0);
            put(projDamageField,1.0);
        }

        // Enemigos (usados por GameController al spawnear)
        put(enemyHpField,     enemyBaseHp);
        put(enemySpeedField,  enemySpeedAvg);
        put(enemyScoreKField, enemyScoreK);
    }

    @FXML
    private void onApply() {
        // Player
        startHp = get(startHpField, startHp);
        maxHp   = get(maxHpField,   maxHp);

        if (stats != null) {
            stats.setStat(StatType.MOVE_SPEED,        get(moveSpeedField, stats.getStat(StatType.MOVE_SPEED)));
            stats.setStat(StatType.FIRE_RATE,         get(fireRateField,  stats.getStat(StatType.FIRE_RATE)));
            stats.setStat(StatType.PROJECTILE_SPEED,  get(projSpeedField, stats.getStat(StatType.PROJECTILE_SPEED)));
            stats.setStat(StatType.PROJECTILE_RANGE,  get(projRangeField, stats.getStat(StatType.PROJECTILE_RANGE)));
            stats.setStat(StatType.PROJECTILE_DAMAGE, get(projDamageField,stats.getStat(StatType.PROJECTILE_DAMAGE)));
        }

        // Enemigos
        enemyBaseHp    = get(enemyHpField,     enemyBaseHp);
        enemySpeedAvg  = get(enemySpeedField,  enemySpeedAvg);
        enemyScoreK    = get(enemyScoreKField, enemyScoreK);

        if (persistCheck.isSelected()) saveUserJson();

        onClose.run();
    }

    @FXML
    private void onCancel() { onClose.run(); }

    @FXML
    private void onClose() { onClose.run(); }

    // ---------- Helpers ----------
    private static void put(TextField f, double v) {
        if (f != null) f.setText(Double.toString(v));
    }
    private static double get(TextField f, double def) {
        try {
            String t = (f != null && f.getText() != null) ? f.getText().trim() : "";
            return Double.parseDouble(t);
        } catch (Exception e) {
            return def;
        }
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

            // Player
            if (m.containsKey("startHp"))  startHp = m.get("startHp");
            if (m.containsKey("maxHp"))    maxHp   = m.get("maxHp");

            if (stats != null) {
                if (m.containsKey("moveSpeed"))   stats.setStat(StatType.MOVE_SPEED,         m.get("moveSpeed"));
                if (m.containsKey("fireRate"))    stats.setStat(StatType.FIRE_RATE,          m.get("fireRate"));
                if (m.containsKey("projSpeed"))   stats.setStat(StatType.PROJECTILE_SPEED,   m.get("projSpeed"));
                if (m.containsKey("projRange"))   stats.setStat(StatType.PROJECTILE_RANGE,   m.get("projRange"));
                if (m.containsKey("projDamage"))  stats.setStat(StatType.PROJECTILE_DAMAGE,  m.get("projDamage"));

                // Compatibilidad con claves antiguas
                if (m.containsKey("fireCooldown")) {
                    double cooldown = m.get("fireCooldown");
                    double rate = cooldown > 0.0 ? 1.0 / cooldown : 0.0;
                    stats.setStat(StatType.FIRE_RATE, rate);
                }
                if (m.containsKey("rangePixels")) {
                    double pixels = m.get("rangePixels");
                    double speed = stats.getStat(StatType.PROJECTILE_SPEED);
                    if (speed > 0.0) {
                        stats.setStat(StatType.PROJECTILE_RANGE, pixels / speed);
                    }
                }
                if (m.containsKey("damage")) {
                    stats.setStat(StatType.PROJECTILE_DAMAGE, m.get("damage"));
                }
            }

            // Enemigos
            if (m.containsKey("enemyBaseHp"))  enemyBaseHp   = m.get("enemyBaseHp");
            if (m.containsKey("enemySpeed"))   enemySpeedAvg = m.get("enemySpeed");
            if (m.containsKey("enemyScoreK"))  enemyScoreK   = m.get("enemyScoreK");

        } catch (Exception ignored) {}
    }

    private void saveUserJson() {
        try {
            Path p = cfgPath();
            Files.createDirectories(p.getParent());
            String json = Json.toJson(Map.of(
                "startHp",     startHp,
                "maxHp",       maxHp,
                "moveSpeed",   (stats != null ? stats.getStat(StatType.MOVE_SPEED)        : 160.0),
                "fireRate",    (stats != null ? stats.getStat(StatType.FIRE_RATE)         : 3.0),
                "projSpeed",   (stats != null ? stats.getStat(StatType.PROJECTILE_SPEED)   : 400.0),
                "projRange",   (stats != null ? stats.getStat(StatType.PROJECTILE_RANGE)   : 1.0),
                "projDamage",  (stats != null ? stats.getStat(StatType.PROJECTILE_DAMAGE)  : 1.0),
                "enemyBaseHp", enemyBaseHp,
                "enemySpeed",  enemySpeedAvg,
                "enemyScoreK", enemyScoreK
            ));
            Files.writeString(p, json);
        } catch (Exception ignored) {}
    }

    /** JSON minimalista para no añadir librerías */
    static final class Json {
        static Map<String, Double> minimalParse(String json) {
            try {
                java.util.HashMap<String, Double> out = new java.util.HashMap<>();
                String s = (json == null ? "" : json.trim());
                if (!s.startsWith("{") || !s.endsWith("}")) return null;
                s = s.substring(1, s.length() - 1).trim();
                if (s.isEmpty()) return out;

                // Divide por comas de primer nivel (no soporta anidación, intencionado)
                for (String part : s.split(",")) {
                    String kvs = part.trim();
                    int idx = kvs.indexOf(':');
                    if (idx <= 0) continue;
                    String k = kvs.substring(0, idx).trim();
                    String v = kvs.substring(idx + 1).trim();
                    k = k.replaceAll("^\"|\"$", ""); // quitar comillas
                    out.put(k, Double.parseDouble(v));
                }
                return out;
            } catch (Exception e) {
                return null;
            }
        }

        static String toJson(Map<String, Double> m) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(e.getKey()).append('"')
                  .append(':')
                  .append(e.getValue());
            }
            return sb.append('}').toString();
        }
    }
}
