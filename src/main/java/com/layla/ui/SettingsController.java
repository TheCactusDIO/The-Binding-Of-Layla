package com.layla.ui;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

/**
 * Controlador del menú de ajustes.
 *
 * Responsabilidades:
 * - Ajustes de audio (música/SFX) y fullscreen.
 * - Herramientas de desarrollo / custom run (stats base, monedas, multiplicadores, perfiles de enemigos).
 * - Persistencia opcional de algunos valores de debug en un JSON del usuario (stats.json).
 *
 * Importante:
 * - Mover los sliders aplica el volumen "en caliente" a AssetsManager,
 *   pero NO guarda en disco hasta pulsar "Aplicar".
 */
public final class SettingsController {

    // =========================
    // RUTAS / JSON
    // =========================

    /** Ajustes persistentes de debug (stats base). */
    private static final Path DEV_STATS_PATH =
            Path.of(System.getProperty("user.home"), ".layla", "stats.json");

    /** Balance de enemigos editable desde panel (enemy_balance.json). */
    private static final Path ENEMY_BALANCE_PATH =
            Path.of(System.getProperty("user.home"), ".layla", "enemy_balance.json");

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    // =========================
    // FXML - Audio
    // =========================

    @FXML private Slider musicSlider;
    @FXML private Slider sfxSlider;
    @FXML private CheckBox fullscreenCheck;

    // =========================
    // FXML - Dev / Custom Run
    // =========================

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
    @FXML private TextField projRangeField;

    @FXML private Spinner<Integer> coinsSpinner;
    @FXML private CheckBox persistCheck;

    // =========================
    // FXML - Enemy profiles
    // =========================

    @FXML private ComboBox<EnemyType> enemyTypeCombo;
    @FXML private TextField profileHpField;
    @FXML private TextField profileSpeedField;
    @FXML private TextField profileContactField;
    @FXML private TextField profileProjDmgField;
    @FXML private CheckBox stationaryCheck;
    @FXML private Button saveBtn;

    // =========================
    // ESTADO / DEPENDENCIAS
    // =========================

    private Integer initialCoins;
    private IntConsumer onCoinsChanged;

    private Runnable onClose = () -> {};
    private Runnable onStatsChanged = () -> {};

    private StatsService stats = AppContext.stats();
    private final ConfigService config = AppContext.config();

    private StackPane overlayHost;

    /** Para evitar que "setValue" de UI dispare listeners como si fuera input del usuario. */
    private boolean updatingUi;

    /** Guardamos listeners para poder re-bindeár sin duplicarlos. */
    private final Map<TextField, ChangeListener<String>> profileBindings = new HashMap<>();
    private ChangeListener<Boolean> stationaryBinding;

    // =========================
    // API pública del controlador
    // =========================

    public void setOnClose(Runnable r) {
        this.onClose = (r != null) ? r : () -> {};
    }

    public void setOnStatsChanged(Runnable r) {
        this.onStatsChanged = (r != null) ? r : () -> {};
    }

    public void setStatsService(StatsService s) {
        if (s != null) this.stats = s;
    }

    public void setOverlayHost(StackPane host) {
        this.overlayHost = host;
    }

    /**
     * Permite que el juego le pase las monedas iniciales actuales para que el spinner muestre ese valor.
     */
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

    // =========================
    // CICLO DE VIDA
    // =========================

    /**
     * Inicialización JavaFX:
     * - Configura sliders/spinners.
     * - Conecta listeners.
     * - Prepara el panel de perfiles de enemigo.
     */
    @FXML
    private void initialize() {
        setupAudioControls();
        setupSpinners();
        setupDebugBindings();
        setupEnemyProfilesPanel();
    }

