package com.layla.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.layla.AppContext;
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
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;

/**
 * Panel de depuración / balanceo:
 * - Edita stats base del jugador (HP, velocidad, fire rate, etc.).
 * - Edita balance por tipo de enemigo (EnemyProfile) y permite guardarlo en JSON.
 * - Ajusta monedas iniciales y, si estás en pausa, puede avisar al GameController.
 */
public class StatsPanelController {

    // =========================
    // FXML: Player stats
    // =========================

    /** HP unificado (este manda). */
    @FXML private TextField maxHpField;

    @FXML private TextField moveSpeedField;
    @FXML private TextField fireRateField;
    @FXML private TextField projSpeedField;
    @FXML private TextField projRangeField;
    @FXML private TextField projDamageField;

    // =========================
    // FXML: Coins
    // =========================

    @FXML private Spinner<Integer> coinsSpinner;

    /** Monedas iniciales si vienes desde GameController (pausa). Null => usar balance.startCoins. */
    private Integer initialCoins = null;

    /** Callback para informar al GameController cuando cambien coins del run actual. */
    private Consumer<Integer> onCoinsChanged = c -> {};

    // =========================
    // FXML: Persistencia / Enemy profiles
    // =========================

    @FXML private CheckBox persistCheck;

    @FXML private ComboBox<EnemyType> enemyTypeCombo;
    @FXML private CheckBox stationaryCheck;
    @FXML private Button saveBtn;

    @FXML private TextField profileHpField;
    @FXML private TextField profileSpeedField;
    @FXML private TextField profileContactField;
    @FXML private TextField profileFireField;
    @FXML private TextField profileJitterField;
    @FXML private TextField profileProjSpeedField;
    @FXML private TextField profileProjRangeField;
    @FXML private TextField profileProjDmgField;
    @FXML private TextField profileScoreField;

    // =========================
    // Callbacks / Services
    // =========================

    private Runnable onClose = () -> {};
    private Runnable onStatsChanged = () -> {};

    private StatsService stats = AppContext.stats();

    // =========================
    // Estado interno de bindings
    // =========================

    /** Guard para evitar side-effects cuando rellenamos la UI por código. */
    private boolean updatingUI = false;

    /** Guardamos listeners para poder desengancharlos al cambiar de EnemyType. */
    private final Map<TextField, ChangeListener<String>> profileBindings = new HashMap<>();
    private ChangeListener<Boolean> stationaryBinding;

    // =========================
    // Paths
    // =========================

    private static final Path USER_STATS_PATH = Path.of(
            System.getProperty("user.home"), ".layla", "stats.json"
    );

    private static final Path ENEMY_BALANCE_PATH = Path.of(
            System.getProperty("user.home"), ".layla", "enemy_balance.json"
    );

    // =========================
    // API pública (desde fuera)
    // =========================

    /** Asigna callback de cierre del overlay/panel. */
    public void setOnClose(Runnable r) {
        this.onClose = (r != null) ? r : () -> {};
    }

    /** Se llama cuando cambian stats/balance para refrescar HUD/juego. */
    public void setOnStatsChanged(Runnable r) {
        this.onStatsChanged = (r != null) ? r : () -> {};
    }

    /** Permite inyectar StatsService (tests o variantes). */
    public void setStatsService(StatsService s) {
        if (s != null) this.stats = s;
    }

    /**
     * Monedas iniciales (cuando vienes desde GameController en pausa).
     * También actualiza el spinner si ya está listo.
     */
    public void setInitialCoins(int coins) {
        this.initialCoins = Math.max(0, coins);
        if (coinsSpinner != null && coinsSpinner.getValueFactory() != null) {
            coinsSpinner.getValueFactory().setValue(this.initialCoins);
        }
    }

    /**
     * Callback para avisar al GameController si estás en partida:
     * (ej. al pulsar Aplicar, actualizar coins del run actual).
     */
    public void setOnCoinsChanged(java.util.function.IntConsumer cb) {
        this.onCoinsChanged = (cb != null) ? cb::accept : c -> {};
    }

