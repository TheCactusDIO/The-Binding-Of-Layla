package com.layla.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;

import com.layla.AppContext;
import com.layla.core.AssetsManager;
import com.layla.model.EnemyProfile;
import com.layla.model.EnemyType;
import com.layla.model.PlayerStatId;
import com.layla.services.ConfigService;
import com.layla.services.StatsService;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class SettingsController {
    // --- Audio Settings ---
    @FXML private Slider musicSlider;
    @FXML private Slider sfxSlider;
    @FXML private CheckBox fullscreenCheck;

    // --- Dev / Custom Run Fields ---
    @FXML private TitledPane devToolsPane;
    @FXML private Spinner<Double> spawnRateSpinner;
    @FXML private Spinner<Integer> wavesSpinner;
    @FXML private Spinner<Double> globalHpMultSpinner;
    @FXML private Spinner<Double> globalDmgMultSpinner;

    @FXML private TextField maxHpField;
    @FXML private TextField moveSpeedField;
    @FXML private TextField fireRateField;
    @FXML private TextField projSpeedField;
    @FXML private TextField projDamageField;
    @FXML private TextField projRangeField; // NUEVO: Campo para el rango

    @FXML private Spinner<Integer> coinsSpinner;
    @FXML private CheckBox persistCheck;
    @FXML private ComboBox<EnemyType> enemyTypeCombo;
    @FXML private TextField profileHpField, profileSpeedField, profileContactField, profileProjDmgField;
    @FXML private CheckBox stationaryCheck;
    @FXML private Button saveBtn;

    // --- State ---
    private Integer initialCoins = null;
    private IntConsumer onCoinsChanged;
    private Runnable onClose = () -> {};
    private Runnable onStatsChanged = () -> {};
    private StatsService stats = AppContext.stats();
    private final ConfigService config = AppContext.config();

    private StackPane overlayHost;
    private final Map<TextField, ChangeListener<String>> profileBindings = new HashMap<>();
    private ChangeListener<Boolean> stationaryBinding;
    private final Path enemyBalancePath = Path.of(System.getProperty("user.home"), ".layla", "enemy_balance.json");

    public void setOnClose(Runnable r)          { this.onClose = (r != null) ? r : () -> {}; }
    public void setOnStatsChanged(Runnable r)   { this.onStatsChanged = (r != null) ? r : () -> {}; }
    public void setStatsService(StatsService s) { if (s != null) this.stats = s; }

    public void setOverlayHost(StackPane host) {
        this.overlayHost = host;
    }

    public void setInitialCoins(int coins) {
        this.initialCoins = Math.max(0, coins);
        if (coinsSpinner != null && coinsSpinner.getValueFactory() != null) {
            coinsSpinner.getValueFactory().setValue(this.initialCoins);
        }
    }

    public void setOnCoinsChanged(IntConsumer onCoinsChanged) {
        this.onCoinsChanged = onCoinsChanged;
    }

    public void setExpandDeveloperTools(boolean expand) {
        if (devToolsPane != null) devToolsPane.setExpanded(expand);
    }

    @FXML
    private void initialize() {
        if (musicSlider != null) {
            musicSlider.setValue(config.getMusicVolume());
            musicSlider.valueProperty().addListener((obs, oldV, newV) -> AssetsManager.setMusicVolume(newV.doubleValue()));
        }
        if (sfxSlider != null) {
            sfxSlider.setValue(config.getSfxVolume());
            sfxSlider.valueProperty().addListener((obs, oldV, newV) -> AssetsManager.setSfxVolume(newV.doubleValue()));
        }
        if (fullscreenCheck != null) fullscreenCheck.setSelected(config.isFullscreen());

        setupSpinner(coinsSpinner, 0, 9999, 0, 1);
        setupSpinner(wavesSpinner, 1, 50, 5, 1);
        setupDoubleSpinner(spawnRateSpinner, 0.1, 10.0, 1.0, 0.1);
        setupDoubleSpinner(globalHpMultSpinner, 0.1, 10.0, 1.0, 0.1);
        setupDoubleSpinner(globalDmgMultSpinner, 0.1, 10.0, 1.0, 0.1);

        setupDebugBindings();
        setupEnemyProfilesPanel();
    }

    public void onShow() {
        var bal = AppContext.balance();
        var mods = AppContext.getRunModifiers();

        loadUserJsonIfExists();

        put(maxHpField, bal.maxHp);
        var baseStats = stats.getBaseStats();
        put(moveSpeedField, baseStats.getBase(PlayerStatId.MOVE_SPEED));
        put(fireRateField,  baseStats.getBase(PlayerStatId.FIRE_RATE));
        put(projSpeedField, baseStats.getBase(PlayerStatId.PROJECTILE_SPEED));
        put(projDamageField,baseStats.getBase(PlayerStatId.PROJECTILE_DAMAGE));
        put(projRangeField, baseStats.getBase(PlayerStatId.PROJECTILE_RANGE)); // NUEVO

        if (coinsSpinner != null) {
            int coinsToShow = (initialCoins != null) ? initialCoins : Math.max(0, bal.startCoins);
            coinsSpinner.getValueFactory().setValue(coinsToShow);
        }

        if (spawnRateSpinner != null) spawnRateSpinner.getValueFactory().setValue(mods.spawnRateMult);
        if (wavesSpinner != null) wavesSpinner.getValueFactory().setValue(mods.wavesPerFloor);
        if (globalHpMultSpinner != null) globalHpMultSpinner.getValueFactory().setValue(mods.enemyHpMult);
        if (globalDmgMultSpinner != null) globalDmgMultSpinner.getValueFactory().setValue(mods.enemyDmgMult);

        refreshSelectedProfile();
    }

    @FXML
    private void onApply() {
        config.setMusicVolume(musicSlider.getValue());
        config.setSfxVolume(sfxSlider.getValue());
        config.setFullscreen(fullscreenCheck.isSelected());
        config.save();

        Stage stage = SceneRouter.getStage();
        if (stage != null && stage.isFullScreen() != config.isFullscreen()) {
            stage.setFullScreen(config.isFullscreen());
        }

        var bal = AppContext.balance();
        double hp = get(maxHpField, bal.maxHp);
        bal.maxHp = hp;
        bal.startHp = hp;
        stats.setBaseStat(PlayerStatId.MAX_HEALTH, hp);

        stats.setBaseStat(PlayerStatId.MOVE_SPEED,        get(moveSpeedField, stats.getBaseStat(PlayerStatId.MOVE_SPEED)));
        stats.setBaseStat(PlayerStatId.FIRE_RATE,         get(fireRateField,  stats.getBaseStat(PlayerStatId.FIRE_RATE)));
        stats.setBaseStat(PlayerStatId.PROJECTILE_SPEED,  get(projSpeedField, stats.getBaseStat(PlayerStatId.PROJECTILE_SPEED)));
        stats.setBaseStat(PlayerStatId.PROJECTILE_DAMAGE, get(projDamageField,stats.getBaseStat(PlayerStatId.PROJECTILE_DAMAGE)));
        stats.setBaseStat(PlayerStatId.PROJECTILE_RANGE,  get(projRangeField, stats.getBaseStat(PlayerStatId.PROJECTILE_RANGE))); // NUEVO

        if (coinsSpinner != null && coinsSpinner.getValue() != null) {
            int coins = Math.max(0, coinsSpinner.getValue());
            bal.startCoins = coins;
            if (onCoinsChanged != null) onCoinsChanged.accept(coins);
        }

        var mods = AppContext.getRunModifiers();
        if (spawnRateSpinner != null) mods.spawnRateMult = spawnRateSpinner.getValue();
        if (wavesSpinner != null) mods.wavesPerFloor = wavesSpinner.getValue();
        if (globalHpMultSpinner != null) mods.enemyHpMult = globalHpMultSpinner.getValue();
        if (globalDmgMultSpinner != null) mods.enemyDmgMult = globalDmgMultSpinner.getValue();

        if (persistCheck != null && persistCheck.isSelected()) saveUserJson();

        onStatsChanged.run();
        onClose.run();
    }

    @FXML
    private void onReset() {
        stats.getBaseStats().resetDefaults();
        AppContext.balance().resetDefaults();
        AppContext.balance().startHp = AppContext.balance().maxHp;
        AppContext.getRunModifiers().reset();

        musicSlider.setValue(0.5);
        sfxSlider.setValue(0.8);
        fullscreenCheck.setSelected(false);

        onShow();
        onStatsChanged.run();
    }

    @FXML
    private void onCancel() {
        AssetsManager.setMusicVolume(config.getMusicVolume());
        AssetsManager.setSfxVolume(config.getSfxVolume());
        onClose.run();
    }

    // --- HELPERS ---

    private void setupSpinner(Spinner<Integer> s, int min, int max, int init, int step) {
        if (s == null) return;
        s.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, init, step));
        s.setEditable(true);
    }

    private void setupDoubleSpinner(Spinner<Double> s, double min, double max, double init, double step) {
        if (s == null) return;
        s.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, init, step));
        s.setEditable(true);
    }

    private void setupDebugBindings() {
        Consumer<TextField> selectAll = tf -> tf.focusedProperty().addListener((o, old, newVal) -> {
            if (Boolean.TRUE.equals(newVal)) tf.selectAll();
        });
        if(maxHpField != null) selectAll.accept(maxHpField);
        if(moveSpeedField != null) selectAll.accept(moveSpeedField);
        if(fireRateField != null) selectAll.accept(fireRateField);
        if(projSpeedField != null) selectAll.accept(projSpeedField);
        if(projDamageField != null) selectAll.accept(projDamageField);
        if(projRangeField != null) selectAll.accept(projRangeField); // NUEVO

        if(profileHpField != null) selectAll.accept(profileHpField);
        if(profileSpeedField != null) selectAll.accept(profileSpeedField);
        if(profileContactField != null) selectAll.accept(profileContactField);
        if(profileProjDmgField != null) selectAll.accept(profileProjDmgField);
    }

    private void setupEnemyProfilesPanel() {
        if (enemyTypeCombo == null) return;
        enemyTypeCombo.getItems().setAll(EnemyType.values());
        enemyTypeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldT, newT) -> {
            if (newT != null) bindProfile(AppContext.balance().profile(newT));
        });
        if (!enemyTypeCombo.getItems().isEmpty()) {
            enemyTypeCombo.getSelectionModel().select(EnemyType.SHOOTER);
            refreshSelectedProfile();
        }
        if (saveBtn != null) saveBtn.setOnAction(e -> saveProfilesAsync());
        loadProfilesAsync();
    }

    private void bindProfile(EnemyProfile profile) {
        if (profile == null) return;
        bindNumberField(profileHpField, () -> profile.baseHp, v -> profile.baseHp = v);
        bindNumberField(profileSpeedField, () -> profile.speed, v -> profile.speed = v);
        bindNumberField(profileContactField, () -> profile.contactDmg, v -> profile.contactDmg = v);
        bindNumberField(profileProjDmgField, () -> profile.projDamage, v -> profile.projDamage = v);

        if (stationaryCheck != null) {
            if (stationaryBinding != null) stationaryCheck.selectedProperty().removeListener(stationaryBinding);
            stationaryCheck.setSelected(profile.stationary);
            stationaryBinding = (obs, o, n) -> profile.stationary = Boolean.TRUE.equals(n);
            stationaryCheck.selectedProperty().addListener(stationaryBinding);
        }
    }

    private void bindNumberField(TextField tf, DoubleSupplier getter, DoubleConsumer setter) {
        if (tf == null) return;
        ChangeListener<String> prev = profileBindings.remove(tf);
        if (prev != null) tf.textProperty().removeListener(prev);
        tf.setText(Double.toString(getter.getAsDouble()));
        ChangeListener<String> listener = (obs, oldV, newV) -> {
            try { setter.accept(Double.parseDouble(newV.trim())); } catch(Exception e){}
        };
        tf.textProperty().addListener(listener);
        profileBindings.put(tf, listener);
    }

    private void refreshSelectedProfile() {
        if (enemyTypeCombo != null && enemyTypeCombo.getSelectionModel().getSelectedItem() != null) {
            bindProfile(AppContext.balance().profile(enemyTypeCombo.getSelectionModel().getSelectedItem()));
        }
    }

    private void loadProfilesAsync() {
        CompletableFuture.runAsync(() -> AppContext.balance().loadFromJson(enemyBalancePath))
            .thenRun(() -> Platform.runLater(this::refreshSelectedProfile));
    }
    private void saveProfilesAsync() {
        CompletableFuture.runAsync(() -> AppContext.balance().saveToJson(enemyBalancePath));
    }

    private static void put(TextField f, double v) { if (f != null) f.setText(Double.toString(v)); }
    private static double get(TextField f, double def) {
        try { return Double.parseDouble(f.getText().trim()); } catch (Exception e) { return def; }
    }

    private Path cfgPath() { return Path.of(System.getProperty("user.home"), ".layla", "stats.json"); }

    private void loadUserJsonIfExists() {
        try {
            if (Files.exists(cfgPath())) {
                String json = Files.readString(cfgPath());
                Map<String, Double> m = Json.minimalParse(json);
                if (m == null) return;

                var bal = AppContext.balance();
                if (m.containsKey("maxHp")) bal.maxHp = m.get("maxHp");

                // NUEVO: Cargar stats persistentes
                if (m.containsKey("projDamage")) stats.setBaseStat(PlayerStatId.PROJECTILE_DAMAGE, m.get("projDamage"));
                if (m.containsKey("moveSpeed")) stats.setBaseStat(PlayerStatId.MOVE_SPEED, m.get("moveSpeed"));
                if (m.containsKey("fireRate")) stats.setBaseStat(PlayerStatId.FIRE_RATE, m.get("fireRate"));
                if (m.containsKey("projSpeed")) stats.setBaseStat(PlayerStatId.PROJECTILE_SPEED, m.get("projSpeed"));
                if (m.containsKey("projRange")) stats.setBaseStat(PlayerStatId.PROJECTILE_RANGE, m.get("projRange"));
            }
        } catch (Exception ignored) {}
    }

    private void saveUserJson() {
        try {
            Path p = cfgPath();
            if (p.getParent() != null) Files.createDirectories(p.getParent());

            var bal = AppContext.balance();
            java.util.LinkedHashMap<String, Double> m = new java.util.LinkedHashMap<>();
            m.put("maxHp", bal.maxHp);
            // NUEVO: Guardar stats persistentes
            m.put("projDamage", stats.getBaseStat(PlayerStatId.PROJECTILE_DAMAGE));
            m.put("moveSpeed", stats.getBaseStat(PlayerStatId.MOVE_SPEED));
            m.put("fireRate", stats.getBaseStat(PlayerStatId.FIRE_RATE));
            m.put("projSpeed", stats.getBaseStat(PlayerStatId.PROJECTILE_SPEED));
            m.put("projRange", stats.getBaseStat(PlayerStatId.PROJECTILE_RANGE));

            Files.writeString(p, Json.toJson(m));
        } catch (Exception ignored) {}
    }

    // Helper minimalista para JSON
    static class Json {
        static Map<String, Double> minimalParse(String json) {
            try {
                java.util.HashMap<String, Double> out = new java.util.HashMap<>();
                String s = json.trim();
                if (!s.startsWith("{") || !s.endsWith("}")) return null;
                s = s.substring(1, s.length() - 1).trim();
                if (s.isEmpty()) return out;
                for (String part : s.split(",")) {
                    String[] kv = part.split(":");
                    if (kv.length < 2) continue;
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