    /**
     * Hook que se llama cuando el overlay se muestra.
     * Aquí sincronizamos UI con el estado real del juego/config.
     */
    public void onShow() {
        // 1) Audio / fullscreen desde config
        syncAudioUiFromConfig();

        // 2) Cargar persistencia opcional (stats.json)
        loadUserJsonIfExists();

        // 3) Refrescar UI con balance/stats actuales
        var bal = AppContext.balance();
        var mods = AppContext.getRunModifiers();

        put(maxHpField, bal.maxHp);

        var baseStats = stats.getBaseStats();
        put(moveSpeedField, baseStats.getBase(PlayerStatId.MOVE_SPEED));
        put(fireRateField,  baseStats.getBase(PlayerStatId.FIRE_RATE));
        put(projSpeedField, baseStats.getBase(PlayerStatId.PROJECTILE_SPEED));
        put(projDamageField,baseStats.getBase(PlayerStatId.PROJECTILE_DAMAGE));
        put(projRangeField, baseStats.getBase(PlayerStatId.PROJECTILE_RANGE));

        if (coinsSpinner != null && coinsSpinner.getValueFactory() != null) {
            int coinsToShow = (initialCoins != null) ? initialCoins : Math.max(0, bal.startCoins);
            coinsSpinner.getValueFactory().setValue(coinsToShow);
        }

        if (spawnRateSpinner != null && spawnRateSpinner.getValueFactory() != null) {
            spawnRateSpinner.getValueFactory().setValue(mods.spawnRateMult);
        }
        if (wavesSpinner != null && wavesSpinner.getValueFactory() != null) {
            wavesSpinner.getValueFactory().setValue(mods.wavesPerFloor);
        }
        if (globalHpMultSpinner != null && globalHpMultSpinner.getValueFactory() != null) {
            globalHpMultSpinner.getValueFactory().setValue(mods.enemyHpMult);
        }
        if (globalDmgMultSpinner != null && globalDmgMultSpinner.getValueFactory() != null) {
            globalDmgMultSpinner.getValueFactory().setValue(mods.enemyDmgMult);
        }

        refreshSelectedProfile();
    }

    // =========================
    // BOTONES
    // =========================

    /**
     * Aplica:
     * - Configuración persistente (audio/fullscreen) y la guarda en disco.
     * - Stats/base y balance.
     * - Modificadores de run.
     * - Guardado opcional de stats debug en stats.json.
     */
    @FXML
    private void onApply() {
        // --- Config persistente ---
        if (musicSlider != null) config.setMusicVolume(musicSlider.getValue());
        if (sfxSlider != null)   config.setSfxVolume(sfxSlider.getValue());
        if (fullscreenCheck != null) config.setFullscreen(fullscreenCheck.isSelected());
        config.save();

        // Aplicar fullscreen al Stage si procede
        Stage stage = SceneRouter.getStage();
        if (stage != null && fullscreenCheck != null) {
            boolean want = fullscreenCheck.isSelected();
            if (stage.isFullScreen() != want) stage.setFullScreen(want);
        }

        // --- Balance + Stats base ---
        var bal = AppContext.balance();

        double hp = get(maxHpField, bal.maxHp);
        bal.maxHp = hp;
        bal.startHp = hp;
        stats.setBaseStat(PlayerStatId.MAX_HEALTH, hp);

        stats.setBaseStat(PlayerStatId.MOVE_SPEED,        get(moveSpeedField, stats.getBaseStat(PlayerStatId.MOVE_SPEED)));
        stats.setBaseStat(PlayerStatId.FIRE_RATE,         get(fireRateField,  stats.getBaseStat(PlayerStatId.FIRE_RATE)));
        stats.setBaseStat(PlayerStatId.PROJECTILE_SPEED,  get(projSpeedField, stats.getBaseStat(PlayerStatId.PROJECTILE_SPEED)));
        stats.setBaseStat(PlayerStatId.PROJECTILE_DAMAGE, get(projDamageField,stats.getBaseStat(PlayerStatId.PROJECTILE_DAMAGE)));
        stats.setBaseStat(PlayerStatId.PROJECTILE_RANGE,  get(projRangeField, stats.getBaseStat(PlayerStatId.PROJECTILE_RANGE)));

        // --- Monedas ---
        if (coinsSpinner != null && coinsSpinner.getValue() != null) {
            int coins = Math.max(0, coinsSpinner.getValue());
            bal.startCoins = coins;
            if (onCoinsChanged != null) onCoinsChanged.accept(coins);
        }

        // --- Modificadores de run ---
        var mods = AppContext.getRunModifiers();
        if (spawnRateSpinner != null && spawnRateSpinner.getValue() != null) mods.spawnRateMult = spawnRateSpinner.getValue();
        if (wavesSpinner != null && wavesSpinner.getValue() != null)         mods.wavesPerFloor = wavesSpinner.getValue();
        if (globalHpMultSpinner != null && globalHpMultSpinner.getValue() != null) mods.enemyHpMult = globalHpMultSpinner.getValue();
        if (globalDmgMultSpinner != null && globalDmgMultSpinner.getValue() != null) mods.enemyDmgMult = globalDmgMultSpinner.getValue();

        // --- Persistencia opcional ---
        if (persistCheck != null && persistCheck.isSelected()) {
            saveUserJson();
        }

        onStatsChanged.run();
        onClose.run();
    }