    // =========================
    // JavaFX init
    // =========================

    /**
     * Inicializa el panel:
     * - Configura spinner, focus helpers, listeners "live".
     * - Configura sección de perfiles y carga JSON de enemigos.
     */
    @FXML
    private void initialize() {
        configureCoinsSpinner();
        installFocusHelpers();
        installLiveBindings();
        setupEnemyProfilesPanel();
    }

    // =========================
    // Entrada (cuando se muestra el panel)
    // =========================

    /**
     * Rellena la UI desde balance/stats actuales.
     * También aplica (si existe) el JSON de usuario antes de pintar.
     */
    public void onShow() {
        updatingUI = true;
        try {
            // 1) Cargar JSON user si existe (puede tocar HP/stats/coins)
            loadUserJsonIfExists();

            // 2) Unificar HP: startHp y maxHp deben ser el mismo valor internamente
            unifyHpInBalance();

            // 3) Pintar campos del jugador
            paintPlayerFields();

            // 4) Pintar coins
            paintCoins();

            // 5) Refrescar perfil seleccionado (si procede)
            refreshSelectedProfile();

        } finally {
            updatingUI = false;
        }
    }

    // =========================
    // UI config helpers
    // =========================

    /**
     * Configura spinner de coins:
     * - rango razonable
     * - editable (y luego "commiteamos" el texto antes de leer)
     */
    private void configureCoinsSpinner() {
        if (coinsSpinner == null) return;

        SpinnerValueFactory<Integer> vf =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 9999, 0);
        coinsSpinner.setValueFactory(vf);
        coinsSpinner.setEditable(true);
    }

    /**
     * Selecciona todo el texto al entrar en foco (mejor UX para editar números).
     */
    private void installFocusHelpers() {
        Consumer<TextField> selectAllOnFocus = tf ->
                tf.focusedProperty().addListener((o, oldV, newV) -> {
                    if (Boolean.TRUE.equals(newV)) tf.selectAll();
                });

        for (TextField tf : new TextField[] {
                maxHpField,
                moveSpeedField, fireRateField, projSpeedField, projRangeField, projDamageField,
                profileHpField, profileSpeedField, profileContactField, profileFireField,
                profileJitterField, profileProjSpeedField, profileProjRangeField, profileProjDmgField,
                profileScoreField
        }) {
            if (tf != null) selectAllOnFocus.accept(tf);
        }
    }

    // =========================
    // Player stats logic
    // =========================

    /**
     * Unifica HP en el balance:
     * - maxHp manda
     * - startHp = maxHp
     * - también sincroniza StatsService VIDA_MAXIMA
     */
    private void unifyHpInBalance() {
        var bal = AppContext.balance();
        double hp = bal.maxHp;
        bal.maxHp = hp;
        bal.startHp = hp;

        stats.setBaseStat(PlayerStatId.VIDA_MAXIMA, hp);
    }

    /**
     * Pinta campos del jugador (StatsService + Balance).
     * No debe disparar listeners gracias a updatingUI.
     */
    private void paintPlayerFields() {
        var bal = AppContext.balance();

        // HP unificado
        setNumber(maxHpField, bal.maxHp);

        // Stats base (StatsService)
        setNumber(moveSpeedField,  stats.getBaseStat(PlayerStatId.VELOCIDAD_DE_MOVIMIENTO));
        setNumber(fireRateField,   stats.getBaseStat(PlayerStatId.CADENCIA));
        setNumber(projSpeedField,  stats.getBaseStat(PlayerStatId.VELOCIDAD_DEL_PROYECTIL));
        setNumber(projRangeField,  stats.getBaseStat(PlayerStatId.RANGO_DEL_PROYECTIL));
        setNumber(projDamageField, stats.getBaseStat(PlayerStatId.DAÑO_DEL_PROYECTIL));
    }

    /**
     * Pinta coins:
     * - si hay initialCoins (pausa) => se ve eso
     * - si no => balance.startCoins
     */
    private void paintCoins() {
        if (coinsSpinner == null || coinsSpinner.getValueFactory() == null) return;

        int coinsToShow = (initialCoins != null)
                ? initialCoins
                : Math.max(0, AppContext.balance().startCoins);

        coinsSpinner.getValueFactory().setValue(coinsToShow);
    }

    // =========================
    // Enemy profiles setup
    // =========================

    /**
     * Prepara el panel de perfiles:
     * - llena ComboBox
     * - bind del perfil al seleccionar tipo
     * - carga JSON de enemigos
     * - botón save guarda a JSON
     */
    private void setupEnemyProfilesPanel() {
        if (enemyTypeCombo == null) return;

        enemyTypeCombo.getItems().setAll(EnemyType.values());

        enemyTypeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldT, newT) -> {
            if (newT != null) {
                bindProfile(AppContext.balance().profile(newT));
            }
        });

        // Selección inicial sensata
        if (!enemyTypeCombo.getItems().isEmpty()) {
            EnemyType preferred = enemyTypeCombo.getItems().contains(EnemyType.SHOOTER)
                    ? EnemyType.SHOOTER
                    : enemyTypeCombo.getItems().get(0);
            enemyTypeCombo.getSelectionModel().select(preferred);
        }

        // Botón guardar perfiles
        if (saveBtn != null) {
            saveBtn.setOnAction(e -> saveProfilesAsync());
        }

        // Cargar JSON de enemigos (asíncrono)
        loadProfilesAsync();
    }

    /**
     * Vincula los campos del panel EnemyProfile al objeto profile actual.
     * Importante: removemos listeners anteriores para no acumularlos.
     */
    private void bindProfile(EnemyProfile profile) {
        if (profile == null) return;

        updatingUI = true;
        try {
            bindNumberField(profileHpField,        () -> profile.baseHp,     v -> profile.baseHp = v);
            bindNumberField(profileSpeedField,     () -> profile.speed,      v -> profile.speed = v);
            bindNumberField(profileContactField,   () -> profile.contactDmg, v -> profile.contactDmg = v);
            bindNumberField(profileFireField,      () -> profile.fireRate,   v -> profile.fireRate = v);
            bindNumberField(profileJitterField,    () -> profile.jitter,     v -> profile.jitter = v);
            bindNumberField(profileProjSpeedField, () -> profile.projSpeed,  v -> profile.projSpeed = v);
            bindNumberField(profileProjRangeField, () -> profile.projRange,  v -> profile.projRange = v);
            bindNumberField(profileProjDmgField,   () -> profile.projDamage, v -> profile.projDamage = v);

            // Score: int internamente
            bindNumberField(
                    profileScoreField,
                    () -> (double) profile.score,
                    v  -> profile.score = (int) Math.round(v)
            );

            bindStationary(profile);

        } finally {
            updatingUI = false;
        }
    }

    /**
     * Bindea un TextField numérico a un getter/setter.
     * - actualiza el texto con el getter
     * - registra listener y lo guarda para removerlo en el siguiente bind
     */
    private void bindNumberField(TextField tf, DoubleSupplier getter, DoubleConsumer setter) {
        if (tf == null || getter == null || setter == null) return;

        // Quitar listener previo si existe
        ChangeListener<String> prev = profileBindings.remove(tf);
        if (prev != null) tf.textProperty().removeListener(prev);

        // Pintar valor actual
        tf.setText(Double.toString(getter.getAsDouble()));

        // Nuevo listener
        ChangeListener<String> listener = (obs, oldV, newV) -> {
            if (updatingUI) return;

            Double val = tryParseDouble(newV);
            if (val != null) setter.accept(val);
        };

        tf.textProperty().addListener(listener);
        profileBindings.put(tf, listener);
    }

    /**
     * Bindea el CheckBox "stationary" al EnemyProfile actual.
     * Remueve binding previo para evitar acumulación.
     */
    private void bindStationary(EnemyProfile profile) {
        if (stationaryCheck == null) return;

        if (stationaryBinding != null) {
            stationaryCheck.selectedProperty().removeListener(stationaryBinding);
        }

        stationaryCheck.setSelected(profile.stationary);

        stationaryBinding = (obs, oldV, newV) -> {
            if (updatingUI) return;
            profile.stationary = Boolean.TRUE.equals(newV);
        };

        stationaryCheck.selectedProperty().addListener(stationaryBinding);
    }

    /**
     * Re-bindea el perfil del EnemyType seleccionado.
     */
    private void refreshSelectedProfile() {
        if (enemyTypeCombo == null) return;

        EnemyType selected = enemyTypeCombo.getSelectionModel().getSelectedItem();
        if (selected != null) {
            bindProfile(AppContext.balance().profile(selected));
        }
    }

    /**
     * Carga perfiles desde JSON (asíncrono) y refresca UI al finalizar.
     */
    private void loadProfilesAsync() {
        CompletableFuture
                .runAsync(() -> AppContext.balance().loadFromJson(ENEMY_BALANCE_PATH))
                .thenRun(() -> Platform.runLater(this::refreshSelectedProfile));
    }

    /**
     * Guarda perfiles a JSON (asíncrono).
     */
    private void saveProfilesAsync() {
        CompletableFuture.runAsync(() -> AppContext.balance().saveToJson(ENEMY_BALANCE_PATH));
    }

    // =========================
    // Live bindings (player)
    // =========================

    /**
     * Instala listeners "en vivo" para que, al editar un campo, el juego se actualice sin esperar a Apply.
     */
    private void installLiveBindings() {
        // HP único (maxHpField manda)
        liveNumber(maxHpField, v -> {
            var b = AppContext.balance();
            b.maxHp = v;
            b.startHp = v;
            stats.setBaseStat(PlayerStatId.VIDA_MAXIMA, v);
            onStatsChanged.run();
        });

        // Stats base
        liveNumber(moveSpeedField,  v -> { stats.setBaseStat(PlayerStatId.VELOCIDAD_DE_MOVIMIENTO, v); onStatsChanged.run(); });
        liveNumber(fireRateField,   v -> { stats.setBaseStat(PlayerStatId.CADENCIA, v); onStatsChanged.run(); });
        liveNumber(projSpeedField,  v -> { stats.setBaseStat(PlayerStatId.VELOCIDAD_DEL_PROYECTIL, v); onStatsChanged.run(); });
        liveNumber(projRangeField,  v -> { stats.setBaseStat(PlayerStatId.RANGO_DEL_PROYECTIL, v); onStatsChanged.run(); });
        liveNumber(projDamageField, v -> { stats.setBaseStat(PlayerStatId.DAÑO_DEL_PROYECTIL, v); onStatsChanged.run(); });
    }

    /**
     * Listener genérico para TextField numérico.
     * Ignora entradas inválidas (incluye estados intermedios al escribir).
     */
    private void liveNumber(TextField tf, Consumer<Double> onValidNumber) {
        if (tf == null || onValidNumber == null) return;

        tf.textProperty().addListener((obs, oldV, newV) -> {
            if (updatingUI) return;

            Double val = tryParseDouble(newV);
            if (val != null) onValidNumber.accept(val);
        });
    }

    // =========================
    // Actions (FXML)
    // =========================

    /**
     * Aplica valores a balance/stats, persiste si procede y cierra.
     */
    @FXML
    private void onApply() {
        var bal = AppContext.balance();

        // HP único
        double hp = getNumber(maxHpField, bal.maxHp);
        bal.maxHp = hp;
        bal.startHp = hp;
        stats.setBaseStat(PlayerStatId.VIDA_MAXIMA, hp);

        // Stats
        stats.setBaseStat(PlayerStatId.VELOCIDAD_DE_MOVIMIENTO,        getNumber(moveSpeedField,  stats.getBaseStat(PlayerStatId.VELOCIDAD_DE_MOVIMIENTO)));
        stats.setBaseStat(PlayerStatId.CADENCIA,         getNumber(fireRateField,   stats.getBaseStat(PlayerStatId.CADENCIA)));
        stats.setBaseStat(PlayerStatId.VELOCIDAD_DEL_PROYECTIL,  getNumber(projSpeedField,  stats.getBaseStat(PlayerStatId.VELOCIDAD_DEL_PROYECTIL)));
        stats.setBaseStat(PlayerStatId.RANGO_DEL_PROYECTIL,  getNumber(projRangeField,  stats.getBaseStat(PlayerStatId.RANGO_DEL_PROYECTIL)));
        stats.setBaseStat(PlayerStatId.DAÑO_DEL_PROYECTIL, getNumber(projDamageField, stats.getBaseStat(PlayerStatId.DAÑO_DEL_PROYECTIL)));

        // Coins (commitear editor si el spinner es editable)
        int coins = getSpinnerIntValue(coinsSpinner, Math.max(0, bal.startCoins));
        coins = Math.max(0, coins);

        bal.startCoins = coins;        // futuras runs
        onCoinsChanged.accept(coins);  // run actual (si aplica)

        // Persistencia user
        if (persistCheck != null && persistCheck.isSelected()) {
            saveUserJson();
        }

        onStatsChanged.run();
        onClose.run();
    }

    /**
     * Restaura defaults (balance + stats), repinta, persiste si procede.
     */
    @FXML
    private void onReset() {
        stats.getBaseStats().resetDefaults();
        AppContext.balance().resetDefaults();

        // Unificar startHp/maxHp en defaults
        unifyHpInBalance();

        // Repintar
        onShow();

        if (persistCheck != null && persistCheck.isSelected()) {
            saveUserJson();
        }

        onStatsChanged.run();
    }

    /**
     * Cierra sin aplicar.
     */
    @FXML
    private void onCancel() {
        onClose.run();
    }

    // =========================
    // JSON user stats
    // =========================

    /**
     * Carga stats.json si existe (sin librerías), y aplica valores al balance/stats.
     * Nota: leemos "maxHp" y (por compatibilidad) también "startHp" si existiera.
     */
    private void loadUserJsonIfExists() {
        try {
            if (!Files.exists(USER_STATS_PATH)) return;

            String json = Files.readString(USER_STATS_PATH);
            Map<String, Double> m = Json.parseNumbers(json);
            if (m == null || m.isEmpty()) return;

            var bal = AppContext.balance();

            // HP unificado: prioriza maxHp, si no existe usa startHp (compatibilidad con configs viejas)
            Double savedHp = m.containsKey("maxHp") ? m.get("maxHp") : m.get("startHp");
            if (savedHp != null) {
                bal.maxHp = savedHp;
                bal.startHp = savedHp;
                stats.setBaseStat(PlayerStatId.VIDA_MAXIMA, savedHp);
            }

            if (m.containsKey("moveSpeed"))  stats.setBaseStat(PlayerStatId.VELOCIDAD_DE_MOVIMIENTO,        m.get("moveSpeed"));
            if (m.containsKey("fireRate"))   stats.setBaseStat(PlayerStatId.CADENCIA,         m.get("fireRate"));
            if (m.containsKey("projSpeed"))  stats.setBaseStat(PlayerStatId.VELOCIDAD_DEL_PROYECTIL,  m.get("projSpeed"));
            if (m.containsKey("projRange"))  stats.setBaseStat(PlayerStatId.RANGO_DEL_PROYECTIL,  m.get("projRange"));
            if (m.containsKey("projDamage")) stats.setBaseStat(PlayerStatId.DAÑO_DEL_PROYECTIL, m.get("projDamage"));

            // Back-compat keys (por si guardaste versiones antiguas)
            if (m.containsKey("fireCooldown")) {
                double cooldown = m.get("fireCooldown");
                double rate = cooldown > 0.0 ? 1.0 / cooldown : 0.0;
                stats.setBaseStat(PlayerStatId.CADENCIA, rate);
            }
            if (m.containsKey("rangePixels")) {
                double pixels = m.get("rangePixels");
                double speed = stats.getBaseStat(PlayerStatId.VELOCIDAD_DEL_PROYECTIL);
                if (speed > 0.0) stats.setBaseStat(PlayerStatId.RANGO_DEL_PROYECTIL, pixels / speed);
            }
            if (m.containsKey("damage")) {
                stats.setBaseStat(PlayerStatId.DAÑO_DEL_PROYECTIL, m.get("damage"));
            }

            // Coins persistentes (startCoins)
            if (m.containsKey("startCoins")) {
                int c = (int) Math.max(0, Math.round(m.get("startCoins")));
                bal.startCoins = c;
            }

        } catch (Exception ignored) {
            // No queremos crashear el panel por un JSON roto
        }
    }

    /**
     * Guarda stats.json (sin librerías), solo valores del jugador (no enemy balance).
     * Guardamos maxHp y startCoins, etc.
     */
    private void saveUserJson() {
        try {
            var bal = AppContext.balance();

            Files.createDirectories(USER_STATS_PATH.getParent());

            LinkedHashMap<String, Double> m = new LinkedHashMap<>();
            // HP unificado (guardamos startHp también por compatibilidad con configs viejas)
            m.put("maxHp",      bal.maxHp);
            m.put("startHp",    bal.maxHp);

            m.put("moveSpeed",  stats.getBaseStat(PlayerStatId.VELOCIDAD_DE_MOVIMIENTO));
            m.put("fireRate",   stats.getBaseStat(PlayerStatId.CADENCIA));
            m.put("projSpeed",  stats.getBaseStat(PlayerStatId.VELOCIDAD_DEL_PROYECTIL));
            m.put("projRange",  stats.getBaseStat(PlayerStatId.RANGO_DEL_PROYECTIL));
            m.put("projDamage", stats.getBaseStat(PlayerStatId.DAÑO_DEL_PROYECTIL));

            m.put("startCoins", (double) Math.max(0, bal.startCoins));

            Files.writeString(USER_STATS_PATH, Json.toJson(m));

        } catch (Exception ignored) {
            // no crashear por IO
        }
    }

    // =========================
    // Parsing / small helpers
    // =========================

    /** Setter seguro para números. */
    private void setNumber(TextField f, double v) {
        if (f == null) return;
        f.setText(Double.toString(v));
    }

    /** Getter seguro para números. */
    private double getNumber(TextField f, double def) {
        if (f == null) return def;
        Double v = tryParseDouble(f.getText());
        return (v != null) ? v : def;
    }

    /** Parse tolerante (null/blank/espacios). */
    private static Double tryParseDouble(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return Double.parseDouble(t);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Corrige el bug típico del Spinner editable:
     * si el usuario escribe en el editor, `getValue()` puede no reflejarlo.
     * Aquí commiteamos lo escrito antes de leer.
     */
    private static int getSpinnerIntValue(Spinner<Integer> sp, int fallback) {
        if (sp == null || sp.getValueFactory() == null) return fallback;

        if (sp.isEditable() && sp.getEditor() != null) {
            String text = sp.getEditor().getText();
            try {
                int v = Integer.parseInt(text.trim());
                sp.getValueFactory().setValue(v); // commit
            } catch (Exception ignore) {
                // no commit si es inválido
            }
        }

        Integer v = sp.getValue();
        return (v != null) ? v : fallback;
    }

    /**
     * JSON mínimo sin librerías:
     * solo soporta claves string y valores numéricos.
     */
    static final class Json {
        private static final Pattern NUM_ENTRY =
                Pattern.compile("\"([^\"]+)\"\\s*:\\s*([-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)");

        /** Extrae {"a":1,"b":2.5} -> Map(a=1, b=2.5). Ignora lo que no sea número. */
        static Map<String, Double> parseNumbers(String json) {
            if (json == null) return null;
            Matcher m = NUM_ENTRY.matcher(json);
            Map<String, Double> out = new HashMap<>();
            while (m.find()) {
                String key = m.group(1);
                String raw = m.group(2);
                try {
                    out.put(key, Double.parseDouble(raw));
                } catch (Exception ignore) {}
            }
            return out;
        }

        /** Serializa Map a JSON simple (clave string, valor número). */
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