    /**
     * Restaura valores por defecto (runtime) para balance/stats y valores UI de audio.
     * Nota: NO guarda en disco hasta que el usuario pulse Aplicar.
     */
    @FXML
    private void onReset() {
        stats.getBaseStats().resetDefaults();
        AppContext.balance().resetDefaults();
        AppContext.balance().startHp = AppContext.balance().maxHp;
        AppContext.getRunModifiers().reset();

        // Valores por defecto “UI”
        if (musicSlider != null) musicSlider.setValue(0.5);
        if (sfxSlider != null)   sfxSlider.setValue(0.8);
        if (fullscreenCheck != null) fullscreenCheck.setSelected(false);

        onShow();
        onStatsChanged.run();
    }

    /**
     * Cancela cambios no aplicados:
     * - Restaura los volúmenes activos a lo guardado (config).
     * - Cierra el overlay.
     */
    @FXML
    private void onCancel() {
        AssetsManager.setMusicVolume(config.getMusicVolume());
        AssetsManager.setSfxVolume(config.getSfxVolume());
        onClose.run();
    }

    // =========================
    // SETUP UI
    // =========================

    /**
     * Configura sliders de audio:
     * - Inicializa con valores del config.
     * - Listener en caliente para escuchar el cambio mientras mueves el slider.
     */
    private void setupAudioControls() {
        syncAudioUiFromConfig();

        if (musicSlider != null) {
            musicSlider.valueProperty().addListener((obs, oldV, newV) -> {
                if (updatingUi) return;
                AssetsManager.setMusicVolume(newV.doubleValue());
            });
        }
        if (sfxSlider != null) {
            sfxSlider.valueProperty().addListener((obs, oldV, newV) -> {
                if (updatingUi) return;
                AssetsManager.setSfxVolume(newV.doubleValue());
            });
        }
    }

    /**
     * Sincroniza UI de audio/fullscreen desde el ConfigService.
     * Se usa al inicializar y al mostrar el overlay.
     */
    private void syncAudioUiFromConfig() {
        updatingUi = true;
        try {
            if (musicSlider != null) musicSlider.setValue(config.getMusicVolume());
            if (sfxSlider != null)   sfxSlider.setValue(config.getSfxVolume());
            if (fullscreenCheck != null) fullscreenCheck.setSelected(config.isFullscreen());
        } finally {
            updatingUi = false;
        }

        // Blindaje: asegurar que el runtime está con el config (por si algo lo cambió antes)
        AssetsManager.setMusicVolume(config.getMusicVolume());
        AssetsManager.setSfxVolume(config.getSfxVolume());
    }

    /**
     * Configura spinners con sus rangos y valores iniciales.
     */
    private void setupSpinners() {
        setupSpinner(coinsSpinner, 0, 9999, 0, 1);
        setupSpinner(wavesSpinner, 1, 50, 5, 1);

        setupDoubleSpinner(spawnRateSpinner, 0.1, 10.0, 1.0, 0.1);
        setupDoubleSpinner(globalHpMultSpinner, 0.1, 10.0, 1.0, 0.1);
        setupDoubleSpinner(globalDmgMultSpinner, 0.1, 10.0, 1.0, 0.1);
    }

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

    /**
     * Pequeñas mejoras de UX:
     * - Auto-seleccionar texto al enfocar campos numéricos.
     */
    private void setupDebugBindings() {
        Consumer<TextField> selectAll = tf ->
                tf.focusedProperty().addListener((o, old, focused) -> {
                    if (Boolean.TRUE.equals(focused)) tf.selectAll();
                });

        if (maxHpField != null) selectAll.accept(maxHpField);
        if (moveSpeedField != null) selectAll.accept(moveSpeedField);
        if (fireRateField != null) selectAll.accept(fireRateField);
        if (projSpeedField != null) selectAll.accept(projSpeedField);
        if (projDamageField != null) selectAll.accept(projDamageField);
        if (projRangeField != null) selectAll.accept(projRangeField);

        if (profileHpField != null) selectAll.accept(profileHpField);
        if (profileSpeedField != null) selectAll.accept(profileSpeedField);
        if (profileContactField != null) selectAll.accept(profileContactField);
        if (profileProjDmgField != null) selectAll.accept(profileProjDmgField);
    }

    // =========================
    // ENEMY PROFILES PANEL
    // =========================

    /**
     * Inicializa el panel de perfiles:
     * - Rellena el combo con EnemyType.
     * - Bindea campos al perfil seleccionado.
     * - Carga y guarda enemy_balance.json en background.
     */
    private void setupEnemyProfilesPanel() {
        if (enemyTypeCombo == null) return;

        enemyTypeCombo.getItems().setAll(EnemyType.values());
        enemyTypeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldT, newT) -> {
            if (newT != null) bindProfile(AppContext.balance().profile(newT));
        });

        if (!enemyTypeCombo.getItems().isEmpty()) {
            enemyTypeCombo.getSelectionModel().select(EnemyType.SHOOTER);
        }

        if (saveBtn != null) saveBtn.setOnAction(e -> saveProfilesAsync());

        loadProfilesAsync();
        refreshSelectedProfile();
    }

    /**
     * Bindea los campos de la UI al EnemyProfile actual.
     * Cada cambio en los TextField actualiza el objeto en memoria.
     */
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

    /**
     * Conecta un TextField a un getter/setter numérico.
     * Re-bindea limpiando listeners previos para evitar duplicados.
     */
    private void bindNumberField(TextField tf, DoubleSupplier getter, DoubleConsumer setter) {
        if (tf == null) return;

        ChangeListener<String> prev = profileBindings.remove(tf);
        if (prev != null) tf.textProperty().removeListener(prev);

        tf.setText(Double.toString(getter.getAsDouble()));

        ChangeListener<String> listener = (obs, oldV, newV) -> {
            if (newV == null) return;
            String s = newV.trim();
            if (s.isEmpty() || "-".equals(s) || ".".equals(s)) return;
            try {
                setter.accept(Double.parseDouble(s));
            } catch (Exception ignore) {
                // input inválido: no rompemos nada, simplemente no aplicamos
            }
        };

        tf.textProperty().addListener(listener);
        profileBindings.put(tf, listener);
    }

    private void refreshSelectedProfile() {
        if (enemyTypeCombo == null) return;
        EnemyType t = enemyTypeCombo.getSelectionModel().getSelectedItem();
        if (t != null) bindProfile(AppContext.balance().profile(t));
    }

    private void loadProfilesAsync() {
        CompletableFuture
                .runAsync(() -> AppContext.balance().loadFromJson(ENEMY_BALANCE_PATH))
                .thenRun(() -> Platform.runLater(this::refreshSelectedProfile));
    }

    private void saveProfilesAsync() {
        CompletableFuture.runAsync(() -> AppContext.balance().saveToJson(ENEMY_BALANCE_PATH));
    }

    // =========================
    // PERSISTENCIA DEV (stats.json)
    // =========================

    /**
     * Carga stats de debug persistidas (si existen) y las aplica al balance/stats.
     * Se usa para “runs custom” y herramientas de dev.
     */
    private void loadUserJsonIfExists() {
        try {
            if (!Files.exists(DEV_STATS_PATH)) return;

            try (Reader r = Files.newBufferedReader(DEV_STATS_PATH)) {
                PersistedDevStats dto = GSON.fromJson(r, PersistedDevStats.class);
                if (dto == null) return;

                var bal = AppContext.balance();

                if (dto.maxHp != null) {
                    bal.maxHp = dto.maxHp;
                    bal.startHp = dto.maxHp;
                    stats.setBaseStat(PlayerStatId.MAX_HEALTH, dto.maxHp);
                }

                if (dto.moveSpeed != null) stats.setBaseStat(PlayerStatId.MOVE_SPEED, dto.moveSpeed);
                if (dto.fireRate != null)  stats.setBaseStat(PlayerStatId.FIRE_RATE, dto.fireRate);
                if (dto.projSpeed != null) stats.setBaseStat(PlayerStatId.PROJECTILE_SPEED, dto.projSpeed);
                if (dto.projDamage != null)stats.setBaseStat(PlayerStatId.PROJECTILE_DAMAGE, dto.projDamage);
                if (dto.projRange != null) stats.setBaseStat(PlayerStatId.PROJECTILE_RANGE, dto.projRange);
            }
        } catch (Exception e) {
            System.err.println("[SettingsController] Error cargando stats.json: " + e.getMessage());
        }
    }

    /**
     * Guarda stats de debug actuales a stats.json (solo si el usuario marcó persistencia).
     */
    private void saveUserJson() {
        try {
            if (DEV_STATS_PATH.getParent() != null) {
                Files.createDirectories(DEV_STATS_PATH.getParent());
            }

            PersistedDevStats dto = new PersistedDevStats();
            dto.maxHp = AppContext.balance().maxHp;
            dto.moveSpeed = stats.getBaseStat(PlayerStatId.MOVE_SPEED);
            dto.fireRate = stats.getBaseStat(PlayerStatId.FIRE_RATE);
            dto.projSpeed = stats.getBaseStat(PlayerStatId.PROJECTILE_SPEED);
            dto.projDamage = stats.getBaseStat(PlayerStatId.PROJECTILE_DAMAGE);
            dto.projRange = stats.getBaseStat(PlayerStatId.PROJECTILE_RANGE);

            try (Writer w = Files.newBufferedWriter(DEV_STATS_PATH)) {
                GSON.toJson(dto, w);
            }

        } catch (Exception e) {
            System.err.println("[SettingsController] Error guardando stats.json: " + e.getMessage());
        }
    }

    /**
     * DTO del JSON para persistencia de stats de debug.
     * Usamos wrappers (Double) para poder distinguir “no estaba” de “0.0”.
     */
    private static final class PersistedDevStats {
        Double maxHp;
        Double moveSpeed;
        Double fireRate;
        Double projSpeed;
        Double projDamage;
        Double projRange;
    }

    // =========================
    // HELPERS
    // =========================

    private static void put(TextField f, double v) {
        if (f != null) f.setText(Double.toString(v));
    }

    private static double get(TextField f, double def) {
        if (f == null) return def;
        try {
            return Double.parseDouble(Objects.toString(f.getText(), "").trim());
        } catch (Exception e) {
            return def;
        }
    }
}
