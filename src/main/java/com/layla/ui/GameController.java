package com.layla.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.layla.AppContext;
import com.layla.core.AssetsManager;
import com.layla.core.GameEntity;
import com.layla.core.GameLoop;
import com.layla.core.InputService;
import com.layla.db.DatabaseService;
import com.layla.entities.Boss;
import com.layla.entities.BossCharger;
import com.layla.entities.BossHive;
import com.layla.entities.BossSniper;
import com.layla.entities.ChocoCat;
import com.layla.entities.Coin;
import com.layla.entities.GreedButton;
import com.layla.entities.ItemPedestal;
import com.layla.entities.Player;
import com.layla.entities.Rock;
import com.layla.entities.SpawnIndicator;
import com.layla.items.ItemDefinition;
import com.layla.items.ItemId;
import com.layla.items.ItemPoolType;
import com.layla.items.ItemRegistry;
import com.layla.model.CharacterType;
import com.layla.model.Enemy;
import com.layla.model.EnemyProfile;
import com.layla.model.EnemyType;
import com.layla.model.PlayerStatId;
import com.layla.services.AchievementService;
import com.layla.services.ShootingService;
import com.layla.services.SoundService;
import com.layla.services.StatsService;
import com.layla.ui.ShopOverlayController.ShopOffer;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

/**
 * Controlador principal de gameplay.
 *
 * <p>
 * Responsabilidades:
 * </p>
 * <ul>
 *   <li>Gestionar el ciclo de vida de la vista (entrar/salir) y el {@link GameLoop}.</li>
 *   <li>Spawnear y mantener entidades principales (jugador, enemigos, boss, tendero, botón Greed, obstáculos).</li>
 *   <li>Gestionar HUD/overlays (pausa, tienda, game over, victoria, pickup de ítems).</li>
 *   <li>Controlar la lógica de oleadas/pisos del modo Avaricia (Greed), recompensas y música.</li>
 *   <li>Aplicar colisiones contra obstáculos (incluyendo paredes invisibles que ajustan el área jugable).</li>
 * </ul>
 *
 * <p>
 * Nota: El background está en modo “stretch” (fitWidth/fitHeight bindados) y <b>no</b> debe tocarse con setters
 * directos cuando ya está bindado (provoca excepción).
 * </p>
 */
public class GameController implements ViewLifecycle {

    // =====================================================================
    // TEXTOS UI (HARDCODEADOS EN ESPAÑOL)
    // =====================================================================

    private static final String TXT_PUNTUACION = "Puntuación";
    private static final String TXT_MONEDAS = "Monedas";

    private static final String TXT_LISTO = "Listo";
    private static final String TXT_OLEADA = "Oleada";
    private static final String TXT_SIGUIENTE = "Siguiente";
    private static final String TXT_JEFE = "JEFE";
    private static final String TXT_PAUSA = "PAUSA";
    private static final String TXT_INICIO = "INICIO";
    private static final String TXT_LIMPIO = "LIMPIO";

    // Zonas / pisos
    private static final String ZONA_SOTANO = "Sótano";
    private static final String ZONA_CUEVAS = "Cuevas";
    private static final String ZONA_PROFUNDIDADES = "Profundidades";
    private static final String ZONA_UTERO = "Útero";
    private static final String ZONA_SEOL = "Seol";

    // Notificaciones
    private static final String NOTI_MODO_AVARICIA_T = "MODO AVARICIA";
    private static final String NOTI_MODO_AVARICIA_D = "Toca el botón para empezar";

    private static final String NOTI_PAUSA_T = "¡PAUSA!";
    private static final String NOTI_PAUSA_D = "-1 vida, -5 monedas";

    private static final String NOTI_OLEADA_RAPIDA_T = "¡OLEADA RÁPIDA!";
    private static final String NOTI_OLEADA_RAPIDA_D = "+1 moneda";

    private static final String NOTI_DESBORDADO_T = "¡DESBORDADO!";
    private static final String NOTI_DESBORDADO_D = "¡Las oleadas se acumulan!";

    private static final String NOTI_PISO_T = "PISO ";
    private static final String NOTI_PISO_D = "Te esperan nuevos desafíos";

    // Tienda
    private static final String TXT_TIENDA = "TIENDA";

    // Game over / victoria
    private static final String TXT_GAME_OVER = "FIN DE LA PARTIDA";
    private static final String TXT_VICTORIA = "¡VICTORIA!";

    // =====================================================================
    // FXML / NODOS PRINCIPALES
    // =====================================================================

    @FXML private Pane gameArea;
    @FXML private HBox hudBar;
    @FXML private Label scoreLabel;
    @FXML private Label coinLabel;
    @FXML private Label floorLabel;
    @FXML private Label healthLabel; // Ignorado/Oculto
    @FXML private StackPane root;
    @FXML private StackPane overlayLayer;

    // =====================================================================
    // BACKGROUND
    // =====================================================================

    /** Vista del fondo (se mantiene en índice 0 dentro de gameArea). */
    private ImageView backgroundView;

    /** Si el fondo ya tiene sus bindings para modo “stretch”. */
    private boolean bgStretchBound = false;

    // =====================================================================
    // OVERLAYS / UI
    // =====================================================================

    private Node pauseOverlay;
    private Node gameOverOverlay;
    private Node shopOverlay;

    private VBox bossHealthBox;
    private ProgressBar bossHealthBar;
    private Label bossNameLabel;

    // =====================================================================
    // ENTIDADES / ESTADO DE JUEGO
    // =====================================================================

    /** Tendero físico dentro de la sala. */
    private GameEntity shopKeeperEntity;

    /** Cooldown para evitar re-entrada inmediata a la tienda (p.ej. al cerrarla). */
    private double shopCooldown = 0.0;

    /** Referencia al NPC ChocoCat para poder limpiarlo/respawnearlo en transiciones. */
    private ChocoCat chocoEntity;

    /** Indicadores de spawn (para limpiar correctamente al cambiar de piso/sala). */
    private final List<SpawnIndicator> spawnIndicators = new ArrayList<>();

    /** Ofertas actuales de la tienda (persisten durante el piso). */
    private List<ShopOffer> currentShopOffers = new ArrayList<>();

    /** Precio actual del reroll (escala durante el piso). */
    private int currentRerollPrice = 1;

    /** Precio del corazón (escala con compras durante el piso). */
    private int currentHeartPrice = 2;

    // =====================================================================
    // MÚSICA
    // =====================================================================

    /** Indica si ya se inició música al empezar la run (para evitar re-elegir). */
    private boolean musicStarted = false;

    /** Track elegido para el piso actual (se reutiliza al volver de boss). */
    private String currentFloorMusicFile = null;

    /** Track del boss actual (si hay). */
    private String currentBossMusicFile = null;

    // =====================================================================
    // BOSSES SIN REPETIR (PISOS 1-4)
    // =====================================================================

    /**
     * Baraja de bosses (pisos 1-4) sin repetición.
     * 0 = Spreader, 1 = Sentry, 2 = Brute, 3 = Hive
     */
    private final List<Integer> bossDeck = new ArrayList<>(4);

    // =====================================================================
    // TIMERS GLOBALES (SFX)
    // =====================================================================

    private static final double ENEMY_PRESENCE_INTERVAL = 3.0;
    private double enemyPresenceTimer = 0.0;

    private static final double PLAYER_HURT_SFX_COOLDOWN = 0.12;
    private double playerHurtSfxTimer = 0.0;

    // =====================================================================
    // SCORE / MONEDAS / HUD
    // =====================================================================

    private int score = 500;
    private int coins = Math.max(0, AppContext.balance().startCoins);
    private Label timeLabel;

    // =====================================================================
    // OLAS / PISOS
    // =====================================================================

    private int wavesPerFloor = 5;
    private static final int MAX_FLOORS = 5;

    private int currentFloor = 1;
    private int currentWave = 0;

    private double nextWaveTimer = 0.0;
    private int pendingSpawns = 0;
    private boolean waveInProgress = false;

    // Estado Greed
    private boolean timerStopped = false;
    private double buttonSafetyTimer = 0.0;

    /**
     * Campos reservados para un futuro “spawn constante”.
     * Se mantienen para no alterar comportamiento actual (aunque la lógica esté desactivada).
     */
    private double constantSpawnInterval;
    private double timeUntilNextSpawn = 0.0;

    private boolean gameStarted = false;
    private boolean paused = false;
    private boolean gameOverShown = false;

    /** Bandera estática para indicar que se debe iniciar partida al cargar (REINICIO). */
    private static boolean restartPending = false;

    private GameLoop gameLoop;
    private Player player;

    /** Para enganchar listeners de spawn una sola vez. */
    private boolean playerSpawnListenerAdded = false;

    /** “Puerta de inicio”: hasta que no esté abierta, no spawnea el jugador. */
    private boolean startGateOpen = false;

    private Boss activeBoss = null;
    private GreedButton greedButton;

    private InputService input;

    private final StatsService statsService = AppContext.stats();
    private ShootingService shootingService;

    private final SoundService sound = new SoundService();
    private final DatabaseService db = AppContext.db();
    private final AchievementService achievements = AppContext.achievements();

    /** Entidad “ticker” para ejecutar lógica global (timers, colisiones, oleadas, etc.). */
    private GameEntity ticker;

    private final List<Enemy> enemies = new ArrayList<>();
    private final List<GameEntity> obstacles = new ArrayList<>();

    // =====================================================================
    // PAREDES INVISIBLES (LÍMITES DEL “MURO” DECORATIVO DEL BG)
    // =====================================================================

    /** Grosor de paredes invisibles (ajustar para que coincida con borde del fondo). */
    private static final double BG_WALL_THICKNESS_LEFT = 120.0;
    private static final double BG_WALL_THICKNESS_RIGHT = 120.0;
    private static final double BG_WALL_THICKNESS_TOP = 80.0;
    private static final double BG_WALL_THICKNESS_BOTTOM = 100.0;

    private InvisibleWall wallTop;
    private InvisibleWall wallBottom;
    private InvisibleWall wallLeft;
    private InvisibleWall wallRight;

    /** Si ya se crearon paredes (para helpers de limpieza). */
    private boolean boundaryWallsAdded = false;

    /** Evita añadir paredes duplicadas al {@link GameLoop}. */
    private boolean boundaryWallsInLoop = false;

    // =====================================================================
    // RNG / CONSTANTES DE GAMEPLAY
    // =====================================================================

    private final ThreadLocalRandom enemyRng = ThreadLocalRandom.current();

    private static final int SHOP_OFFER_COUNT = 3;
    private static final int SHOP_REROLL_BASE_PRICE = 1;

    private static final int SCORE_PENALTY_PER_SECOND = 1;
    private static final int SCORE_PENALTY_ON_DAMAGE = 20;
    private static final int SCORE_PENALTY_ON_BUY = 30;

    private int comboCount = 0;
    private double comboMultiplier = 1.0;
    private double comboTimer = 0.0;
    private static final double COMBO_MAX_TIME = 3.5;

    private double scoreTimer = 0.0;
    private double lastPlayerHealth = -1.0;

    private int rewardItemCursor = 0;

    private HudView hud;
    private ItemHudView itemHud;

    private double hudRefreshTimer = 0.0;

    /** Evita disparar durante los primeros instantes tras spawnear al jugador. */
    private boolean shootingArmed = false;
    private double shootingArmTimer = 0.6;

    // =====================================================================
    // BOSS DECK (SIN REPETIR)
    // =====================================================================

    /**
     * Reinicia la baraja de bosses (pisos 1-4) para que no se repitan durante la run.
     */
    private void resetBossDeckForRun() {
        bossDeck.clear();
        bossDeck.add(0);
        bossDeck.add(1);
        bossDeck.add(2);
        bossDeck.add(3);
        Collections.shuffle(bossDeck, enemyRng);
    }

    /**
     * Devuelve el tipo de boss para el piso indicado intentando que no se repita entre pisos 1-4.
     *
     * @param floor piso actual (1..4)
     * @return índice de boss (0..3)
     */
    private int pickBossTypeForFloor(int floor) {
        if (bossDeck.isEmpty()) resetBossDeckForRun();

        int idx = floor - 1;
        if (idx < 0) idx = 0;
        if (idx >= bossDeck.size()) idx = bossDeck.size() - 1;

        return bossDeck.get(idx);
    }

    // =====================================================================
    // PAUSA / REANUDAR
    // =====================================================================

    /**
     * Handler de pausa (ESC).
     *
     * <p>
     * Detiene el {@link GameLoop}, actualiza paredes y muestra overlay de pausa.
     * Importante: <b>no</b> tocar el fondo aquí (está bindado).
     * </p>
     */
    @FXML
    private void onPausePressed() {
        if (gameOverShown || pauseOverlay != null || paused) return;
        paused = true;

        Platform.runLater(() -> {
            if (gameLoop != null && gameLoop.isRunning()) gameLoop.stop();
            updateBoundaryWalls();
        });

        pauseOverlay = OverlayRouter.showOverlay(overlayLayer, "ui/pause_overlay.fxml", 0.50, controller -> {
            if (controller instanceof PauseOverlayController poc) {
                poc.setOnResume(this::resumeFromPause);
                poc.setOnBackToMenu(() -> {
                    OverlayRouter.closeOverlay(overlayLayer, pauseOverlay);
                    pauseOverlay = null;

                    Platform.runLater(() -> {
                        if (gameLoop != null && gameLoop.isRunning()) gameLoop.stop();
                        paused = false;
                        backToMenu();
                    });
                });
                poc.setOnSettings(() -> {
                    final Node[] settingsNode = new Node[1];
                    settingsNode[0] = OverlayRouter.showOverlay(overlayLayer, "ui/settings.fxml", 0.90, c -> {
                        if (c instanceof SettingsController sc) {
                            sc.setStatsService(statsService);
                            sc.setOverlayHost(overlayLayer);
                            sc.setInitialCoins(coins);
                            sc.setOnCoinsChanged(newCoins -> {
                                coins = Math.max(0, newCoins);
                                updateHudLabels();
                            });
                            sc.setOnClose(() -> OverlayRouter.closeOverlay(overlayLayer, settingsNode[0]));
                        }
                    });
                });
            }
        });
    }

    /**
     * Cierra overlay de pausa y reanuda el {@link GameLoop}.
     */
    private void resumeFromPause() {
        if (pauseOverlay != null) {
            OverlayRouter.closeOverlay(overlayLayer, pauseOverlay);
            pauseOverlay = null;
        }
        Platform.runLater(() -> {
            if (gameLoop != null && !gameLoop.isRunning()) gameLoop.start();
            paused = false;
        });
    }

    // =====================================================================
    // HUD
    // =====================================================================

    /**
     * Formatea segundos en string “SS” (dos dígitos).
     *
     * @param seconds segundos restantes
     * @return texto formateado en 2 dígitos
     */
    private static String formatTime(double seconds) {
        int s = (int) Math.ceil(seconds);
        return String.format("%02d", s);
    }

    /**
     * Actualiza textos del HUD (puntuación, monedas, piso/oleada, temporizador).
     * También refresca barra de vida del boss y el {@link ItemHudView} si existe.
     */
    private void updateHudLabels() {
        String mult = comboMultiplier > 1.0 ? String.format(" (x%.1f)", comboMultiplier) : "";

        if (scoreLabel != null) scoreLabel.setText(TXT_PUNTUACION + ": " + score + mult);
        if (coinLabel != null) coinLabel.setText(TXT_MONEDAS + ": " + coins);

        String zona = switch (currentFloor) {
            case 1 -> ZONA_SOTANO;
            case 2 -> ZONA_CUEVAS;
            case 3 -> ZONA_PROFUNDIDADES;
            case 4 -> ZONA_UTERO;
            default -> ZONA_SEOL;
        };

        String oleadaTxt = (currentWave == 0) ? TXT_LISTO : (currentWave + "/" + wavesPerFloor);
        if (floorLabel != null) floorLabel.setText(zona + " - " + TXT_OLEADA + " " + oleadaTxt);

        if (timeLabel != null) {
            if (activeBoss != null) {
                timeLabel.setText(TXT_JEFE);
                timeLabel.setStyle("-fx-text-fill: #ff0000; -fx-font-weight: bold;");
            } else if (waveInProgress) {
                if (timerStopped) {
                    timeLabel.setText(TXT_PAUSA);
                    timeLabel.setStyle("-fx-text-fill: #aaaaff; -fx-font-weight: bold;");
                } else {
                    timeLabel.setText(TXT_SIGUIENTE + ": " + formatTime(nextWaveTimer));
                    timeLabel.setStyle("-fx-text-fill: white;");
                    if (nextWaveTimer <= 3.0) {
                        timeLabel.setStyle("-fx-text-fill: #ff5555; -fx-effect: dropshadow(gaussian, red, 10, 0.5, 0, 0);");
                    }
                }
            } else {
                timeLabel.setText(currentWave == 0 ? TXT_INICIO : TXT_LIMPIO);
                timeLabel.setStyle("-fx-text-fill: #55ff55;");
            }
        }

        if (activeBoss != null && bossHealthBar != null) {
            double maxHp = Math.max(0.0001, activeBoss.getMaxHp());
            double v = activeBoss.getHp() / maxHp;
            bossHealthBar.setProgress(Math.max(0.0, Math.min(1.0, v)));
        }

        if (itemHud != null) {
            itemHud.toFront();
            itemHud.refresh();
        }
    }

    /**
     * Inicialización JavaFX: configura HUD (timer), crea barra de boss y la añade al overlay.
     */
    @FXML
    private void initialize() {
        // Ocultar/retirar label antiguo de vida si existe (se reemplaza por HUD custom).
        if (hudBar != null && healthLabel != null) {
            hudBar.getChildren().remove(healthLabel);
        }

        // Timer HUD
        if (hudBar != null) {
            if (timeLabel == null) {
                timeLabel = new Label();
                timeLabel.getStyleClass().add("hud-timer");
                timeLabel.setStyle("-fx-font-size: 24px;");
            }
            if (!hudBar.getChildren().contains(timeLabel)) {
                hudBar.getChildren().add(hudBar.getChildren().size(), timeLabel);
            }
        }

        // Boss HUD
        bossHealthBar = new ProgressBar(1.0);
        bossHealthBar.setPrefWidth(600);
        bossHealthBar.setStyle("-fx-accent: #cc0000; -fx-control-inner-background: #333333; -fx-text-box-border: transparent;");

        bossNameLabel = new Label(TXT_JEFE);
        bossNameLabel.setStyle("-fx-text-fill: #ffaaaa; -fx-font-weight: bold; -fx-font-size: 18px; "
                + "-fx-effect: dropshadow(gaussian, black, 2, 1, 0, 0);");

        bossHealthBox = new VBox(4, bossNameLabel, bossHealthBar);
        bossHealthBox.setAlignment(Pos.CENTER);
        bossHealthBox.setManaged(true);
        bossHealthBox.setVisible(false);

        if (overlayLayer != null) {
            overlayLayer.getChildren().add(bossHealthBox);

            StackPane.setAlignment(bossHealthBox, Pos.BOTTOM_CENTER);
            StackPane.setMargin(bossHealthBox, new Insets(0, 0, 18, 0));

            // Para que no bloquee clics
            bossHealthBox.setMouseTransparent(true);
        }

        updateHudLabels();
    }

    // =====================================================================
    // BALANCE / PLAYER
    // =====================================================================

    /**
     * Aplica el balance (stats base) al jugador ya instanciado en runtime.
     */
    private void applyBalanceToRuntimePlayer() {
        if (player == null) return;

        var bal = AppContext.balance();

        CharacterType charType = AppContext.getSelectedCharacter();
        double initialMaxHp = bal.maxHp;

        if (charType == CharacterType.THE_FRAGILE) {
            initialMaxHp = 2.0;
            statsService.setBaseStat(PlayerStatId.DAÑO_DEL_PROYECTIL, 1.5);
            statsService.setBaseStat(PlayerStatId.VELOCIDAD_DE_MOVIMIENTO, statsService.getBaseStat(PlayerStatId.VELOCIDAD_DE_MOVIMIENTO) * 1.1);
        } else {
            statsService.setBaseStat(PlayerStatId.DAÑO_DEL_PROYECTIL, 1.0);
        }

        statsService.setBaseStat(PlayerStatId.VIDA_MAXIMA, initialMaxHp);
        player.setMaxHealth(statsService.getMaxHealth());
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    // =====================================================================
    // CICLO DE VIDA DE LA VISTA
    // =====================================================================

    /**
     * Se ejecuta al entrar en la vista del juego:
     * <ul>
     *   <li>Resetea el área (anchors/escala/fondo/paredes).</li>
     *   <li>Engancha input + hotkeys.</li>
     *   <li>Prepara loop y listeners para spawnear jugador cuando toque.</li>
     *   <li>Si hay reinicio pendiente, lanza {@link #signalGameStart()}.</li>
     * </ul>
     */
    @Override
    public void onEnter() {
        this.wavesPerFloor = AppContext.getRunModifiers().wavesPerFloor;

        resetPlainGameArea();

        if (hudBar != null) {
            hudBar.setOpacity(1.0);
            new FadeTransition(Duration.millis(400), hudBar).play();
        }

        NotificationService ns = AppContext.notifications();
        if (root != null && !root.getChildren().contains(ns.getView())) {
            root.getChildren().add(ns.getView());
            StackPane.setAlignment(ns.getView(), Pos.TOP_CENTER);
            StackPane.setMargin(ns.getView(), new Insets(10, 0, 0, 0));
        }

        Platform.runLater(() -> {
            var sc = gameArea.getScene();

            if (input == null) input = new InputService();
            input.attach(sc);

            sc.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    if (!gameOverShown && !paused) onPausePressed();
                    else if (paused) resumeFromPause();
                }
            });

            gameArea.requestFocus();

            // Mantener paredes sincronizadas con resize de la ventana
            gameArea.widthProperty().addListener((obs, ov, nv) -> updateBoundaryWalls());
            gameArea.heightProperty().addListener((obs, ov, nv) -> updateBoundaryWalls());

            // Forzar intento de spawn tras enganchar input (si procede).
            maybeSpawnPlayer();
        });

        if (gameLoop == null) gameLoop = new GameLoop(gameArea);

        if (!playerSpawnListenerAdded) {
            playerSpawnListenerAdded = true;
            gameArea.widthProperty().addListener((obs, ow, nw) -> maybeSpawnPlayer());
            gameArea.heightProperty().addListener((obs, oh, nh) -> maybeSpawnPlayer());
        }

        // Intento inicial (probablemente no spawnee hasta que startGateOpen sea true).
        maybeSpawnPlayer();

        if (restartPending) {
            restartPending = false;
            Platform.runLater(() -> {
                System.out.println("[GameController] Restart pending -> Signal Game Start");
                signalGameStart();
            });
        }
    }

    /**
     * Se ejecuta al salir de la vista:
     * <ul>
     *   <li>Detiene el {@link GameLoop} y desengancha input.</li>
     *   <li>Resetea banderas para evitar fugas de estado entre escenas.</li>
     * </ul>
     */
    @Override
    public void onExit() {
        if (gameLoop != null) gameLoop.stop();
        if (input != null) input.detach();

        startGateOpen = false;
        gameOverShown = false;
        paused = false;
        activeBoss = null;
        waveInProgress = false;

        greedButton = null;
        shopKeeperEntity = null;
        chocoEntity = null;
    }

    // =====================================================================
    // INICIO DE PARTIDA
    // =====================================================================

    /**
     * Señal para empezar la run.
     *
     * <p>
     * Abre “puerta de inicio”, resetea estado principal, prepara tienda inicial, fondo/paredes,
     * spawnea entidades base y arranca música.
     * </p>
     */
    public void signalGameStart() {
        if (startGateOpen) return;

        startGateOpen = true;
        gameStarted = true;

        statsService.resetDefaults();

        this.coins = 0;
        this.score = 500;
        this.currentHeartPrice = 2;

        currentFloor = 1;
        currentWave = 0;
        enemies.clear();
        pendingSpawns = 0;

        // Bosses sin repetir en floors 1-4
        resetBossDeckForRun();

        currentRerollPrice = SHOP_REROLL_BASE_PRICE;
        currentShopOffers = generateShopOffers(SHOP_OFFER_COUNT);

        changeFloorVisuals();
        ensureBoundaryWalls();

        spawnGreedButton();
        spawnShopKeeper();
        spawnChocoCat();

        updateHudLabels();
        maybeSpawnPlayer();
        addTickerIfNeeded();

        currentFloorMusicFile = null;
        currentBossMusicFile = null;
        musicStarted = false; // fuerza música de run nueva
        startFloorMusicIfNeeded();

        sound.warmUp(
                "coin", "shot", "hurt", "hit",
                "enemy_death", "player_death",
                "enemy_presence", "buy", "boss_death"
        );

        enemyPresenceTimer = 0.0;
        playerHurtSfxTimer = 0.0;

        AppContext.notifications().showNotification(NOTI_MODO_AVARICIA_T, NOTI_MODO_AVARICIA_D, 4.0);
    }

    // =====================================================================
    // FONDO (BACKGROUND)
    // =====================================================================

    /**
     * Cambia el fondo según el piso actual.
     *
     * <p>
     * Mantiene el background siempre al fondo (índice 0) y el HUD por delante.
     * </p>
     */
    private void changeFloorVisuals() {
        if (backgroundView == null) {
            backgroundView = new ImageView();
            backgroundView.setManaged(false);
            gameArea.getChildren().add(0, backgroundView);
        }

        // Reinserta al índice 0 por seguridad.
        if (!gameArea.getChildren().contains(backgroundView)) {
            gameArea.getChildren().add(0, backgroundView);
        } else {
            gameArea.getChildren().remove(backgroundView);
            gameArea.getChildren().add(0, backgroundView);
        }

        String file = switch (currentFloor) {
            case 1 -> "basement.png";
            case 2 -> "caves.png";
            case 3 -> "depths.png";
            case 4 -> "womb.png";
            case 5 -> "sheol.png";
            default -> "basement.png";
        };

        // 1) Ruta “principal”
        Image img = AssetsManager.loadImage("assets/background/" + file);

        // 2) Fallbacks
        if (img == null) img = AssetsManager.loadImage("/assets/images/background/" + file);
        if (img == null) img = AssetsManager.loadImage("/assets/background/" + file);

        if (img != null) {
            backgroundView.setImage(img);
            gameArea.setStyle(null);
        } else {
            System.out.println("[GameController] BG not found for floor=" + currentFloor + " file=" + file);
            backgroundView.setImage(null);

            String color = switch (currentFloor) {
                case 1 -> "#2b2010";
                case 2 -> "#202020";
                case 3 -> "#10102b";
                case 4 -> "#4a0000";
                default -> "#000000";
            };
            gameArea.setStyle("-fx-background-color: " + color + ";");
        }

        ensureBackgroundStretchNoPreserveRatio();

        backgroundView.toBack();
        if (hudBar != null) hudBar.toFront();
    }

    /**
     * Activa modo “stretch” sin preserve ratio:
     * <ul>
     *   <li>fitWidth/fitHeight bindados al tamaño de gameArea</li>
     *   <li>layout fijo (0,0)</li>
     * </ul>
     */
    private void ensureBackgroundStretchNoPreserveRatio() {
        if (backgroundView == null) return;
        if (bgStretchBound) return;
        bgStretchBound = true;

        backgroundView.setPreserveRatio(false);
        backgroundView.fitWidthProperty().bind(gameArea.widthProperty());
        backgroundView.fitHeightProperty().bind(gameArea.heightProperty());
        backgroundView.setLayoutX(0);
        backgroundView.setLayoutY(0);
    }

    // =====================================================================
    // BOTÓN GREED
    // =====================================================================

    /**
     * Spawnea el botón central de Greed (start wave / pausar timer / etc.).
     */
    private void spawnGreedButton() {
        removeGreedButtonIfPresent();

        double w = gameArea.getWidth() > 0 ? gameArea.getWidth() : 1280;
        double h = gameArea.getHeight() > 0 ? gameArea.getHeight() : 720;

        double cx = w / 2.0;
        double cy = h / 2.0;

        clearAreaAround(cx, cy, 100);

        greedButton = new GreedButton(cx, cy, gameArea, (btn) -> {
            if (!waveInProgress) {
                if (currentWave <= wavesPerFloor) {
                    btn.setActiveState(true);
                    startNextWave();
                }
                return;
            }

            // Si hay oleada en progreso:
            if (buttonSafetyTimer > 0) return;

            if (!timerStopped && nextWaveTimer > 0) {
                player.takeDamage(1.0);

                coins = Math.max(0, coins - 5);
                updateHudLabels();

                timerStopped = true;
                btn.setActiveState(false);

                AppContext.notifications().showNotification(NOTI_PAUSA_T, NOTI_PAUSA_D, 2.0);
            } else if (!timerStopped) {
                player.takeDamage(0.5);
            }
        });

        greedButton.setActiveState(false);
        gameLoop.addEntity(greedButton);
        checkAndPushInteractive(greedButton);
    }

    /**
     * Elimina el botón Greed si existe (loop + escena + lista de obstáculos si procediera).
     */
    private void removeGreedButtonIfPresent() {
        if (greedButton == null) return;

        gameLoop.removeEntity(greedButton);
        if (greedButton.getView() != null) gameArea.getChildren().remove(greedButton.getView());
        obstacles.remove(greedButton);

        greedButton = null;
    }

    /**
     * Limpia obstáculos alrededor de un punto (útil para que botón/tendero/pedestal tengan espacio).
     *
     * @param x      coordenada X
     * @param y      coordenada Y
     * @param radius radio de limpieza
     */
    private void clearAreaAround(double x, double y, double radius) {
        List<GameEntity> toRemove = new ArrayList<>();

        for (GameEntity obs : obstacles) {
            if (obs == shopKeeperEntity) continue;

            Bounds b = obs.getBounds();
            double dist = Math.hypot(b.getCenterX() - x, b.getCenterY() - y);
            if (dist < radius) {
                toRemove.add(obs);
            }
        }

        for (GameEntity obs : toRemove) {
            if (obs instanceof Rock rock) rock.destroy();
            gameLoop.removeEntity(obs);
            if (obs.getView() != null) gameArea.getChildren().remove(obs.getView());
            obstacles.remove(obs);
        }
    }

    // =====================================================================
    // NAVEGACIÓN / GETTERS
    // =====================================================================

    /**
     * Vuelve al menú principal con transición.
     */
    public void backToMenu() {
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }

    /**
     * Devuelve la capa de overlays (para que otros controladores la usen).
     *
     * @return overlay layer
     */
    public StackPane getOverlayLayer() {
        return overlayLayer;
    }

    /**
     * Devuelve el {@link StatsService} de la run.
     *
     * @return stats service
     */
    public StatsService getStatsService() {
        return statsService;
    }

    // =====================================================================
    // MÚSICA
    // =====================================================================

    /**
     * Inicia música del piso si todavía no ha empezado durante la run.
     */
    public void startFloorMusicIfNeeded() {
        if (!musicStarted) {
            playFloorMusicForCurrentFloor(true);
            musicStarted = true;
        }
    }

    /**
     * Reproduce música del piso actual.
     *
     * @param forceNewPick si {@code true}, elige track nuevo; si {@code false}, reutiliza el asignado al piso.
     */
    private void playFloorMusicForCurrentFloor(boolean forceNewPick) {
        if (!forceNewPick && currentFloorMusicFile != null) {
            AssetsManager.playMusic(currentFloorMusicFile, true);
            return;
        }

        String pick = pickRandomFloorTrack(currentFloor);
        currentFloorMusicFile = pick;
        if (pick != null) {
            AssetsManager.playMusic(pick, true);
        }
    }

    /**
     * Reproduce música de boss (si el track es válido).
     *
     * @param bossTrack nombre de archivo mp3 dentro de /assets/music/
     */
    private void playBossMusic(String bossTrack) {
        if (bossTrack == null || bossTrack.isBlank()) return;
        currentBossMusicFile = bossTrack;
        AssetsManager.playMusic(bossTrack, true);
    }

    /**
     * Tras boss, vuelve a la música del piso (la misma que ya estaba asignada).
     */
    private void resumeFloorMusicAfterBoss() {
        if (currentFloorMusicFile == null) {
            playFloorMusicForCurrentFloor(true);
        } else {
            AssetsManager.playMusic(currentFloorMusicFile, true);
        }
    }

    /**
     * Elige un track aleatorio para el piso, ignorando mp3 que no existan aún.
     *
     * @param floor piso actual
     * @return nombre de archivo mp3
     */
    private String pickRandomFloorTrack(int floor) {
        String[] candidates = switch (floor) {
            case 1 -> new String[] { "basement1.mp3", "basement2.mp3", "basement3.mp3" };
            case 2 -> new String[] { "caves1.mp3", "caves2.mp3" };
            case 3 -> new String[] { "depths1.mp3", "depths2.mp3" };
            case 4 -> new String[] { "womb1.mp3", "womb2.mp3" };
            default -> new String[] { "sheol1.mp3", "sheol2.mp3" };
        };

        List<String> available = new ArrayList<>();
        for (String f : candidates) {
            if (musicExists(f)) available.add(f);
        }

        if (available.isEmpty()) {
            return candidates[0]; // fallback conservador
        }

        return available.get(enemyRng.nextInt(available.size()));
    }

    /**
     * Comprueba si un mp3 existe dentro del classpath.
     *
     * @param fileName nombre de archivo mp3
     * @return true si existe el recurso
     */
    private boolean musicExists(String fileName) {
        return AssetsManager.class.getResource("/assets/music/" + fileName) != null;
    }

    // =====================================================================
    // WAVES / LÓGICA GREED
    // =====================================================================

    /**
     * Arranca una wave concreta:
     * <ul>
     *   <li>Si wave &gt; wavesPerFloor: spawnea boss.</li>
     *   <li>Si wave == wavesPerFloor: spawnea boss (última “oleada”).</li>
     *   <li>En caso normal: timer + enemigos + configuración.</li>
     * </ul>
     *
     * @param wave número de oleada a iniciar
     */
    private void startWave(int wave) {
        if (wave > wavesPerFloor) {
            spawnBoss();
            return;
        }

        waveInProgress = true;
        timerStopped = false;
        buttonSafetyTimer = 3.0;

        if (greedButton != null) greedButton.setActiveState(true);
        removeShopKeeper();

        if (wave == wavesPerFloor) {
            spawnBoss();
            return;
        }

        nextWaveTimer = 10.0 + (wave * 2.0);

        double difficulty = currentFloor * 1.5 + wave * 0.5;
        int enemyCount = (int) ((4 + (difficulty * 1.3)) * Math.max(0.1, AppContext.getRunModifiers().spawnRateMult));

        spawnWaveEnemies(Math.max(1, enemyCount));

        constantSpawnInterval = Math.max(0.25, 2.0 - (difficulty * 0.15));
        timeUntilNextSpawn = 1.0;

        updateHudLabels();
    }

    /**
     * Lógica principal de Greed para oleadas:
     * <ul>
     *   <li>Gestiona timers (incluyendo parada por botón).</li>
     *   <li>Gestiona “oleada rápida” si limpias antes de tiempo.</li>
     *   <li>Gestiona desbordamiento: si se acaba el tiempo, se acumulan oleadas.</li>
     * </ul>
     *
     * @param dt delta time del loop
     */
    private void updateGreedLogic(double dt) {
        if (activeBoss != null) return;

        if (buttonSafetyTimer > 0) buttonSafetyTimer -= dt;

        if (!waveInProgress) {
            if (shopKeeperEntity == null && enemies.isEmpty() && pendingSpawns == 0) {
                spawnShopKeeper();
                if (greedButton != null) greedButton.setActiveState(false);
            }
            return;
        }

        if (!timerStopped) {
            nextWaveTimer -= dt;
        }

        // Reservado (sin spawn constante activo)
        timeUntilNextSpawn -= dt;
        if (timeUntilNextSpawn <= 0.0) {
            // (Reservado / futuro) No hacer nada por ahora.
        }

        boolean timeUp = nextWaveTimer <= 0.0;
        boolean allDead = enemies.isEmpty() && pendingSpawns == 0;

        if (allDead) {
            if (nextWaveTimer > 0 && !timerStopped) {
                spawnRewardCoins(player.getView().getLayoutX(), player.getView().getLayoutY(), 1);
                AppContext.notifications().showNotification(NOTI_OLEADA_RAPIDA_T, NOTI_OLEADA_RAPIDA_D, 2.0);
            }

            nextWaveTimer = 0;

            if (timerStopped) {
                waveInProgress = false;
                if (greedButton != null) greedButton.setActiveState(false);
            } else {
                if (currentWave < wavesPerFloor) {
                    startNextWave();
                } else {
                    waveInProgress = false;
                    if (greedButton != null) greedButton.setActiveState(false);
                }
            }
        } else if (timeUp && !timerStopped) {
            // Desbordamiento: si se acaba el tiempo, añade enemigos de la siguiente oleada (hasta el límite).
            if (currentWave < wavesPerFloor - 1) {
                currentWave++;
                spawnWaveEnemiesForCurrentWave();
                nextWaveTimer = 10.0 + (currentWave * 2.0);
                AppContext.notifications().showNotification(NOTI_DESBORDADO_T, NOTI_DESBORDADO_D, 2.0);
            }
        }
    }

    /**
     * Respawnea enemigos para la oleada actual (cuando se acumulan oleadas).
     */
    private void spawnWaveEnemiesForCurrentWave() {
        double difficulty = currentFloor * 1.5 + currentWave * 0.5;
        int enemyCount = (int) ((4 + (difficulty * 1.3)) * Math.max(0.1, AppContext.getRunModifiers().spawnRateMult));
        spawnWaveEnemies(Math.max(1, enemyCount));
        updateHudLabels();
    }

    /**
     * Avanza a la siguiente oleada y la inicia.
     */
    private void startNextWave() {
        if (gameOverShown || !startGateOpen) return;

        currentWave++;

        if (currentWave > wavesPerFloor) {
            return;
        }

        startWave(currentWave);
        updateHudLabels();
    }

    // =====================================================================
    // SHOPKEEPER
    // =====================================================================

    /**
     * Spawnea el tendero en la sala, con sprite si existe o fallback simple.
     */
    private void spawnShopKeeper() {
        removeShopKeeper();

        if (gameArea == null) return;

        double w = gameArea.getWidth() > 0 ? gameArea.getWidth() : 1280;
        double h = gameArea.getHeight() > 0 ? gameArea.getHeight() : 720;

        double cx = w / 2.0;
        double cy = h / 2.0;

        if (greedButton != null) {
            Node btnView = greedButton.getView();
            cx = btnView.getLayoutX() + 22;
            cy = btnView.getLayoutY() + 22;
        }

        final double finalCx = cx;
        final double finalCy = cy;

        shopKeeperEntity = new GameEntity() {
            Node view;

            {
                Image shopImg = AssetsManager.loadImage("assets/images/shop.png");
                if (shopImg == null) shopImg = AssetsManager.loadImage("/assets/images/shop.png");

                if (shopImg != null) {
                    ImageView iv = new ImageView(shopImg);
                    iv.setFitWidth(60);
                    iv.setFitHeight(60);
                    iv.setPreserveRatio(true);
                    view = iv;
                } else {
                    StackPane sp = new StackPane();
                    Rectangle body = new Rectangle(40, 40, Color.SADDLEBROWN);
                    body.setArcWidth(10);
                    body.setArcHeight(10);
                    body.setStroke(Color.BLACK);
                    body.setStrokeWidth(2);
                    Text label = new Text(TXT_TIENDA);
                    label.setFill(Color.GOLD);
                    label.setStyle("-fx-font-weight: bold;");
                    sp.getChildren().addAll(body, label);
                    view = sp;
                }

                view.setLayoutX(finalCx - 30);
                view.setLayoutY(finalCy - 100);
                view.setEffect(new javafx.scene.effect.DropShadow(5, Color.BLACK));
            }

            @Override
            public void update(double dt) {
                if (player == null || paused || gameOverShown || shopCooldown > 0) return;

                Bounds b1 = view.getBoundsInParent();
                Bounds b2 = player.getBounds();

                double centerX1 = b1.getMinX() + b1.getWidth() / 2;
                double centerY1 = b1.getMinY() + b1.getHeight() / 2;
                double centerX2 = b2.getMinX() + b2.getWidth() / 2;
                double centerY2 = b2.getMinY() + b2.getHeight() / 2;

                double dist = Math.hypot(centerX1 - centerX2, centerY1 - centerY2);

                if (dist < 50.0) {
                    showShopOverlay();
                }
            }

            @Override
            public Node getView() {
                return view;
            }

            @Override
            public Bounds getBounds() {
                return view.getBoundsInParent();
            }
        };

        gameLoop.addEntity(shopKeeperEntity);
        obstacles.add(shopKeeperEntity);
    }

    /**
     * Elimina el tendero del juego (loop + escena + lista de obstáculos).
     */
    private void removeShopKeeper() {
        if (shopKeeperEntity == null) return;

        gameLoop.removeEntity(shopKeeperEntity);
        if (shopKeeperEntity.getView() != null) gameArea.getChildren().remove(shopKeeperEntity.getView());
        obstacles.remove(shopKeeperEntity);

        shopKeeperEntity = null;
    }

    // =====================================================================
    // ENEMIGOS / SPAWN
    // =====================================================================

    /**
     * Spawnea una tanda de enemigos para una oleada (usa {@link SpawnIndicator} con delay).
     *
     * @param count cantidad de enemigos a spawnear
     */
    private void spawnWaveEnemies(int count) {
        if (gameArea == null || player == null) return;

        double w = gameArea.getWidth() > 0 ? gameArea.getWidth() : 1280;
        double h = gameArea.getHeight() > 0 ? gameArea.getHeight() : 720;

        for (int i = 0; i < count; i++) {
            double x = 0, y = 0;
            boolean valid = false;

            for (int tries = 0; tries < 10; tries++) {
                x = enemyRng.nextDouble(20, w - 40);
                y = enemyRng.nextDouble(20, h - 40);
                if (isValidSpawn(x, y, 20, 20)) {
                    valid = true;
                    break;
                }
            }

            if (!valid) {
                x = 50;
                y = 50;
            }

            EnemyType type = pickEnemyTypeForWave();
            double spawnDelay = enemyRng.nextDouble(0.2, 1.5);

            createSpawnIndicator(x, y, type, spawnDelay);
        }
    }

    /**
     * Crea un indicador de spawn que, al terminar, genera el enemigo real.
     *
     * @param x     coordenada X
     * @param y     coordenada Y
     * @param type  tipo de enemigo
     * @param delay delay extra del indicador
     */
    private void createSpawnIndicator(double x, double y, EnemyType type, double delay) {
        pendingSpawns++;

        SpawnIndicator indicator = new SpawnIndicator(x, y, 1.5 + delay, gameArea, (ind) -> {
            // Evita “leaks” de indicadores entre pisos/salas
            spawnIndicators.remove(ind);

            spawnRealEnemy(ind.getX(), ind.getY(), type);
            pendingSpawns--;
        });

        // SpawnIndicator hace toBack() internamente; aquí lo queremos visible sobre el fondo.
        if (indicator.getView() != null) indicator.getView().toFront();

        spawnIndicators.add(indicator);
        gameLoop.addEntity(indicator);
    }

    /**
     * Spawnea el enemigo real aplicando multiplicadores por dificultad/modificadores.
     *
     * @param x    coordenada X
     * @param y    coordenada Y
     * @param type tipo de enemigo
     */
    private void spawnRealEnemy(double x, double y, EnemyType type) {
        double difficulty = currentFloor * 1.0 + (currentWave * 0.1);
        if (AppContext.isHardMode()) difficulty *= 1.5;

        double hpMul = 1.0 + difficulty * 0.2;
        double speedMul = 1.0 + difficulty * 0.05;
        double dmgMul = 1.0 + difficulty * 0.1;

        var mods = AppContext.getRunModifiers();
        hpMul *= Math.max(0.1, mods.enemyHpMult);
        dmgMul *= Math.max(0.1, mods.enemyDmgMult);

        Enemy enemy = new Enemy(
                type, gameArea, this::getPlayerCenter,
                () -> obstacles,
                e -> {
                    gameLoop.removeEntity(e);
                    if (e instanceof Enemy en) {
                        enemies.remove(en);
                        db.incrementEnemyStatAsync(AppContext.getProfileId(), en.getType().name(), DatabaseService.StatType.KILLED);
                        long total = db.getStatTotal(AppContext.getProfileId(), "TOTAL_KILLS");
                        achievements.onEnemyKilled(total + 1);
                        handleEnemyDeathRewards(en);
                    }
                },
                ge -> gameLoop.addEntity(ge),
                k -> sound.play(k),
                hpMul, speedMul, dmgMul
        );

        enemy.setPosition(x, y);
        enemies.add(enemy);
        gameLoop.addEntity(enemy);

        db.incrementEnemyStatAsync(AppContext.getProfileId(), type.name(), DatabaseService.StatType.SEEN);
    }

    // =====================================================================
    // BOSS
    // =====================================================================

    /**
     * Spawnea el boss del piso:
     * <ul>
     *   <li>Limpia enemigos/indicadores.</li>
     *   <li>Elimina botón/tendero.</li>
     *   <li>Configura música/GUI de boss.</li>
     * </ul>
     */
    private void spawnBoss() {
        waveInProgress = true;
        nextWaveTimer = 99999;

        removeGreedButtonIfPresent();
        removeShopKeeper();

        // Eliminar enemigos e indicadores pendientes
        for (Enemy e : new ArrayList<>(enemies)) gameLoop.removeEntity(e);
        enemies.clear();
        pendingSpawns = 0;

        double hpMult = Math.max(0.1, AppContext.getRunModifiers().enemyHpMult);
        double bossHp = (400 + (currentFloor * 250)) * hpMult;

        double bx = gameArea.getWidth() / 2 - 40;
        double by = gameArea.getHeight() / 2 - 100;
        String bossId = "BOSS_FLOOR_" + currentFloor;

        java.util.function.Consumer<GameEntity> projectileRemover = (ent) -> gameLoop.removeEntity(ent);
        String bossTrack = null;

        if (currentFloor == 5) {
            bossTrack = "harvester.mp3";
            bossHp *= 2.0;

            bossNameLabel.setText("EL COSECHADOR (JEFE FINAL)");
            bossNameLabel.setStyle("-fx-text-fill: #ff0000; -fx-font-size: 24px; -fx-font-weight: bold; "
                    + "-fx-effect: dropshadow(gaussian, black, 4, 1, 0, 0);");

            activeBoss = new com.layla.entities.FinalBoss(
                    bx, by, bossHp,
                    gameArea,
                    this::getPlayerCenter,
                    (deadBoss) -> handleBossDeath(bossId),
                    (proj) -> gameLoop.addEntity(proj),
                    projectileRemover,
                    bossId
            );
        } else {
            int bossType = pickBossTypeForFloor(currentFloor);

            switch (bossType) {
                case 0 -> {
                    bossNameLabel.setText("EL EXPANSOR");
                    bossNameLabel.setStyle("-fx-text-fill: #ffaaaa; -fx-font-weight: bold; -fx-font-size: 18px;");
                    activeBoss = new Boss(
                            bx, by, bossHp,
                            gameArea,
                            this::getPlayerCenter,
                            (deadBoss) -> handleBossDeath(bossId),
                            (proj) -> gameLoop.addEntity(proj),
                            projectileRemover,
                            bossId
                    );
                    bossTrack = "spreader.mp3";
                }
                case 1 -> {
                    bossNameLabel.setText("EL CENTINELA");
                    bossNameLabel.setStyle("-fx-text-fill: #aaddff; -fx-font-weight: bold; -fx-font-size: 18px;");
                    activeBoss = new BossSniper(
                            bx, by, bossHp,
                            gameArea,
                            this::getPlayerCenter,
                            (deadBoss) -> handleBossDeath(bossId),
                            (proj) -> gameLoop.addEntity(proj),
                            projectileRemover,
                            bossId
                    );
                    bossTrack = "sentry.mp3";
                }
                case 2 -> {
                    bossNameLabel.setText("EL BRUTO");
                    bossNameLabel.setStyle("-fx-text-fill: #aaffaa; -fx-font-weight: bold; -fx-font-size: 18px;");
                    activeBoss = new BossCharger(
                            bx, by, bossHp,
                            gameArea,
                            this::getPlayerCenter,
                            (deadBoss) -> handleBossDeath(bossId),
                            (proj) -> gameLoop.addEntity(proj),
                            projectileRemover,
                            bossId
                    );
                    bossTrack = "charger.mp3";
                }
                default -> {
                    bossNameLabel.setText("LA COLMENA");
                    bossNameLabel.setStyle("-fx-text-fill: #ffbbff; -fx-font-weight: bold; -fx-font-size: 18px;");
                    activeBoss = new BossHive(
                            bx, by, bossHp,
                            gameArea,
                            this::getPlayerCenter,
                            (deadBoss) -> handleBossDeath(bossId),
                            (proj) -> gameLoop.addEntity(proj),
                            projectileRemover,
                            bossId
                    );
                    bossTrack = "hive.mp3";
                }
            }
        }

        if (bossTrack != null) playBossMusic(bossTrack);

        gameLoop.addEntity(activeBoss);
        db.incrementEnemyStatAsync(AppContext.getProfileId(), bossId, DatabaseService.StatType.SEEN);

        if (bossHealthBox != null) {
            bossHealthBox.setVisible(true);
            bossHealthBox.setManaged(true);
        }
    }

    /**
     * Lógica al morir el boss:
     * <ul>
     *   <li>SFX + persistencia + logros.</li>
     *   <li>Recompensa de score y monedas.</li>
     *   <li>Spawnea salida al siguiente piso + tendero + pedestal de recompensa.</li>
     *   <li>Restaura música del piso.</li>
     * </ul>
     *
     * @param bossId id persistente del boss (por piso)
     */
    private void handleBossDeath(String bossId) {
        sound.play("boss_death");

        if (activeBoss != null) {
            gameLoop.removeEntity(activeBoss);
            activeBoss = null;

            db.incrementEnemyStatAsync(AppContext.getProfileId(), bossId, DatabaseService.StatType.KILLED);
            achievements.onBossKilled();
        }

        if (bossHealthBox != null) bossHealthBox.setVisible(false);

        // Volver a música del piso
        currentBossMusicFile = null;
        resumeFloorMusicAfterBoss();
        musicStarted = true;

        int bossScore = (int) (1000 * currentFloor * comboMultiplier);
        this.score += bossScore;

        spawnRewardCoins(gameArea.getWidth() / 2, gameArea.getHeight() / 2, 50);
        updateHudLabels();

        waveInProgress = false;

        // Limpieza extra: si quedase algo vivo, forzarlo
        for (Enemy e : new ArrayList<>(enemies)) e.applyDamage(99999);

        spawnNextFloorButton();
        spawnShopKeeper();

        spawnRewardPedestal(ItemPoolType.BOSS);
    }

    // =====================================================================
    // NEXT FLOOR
    // =====================================================================

    /**
     * Reutiliza el {@link GreedButton} como “salida” para pasar al siguiente piso.
     */
    private void spawnNextFloorButton() {
        removeGreedButtonIfPresent();

        double w = gameArea.getWidth() > 0 ? gameArea.getWidth() : 1280;
        double h = gameArea.getHeight() > 0 ? gameArea.getHeight() : 720;

        double cx = w / 2.0;
        double cy = h / 2.0;

        clearAreaAround(cx, cy, 100);

        greedButton = new GreedButton(cx, cy, gameArea, (btn) -> loadNextFloor());
        greedButton.setAsExit();

        gameLoop.addEntity(greedButton);
        checkAndPushInteractive(greedButton);
    }

    /**
     * Carga el siguiente piso:
     * <ul>
     *   <li>Limpia el nivel.</li>
     *   <li>Resetea estado/timers.</li>
     *   <li>Actualiza fondo + música.</li>
     *   <li>Respawnea layout + botón + tendero + NPC.</li>
     * </ul>
     */
    private void loadNextFloor() {
        currentFloor++;
        if (currentFloor > MAX_FLOORS) {
            showVictoryOverlay();
            return;
        }

        currentWave = 0;
        clearLevel();

        currentHeartPrice = 2;

        waveInProgress = false;
        timerStopped = false;

        currentRerollPrice = SHOP_REROLL_BASE_PRICE;
        currentShopOffers = generateShopOffers(SHOP_OFFER_COUNT);

        // Audiovisual
        changeFloorVisuals();
        currentBossMusicFile = null;
        currentFloorMusicFile = null;
        playFloorMusicForCurrentFloor(true);
        musicStarted = true;

        ensureBoundaryWalls();
        spawnRoomLayout();
        spawnGreedButton();
        spawnShopKeeper();
        spawnChocoCat();

        updateHudLabels();

        if (player != null) {
            player.setPosition(gameArea.getWidth() / 2 - 10, gameArea.getHeight() / 2 + 80);
        }

        enemyPresenceTimer = 0.0;

        AppContext.notifications().showNotification(NOTI_PISO_T + currentFloor, NOTI_PISO_D, 3.0);
    }

    // =====================================================================
    // CHOCOCAT NPC
    // =====================================================================

    /**
     * Spawnea el NPC {@link ChocoCat}. Se mantiene al frente para no quedar oculto.
     */
    private void spawnChocoCat() {
        if (gameArea == null) return;

        if (chocoEntity != null) {
            gameLoop.removeEntity(chocoEntity);
            if (chocoEntity.getView() != null) gameArea.getChildren().remove(chocoEntity.getView());
            chocoEntity = null;
        }

        double x = gameArea.getWidth() / 2 - 90;
        double y = gameArea.getHeight() / 2 + 130;

        chocoEntity = new ChocoCat(x, y, gameArea);
        gameLoop.addEntity(chocoEntity);

        if (chocoEntity.getView() != null) {
            chocoEntity.getView().setMouseTransparent(true);
            chocoEntity.getView().toFront();
        }
        if (player != null && player.getView() != null) player.getView().toFront();
    }

    /**
     * Alias retrocompatible (código antiguo llama a spawnChoco()).
     */
    private void spawnChoco() {
        spawnChocoCat();
    }

    // =====================================================================
    // LIMPIEZA DE NIVEL
    // =====================================================================

    /**
     * Limpia el estado del nivel actual:
     * <ul>
     *   <li>Enemigos</li>
     *   <li>Indicadores de spawn</li>
     *   <li>Boss</li>
     *   <li>NPCs (ChocoCat)</li>
     *   <li>Obstáculos (excepto paredes invisibles)</li>
     *   <li>Nodos sueltos del Pane (manteniendo BG, player y walls)</li>
     * </ul>
     */
    private void clearLevel() {
        // Enemigos
        for (Enemy e : new ArrayList<>(enemies)) {
            gameLoop.removeEntity(e);
            if (e.getView() != null) gameArea.getChildren().remove(e.getView());
        }
        enemies.clear();

        // Indicadores
        for (SpawnIndicator ind : new ArrayList<>(spawnIndicators)) {
            gameLoop.removeEntity(ind);
            if (ind.getView() != null) gameArea.getChildren().remove(ind.getView());
        }
        spawnIndicators.clear();
        pendingSpawns = 0;

        // Boss
        if (activeBoss != null) {
            gameLoop.removeEntity(activeBoss);
            if (activeBoss.getView() != null) gameArea.getChildren().remove(activeBoss.getView());
            activeBoss = null;
        }

        // ChocoCat
        if (chocoEntity != null) {
            gameLoop.removeEntity(chocoEntity);
            if (chocoEntity.getView() != null) gameArea.getChildren().remove(chocoEntity.getView());
            chocoEntity = null;
        }

        // Obstáculos (mantener paredes invisibles)
        for (GameEntity obs : new ArrayList<>(obstacles)) {
            if (obs == null) continue;
            if (obs instanceof InvisibleWall) continue;

            if (obs instanceof Rock rock) {
                rock.destroy();
            }

            gameLoop.removeEntity(obs);
            if (obs.getView() != null) gameArea.getChildren().remove(obs.getView());
        }

        obstacles.removeIf(o -> !(o instanceof InvisibleWall));

        ensureBoundaryWalls();

        // Nodos sueltos del Pane (mantener BG, player y walls)
        if (gameArea != null) {
            gameArea.getChildren().removeIf(n ->
                    n != backgroundView
                            && (player == null || n != player.getView())
                            && !isBoundaryWallNode(n)
            );
        }
    }

    // =====================================================================
    // ENEMY TYPE PICKER
    // =====================================================================

    /**
     * Selecciona tipo de enemigo según piso y RNG.
     *
     * @return tipo de enemigo
     */
    private EnemyType pickEnemyTypeForWave() {
        int roll = enemyRng.nextInt(100);

        if (currentFloor == 1) {
            if (roll < 70) return EnemyType.SHOOTER;
            if (roll < 90) return EnemyType.MELEE;
            return EnemyType.TURRET;
        }

        if (roll < 25) return EnemyType.SHOOTER;
        if (roll < 50) return EnemyType.MELEE;
        if (roll < 75) return EnemyType.TANK;
        if (roll < 90) return EnemyType.TURRET;
        return EnemyType.KAMIKAZE;
    }

    // =====================================================================
    // PLAYER SPAWN
    // =====================================================================

    /**
     * Spawnea al jugador si la “puerta de inicio” está abierta y aún no existe.
     */
    private void maybeSpawnPlayer() {
        if (!startGateOpen) return;
        if (player != null || input == null || gameLoop == null) return;

        double w = gameArea.getWidth() > 0 ? gameArea.getWidth() : 1280;
        double h = gameArea.getHeight() > 0 ? gameArea.getHeight() : 720;

        player = new Player(input, gameArea, statsService, sound::play);

        // Impide que el player entre en el borde “muro” del background
        player.setWorldInset(
                BG_WALL_THICKNESS_LEFT,
                BG_WALL_THICKNESS_RIGHT,
                BG_WALL_THICKNESS_TOP,
                BG_WALL_THICKNESS_BOTTOM
        );

        applyBalanceToRuntimePlayer();
        player.setHealth(statsService.getMaxHealth());

        player.setPosition(w / 2 - 10, h / 2 + 100);
        lastPlayerHealth = player.getHealth();

        shootingService = new ShootingService(statsService);
        shootingService.setTargetSupplier(this::getHomingTargets);

        shootingArmed = false;
        shootingArmTimer = 0.6;

        // HUD
        hud = new HudView(statsService, player);
        hud.setTranslateX(12);
        hud.setTranslateY(12);
        overlayLayer.getChildren().add(hud);
        StackPane.setAlignment(hud, Pos.TOP_LEFT);

        itemHud = new ItemHudView(statsService);
        itemHud.refresh();
        overlayLayer.getChildren().add(itemHud);
        StackPane.setAlignment(itemHud, Pos.TOP_RIGHT);
        StackPane.setMargin(itemHud, new Insets(12, 8, 12, 8));

        itemHud.toFront();
        hud.toFront();

        gameLoop.addEntity(player);
        if (!gameLoop.isRunning()) gameLoop.start();
    }

    /**
     * Devuelve objetivos para homing: enemigos + boss si está vivo.
     *
     * @return lista de entidades objetivo
     */
    private List<GameEntity> getHomingTargets() {
        List<GameEntity> targets = new ArrayList<>(enemies);
        if (activeBoss != null && !activeBoss.isDead()) targets.add(activeBoss);
        return targets;
    }

    // =====================================================================
    // TICKER (LOOP LÓGICO)
    // =====================================================================

    /**
     * Añade una entidad “ticker” para ejecutar lógica global (timers, SFX, oleadas, colisiones, etc.).
     * Se crea una sola vez por run.
     */
    private void addTickerIfNeeded() {
        if (!startGateOpen || gameLoop == null) return;
        if (ticker != null) return;

        ticker = new GameEntity() {
            private final Group view = new Group();

            @Override
            public void update(double dt) {
                // Timers SFX
                if (playerHurtSfxTimer > 0.0) playerHurtSfxTimer -= dt;
                if (enemyPresenceTimer > 0.0) enemyPresenceTimer -= dt;

                // Armado del disparo
                if (!shootingArmed) {
                    shootingArmTimer -= dt;
                    if (shootingArmTimer <= 0.0) shootingArmed = true;
                }

                // Cooldowns
                if (shopCooldown > 0) shopCooldown -= dt;

                if (!paused && !gameOverShown) {
                    updateGreedLogic(dt);

                    if (greedButton != null) greedButton.update(dt);
                    if (shopKeeperEntity != null) shopKeeperEntity.update(dt);

                    // Enemy presence SFX (cada X segundos si hay enemigos vivos)
                    if (activeBoss == null && !enemies.isEmpty()) {
                        if (enemyPresenceTimer <= 0.0) {
                            sound.play("enemy_presence");
                            enemyPresenceTimer = ENEMY_PRESENCE_INTERVAL;
                        }
                    } else {
                        enemyPresenceTimer = 0.0;
                    }

                    // Penalización de score por segundo
                    scoreTimer += dt;
                    if (scoreTimer >= 1.0) {
                        score = Math.max(0, score - SCORE_PENALTY_PER_SECOND);
                        scoreTimer -= 1.0;
                        updateHudLabels();
                    }

                    // Combo expira
                    if (comboTimer > 0.0) {
                        comboTimer -= dt;
                        if (comboTimer <= 0.0) {
                            comboCount = 0;
                            comboMultiplier = 1.0;
                            updateHudLabels();
                        }
                    }

                    // Boss update + contacto
                    if (activeBoss != null) {
                        activeBoss.update(dt);
                        if (player != null && !player.isDead() && activeBoss.getBounds().intersects(player.getBounds())) {
                            player.setLastHitSource("BOSS_FLOOR_" + currentFloor);
                            player.takeDamage(1.0);
                        }
                    }
                }

                // Shooting service (si existe)
                if (shootingService != null && input != null) {
                    shootingService.update(dt, input.getMoveVector());
                }

                // Disparo automático (si armado)
                if (shootingArmed && !paused && player != null && !player.isDead()) {
                    tryShootNow();
                }

                // Penalización por daño recibido
                if (player != null) {
                    double currentHp = player.getHealth();
                    if (lastPlayerHealth > 0 && currentHp < lastPlayerHealth) {
                        score = Math.max(0, score - SCORE_PENALTY_ON_DAMAGE);
                        comboCount = 0;
                        comboMultiplier = 1.0;
                        comboTimer = 0.0;
                        updateHudLabels();
                    }
                    lastPlayerHealth = currentHp;
                }

                // Refresh HUD
                hudRefreshTimer -= dt;
                if (hud != null && hudRefreshTimer <= 0.0) {
                    hud.refresh();
                    updateHudLabels();
                    hudRefreshTimer = 0.1;
                }

                // Colisiones y separación
                resolveObstacleCollisions();
                applyEnemySeparation(dt);

                // Game over
                if (!gameOverShown && player != null && player.isDead()) {
                    showGameOverOverlay();
                }
            }

            @Override
            public Node getView() {
                return view;
            }
        };

        gameLoop.addEntity(ticker);
    }

    // =====================================================================
    // REWARDS / COMBO
    // =====================================================================

    /**
     * Gestiona recompensas al morir un enemigo:
     * <ul>
     *   <li>Posible drop de monedas.</li>
     *   <li>Combo/multiplicador.</li>
     *   <li>Score base según perfil.</li>
     * </ul>
     *
     * @param en enemigo muerto
     */
    private void handleEnemyDeathRewards(Enemy en) {
        if (enemyRng.nextDouble() < 0.3) {
            int amount = enemyRng.nextInt(1, 4);
            if (timerStopped) amount = Math.max(1, amount / 2);
            spawnRewardCoins(en.getCenterX(), en.getCenterY(), amount);
        }

        comboCount++;
        comboTimer = COMBO_MAX_TIME;
        comboMultiplier = Math.min(5.0, 1.0 + (comboCount * 0.1));

        EnemyProfile profile = AppContext.balance().profile(en.getType());
        int basePoints = (profile != null) ? profile.score : 10;

        this.score += (int) (basePoints * comboMultiplier);
        updateHudLabels();
    }

    // =====================================================================
    // ROOM LAYOUT / ROCAS
    // =====================================================================

    /**
     * Spawnea un patrón de rocas en la sala, manteniendo paredes invisibles.
     */
    private void spawnRoomLayout() {
        // Eliminar obstáculos existentes (mantener walls)
        for (GameEntity r : new ArrayList<>(obstacles)) {
            if (r instanceof InvisibleWall) continue;

            if (r instanceof Rock rock) {
                rock.destroy();
            } else if (r.getView() != null) {
                gameArea.getChildren().remove(r.getView());
            }

            gameLoop.removeEntity(r);
        }

        obstacles.removeIf(o -> !(o instanceof InvisibleWall));
        ensureBoundaryWalls();

        int pattern = enemyRng.nextInt(4);
        double w = gameArea.getWidth() > 0 ? gameArea.getWidth() : 1280;
        double h = gameArea.getHeight() > 0 ? gameArea.getHeight() : 720;
        double cx = w / 2;
        double cy = h / 2;

        if (pattern == 1) {
            spawnRockRow(cx - 80, cx + 80, cy, true);
            spawnRockRow(cy - 80, cy + 80, cx, false);
        } else if (pattern == 2) {
            spawnRock(cx - 150, cy - 100);
            spawnRock(cx + 150, cy - 100);
            spawnRock(cx - 150, cy + 100);
            spawnRock(cx + 150, cy + 100);
        } else if (pattern == 3) {
            for (int i = 0; i < 6; i++) {
                double rx = enemyRng.nextDouble(100, w - 100);
                double ry = enemyRng.nextDouble(100, h - 100);
                if (Math.abs(rx - cx) > 100 || Math.abs(ry - cy) > 100) spawnRock(rx, ry);
            }
        }

        // Recolocar interactivos si colisionan
        List<GameEntity> interactives = new ArrayList<>();
        if (shopKeeperEntity != null) interactives.add(shopKeeperEntity);
        if (greedButton != null) interactives.add(greedButton);

        for (GameEntity ent : interactives) {
            checkAndPushInteractive(ent);
        }
    }

    /**
     * Spawnea una roca en coordenadas, usando skin según piso.
     *
     * @param x coordenada X
     * @param y coordenada Y
     */
    private void spawnRock(double x, double y) {
        int skinCol = getRockSkinColumn();
        Rock r = new Rock(x, y, gameArea, skinCol);
        obstacles.add(r);
        gameLoop.addEntity(r);
    }

    /**
     * Spawnea una fila (o columna) de rocas.
     *
     * @param start      inicio de coordenada variable
     * @param end        final de coordenada variable
     * @param fixed      coordenada fija
     * @param horizontal true para fila horizontal; false para columna
     */
    private void spawnRockRow(double start, double end, double fixed, boolean horizontal) {
        double step = Rock.SIZE;
        for (double p = start; p <= end; p += step) {
            if (horizontal) spawnRock(p, fixed);
            else spawnRock(fixed, p);
        }
    }

    /**
     * Determina la columna del spritesheet de roca según piso.
     *
     * @return índice de columna
     */
    private int getRockSkinColumn() {
        return switch (currentFloor) {
            case 1 -> 0; // Sótano
            case 2 -> 5; // Cuevas
            case 3 -> 2; // Profundidades
            case 4 -> 3; // Útero
            default -> 2; // Seol + fallback
        };
    }

    // =====================================================================
    // RESET DE ESCENA / ANCLAJES
    // =====================================================================

    /**
     * Restaura gameArea a su estado “normal” (anchors, escala, fondo y paredes).
     */
    private void resetPlainGameArea() {
        if (gameArea == null || root == null) return;

        gameArea.setScaleX(1.0);
        gameArea.setScaleY(1.0);
        gameArea.setManaged(true);
        gameArea.setClip(null);

        AnchorPane anchor = null;
        for (var n : root.getChildren()) {
            if (n instanceof AnchorPane ap) {
                anchor = ap;
                break;
            }
        }

        if (anchor != null) {
            if (!anchor.getChildren().contains(gameArea)) anchor.getChildren().add(gameArea);

            AnchorPane.setTopAnchor(gameArea, 72.0);
            AnchorPane.setBottomAnchor(gameArea, 0.0);
            AnchorPane.setLeftAnchor(gameArea, 0.0);
            AnchorPane.setRightAnchor(gameArea, 0.0);

            if (hudBar != null) hudBar.toFront();
        }

        changeFloorVisuals();
        ensureBoundaryWalls();
    }

    // =====================================================================
    // SHOP OVERLAY
    // =====================================================================

    /**
     * Muestra la tienda y pausa el {@link GameLoop}.
     * Configura callbacks de compra, reroll y cierre.
     */
    private void showShopOverlay() {
        paused = true;
        if (gameLoop != null) gameLoop.stop();

        final Node[] overlayRef = new Node[1];
        overlayRef[0] = OverlayRouter.showOverlay(overlayLayer, "ui/shop_overlay.fxml", 0.90, controller -> {
            if (!(controller instanceof ShopOverlayController soc)) return;

            soc.setStatsService(statsService);
            soc.setPlayer(player);
            soc.setCoins(coins);
            soc.setRerollBasePrice(currentRerollPrice);
            soc.setOffers(currentShopOffers);

            // Corazón
            soc.setHeartPrice(currentHeartPrice);
            soc.setOnHeartBuyRequest((c) -> {
                if (coins >= currentHeartPrice && player.getHealth() < player.getMaxHealth()) {
                    coins -= currentHeartPrice;
                    player.addHealth(2.0);
                    currentHeartPrice += 2;

                    sound.play("buy");

                    updateHudLabels();
                    if (hud != null) hud.refresh();

                    c.setCoins(coins);
                    c.setHeartPrice(currentHeartPrice);
                }
            });

            soc.setOnCoinsChanged(newCoins -> {
                coins = Math.max(0, newCoins);
                updateHudLabels();
            });

            soc.setOnItemsChanged(() -> {
                onShopItemPurchaseConfirmed();

                score = Math.max(0, score - SCORE_PENALTY_ON_BUY);
                sound.play("buy");

                if (hud != null) hud.refresh();
                if (itemHud != null) itemHud.refresh();
                updateHudLabels();
            });

            soc.setOnRerollPriceChanged(newPrice -> currentRerollPrice = newPrice);

            soc.setOnRerollRequested(c -> {
                currentShopOffers = generateShopOffers(SHOP_OFFER_COUNT);
                c.setOffers(currentShopOffers);
                sound.play("buy");
            });

            soc.setOnClose(() -> {
                coins = soc.getCoins();
                updateHudLabels();

                OverlayRouter.closeOverlay(overlayLayer, overlayRef[0]);
                shopOverlay = null;

                Platform.runLater(() -> {
                    if (gameLoop != null && !gameOverShown) gameLoop.start();
                    paused = false;
                    shopCooldown = 5.0;
                });
            });

            soc.onShow();
        });

        shopOverlay = overlayRef[0];

        // Traer HUD al frente
        if (hud != null) hud.toFront();
        if (itemHud != null) itemHud.toFront();
    }

    /**
     * Se llama una vez por compra de ítem confirmada en tienda (monedas descontadas + ítem concedido).
     * Dispara chequeo de logros.
     */
    private void onShopItemPurchaseConfirmed() {
        achievements.onItemBought();
        System.out.println("[Achievements] Shop purchase confirmed for profile " + AppContext.getProfileId()
                + "; onItemBought fired.");
    }

    // =====================================================================
    // REWARDS: COINS / PEDESTAL / ITEM PICKUP
    // =====================================================================

    /**
     * Spawnea monedas como recompensa.
     *
     * @param x      coordenada X
     * @param y      coordenada Y
     * @param amount cantidad de monedas/valor a dropear
     */
    private void spawnRewardCoins(double x, double y, int amount) {
        Coin coin = new Coin(x, y, amount, gameArea, statsService, player, c -> {
            coins += c.getValue();
            updateHudLabels();
            sound.play("coin");
            gameLoop.removeEntity(c);
        });
        gameLoop.addEntity(coin);
    }

    /**
     * Spawnea un pedestal de item de un pool.
     *
     * <p>
     * Si el pool solicitado está vacío, hace fallback a {@link ItemPoolType#SHOP}.
     * </p>
     *
     * @param poolType tipo de pool del ítem
     */
    private void spawnRewardPedestal(ItemPoolType poolType) {
        if (gameLoop == null || gameArea == null) return;

        List<ItemDefinition> availableItems = ItemRegistry.getUnlockedByPool(poolType, achievements);

        // Fallback a SHOP si el pool solicitado estuviera vacío
        if (availableItems.isEmpty()) {
            availableItems = ItemRegistry.getUnlockedByPool(ItemPoolType.SHOP, achievements);
        }
        if (availableItems.isEmpty()) return;

        ItemDefinition def = availableItems.get(rewardItemCursor % availableItems.size());
        final ItemId itemId = def.getId();
        rewardItemCursor++;

        double w = gameArea.getWidth() > 0 ? gameArea.getWidth() : 1280;
        double h = gameArea.getHeight() > 0 ? gameArea.getHeight() : 720;

        double cx = w / 2;
        double cy = h / 2;

        clearAreaAround(cx, cy + 80, 60);

        ItemPedestal pedestal = new ItemPedestal(
                itemId, gameArea, statsService,
                e -> {
                    gameLoop.removeEntity(e);
                    obstacles.remove(e);
                    if (e.getView() != null) gameArea.getChildren().remove(e.getView());
                    if (itemHud != null) itemHud.refresh();
                    showItemPickupOverlay(itemId);
                },
                k -> sound.play(k)
        );

        pedestal.setPosition(cx, cy + 80);

        // Asegurar visibilidad
        if (pedestal.getView() != null && !gameArea.getChildren().contains(pedestal.getView())) {
            gameArea.getChildren().add(pedestal.getView());
        }

        gameLoop.addEntity(pedestal);
        checkAndPushInteractive(pedestal);
        obstacles.add(pedestal);
    }

    /**
     * Muestra overlay de pickup del item y, al cerrar, vuelve a abrir la tienda.
     *
     * @param itemId id del ítem recogido
     */
    private void showItemPickupOverlay(ItemId itemId) {
        paused = true;
        if (gameLoop != null) gameLoop.stop();

        final Node[] overlayRef = new Node[1];
        overlayRef[0] = OverlayRouter.showOverlay(overlayLayer, "ui/item_pickup_overlay.fxml", 0.90, controller -> {
            if (!(controller instanceof ItemPickupOverlayController ipc)) return;

            ItemDefinition def = ItemRegistry.getDefinition(itemId);
            String name = (def != null ? def.getName() : itemId.name());
            String desc = (def != null ? def.getDescription() : "");

            Image icon = null;
            String path = ItemRegistry.getIconPath(itemId);
            if (path != null) icon = AssetsManager.loadImage(path.startsWith("/") ? path.substring(1) : path);

            ipc.setItem(name, desc, icon);
            ipc.setOnClose(() -> {
                OverlayRouter.closeOverlay(overlayLayer, overlayRef[0]);
                Platform.runLater(this::showShopOverlay);
            });
        });
    }

    /**
     * Genera ofertas de tienda (pool SHOP) con precio basado en oleada.
     *
     * @param count número de ofertas
     * @return lista de ofertas
     */
    private List<ShopOffer> generateShopOffers(int count) {
        List<ShopOffer> offers = new ArrayList<>();

        List<ItemDefinition> pool = ItemRegistry.getUnlockedByPool(ItemPoolType.SHOP, achievements);
        if (pool.isEmpty()) return offers;

        for (int i = 0; i < count; i++) {
            ItemDefinition def = pool.get(enemyRng.nextInt(pool.size()));
            int price = Math.max(5, 10 + Math.max(0, currentWave - 1) * 2 + enemyRng.nextInt(0, 6));
            offers.add(new ShopOffer(def.getId(), price));
        }

        return offers;
    }

    // =====================================================================
    // PAREDES INVISIBLES (BOUNDARY WALLS)
    // =====================================================================

    /**
     * Crea/asegura 4 rectángulos invisibles alrededor del área jugable para bloquear player/enemies
     * y evitar spawns en el borde decorativo del fondo.
     */
    private void ensureBoundaryWalls() {
        if (wallTop == null || wallBottom == null || wallLeft == null || wallRight == null) {
            wallTop = new InvisibleWall();
            wallBottom = new InvisibleWall();
            wallLeft = new InvisibleWall();
            wallRight = new InvisibleWall();
            boundaryWallsAdded = true;
        }

        attachBoundaryWall(wallTop);
        attachBoundaryWall(wallBottom);
        attachBoundaryWall(wallLeft);
        attachBoundaryWall(wallRight);

        // Añadir al loop solo una vez (evitar duplicados)
        if (gameLoop != null && !boundaryWallsInLoop) {
            gameLoop.addEntity(wallTop);
            gameLoop.addEntity(wallBottom);
            gameLoop.addEntity(wallLeft);
            gameLoop.addEntity(wallRight);
            boundaryWallsInLoop = true;
        }

        updateBoundaryWalls();
        Platform.runLater(this::updateBoundaryWalls);
    }

    /**
     * Asegura que la pared esté en el scene graph y en la lista de obstáculos.
     *
     * @param w pared invisible
     */
    private void attachBoundaryWall(InvisibleWall w) {
        if (w == null) return;

        Node v = w.getView();
        if (v != null && gameArea != null && !gameArea.getChildren().contains(v)) {
            gameArea.getChildren().add(v);
        }

        if (!obstacles.contains(w)) {
            obstacles.add(w);
        }
    }

    /**
     * Método legacy (compatibilidad interna).
     *
     * @param w pared invisible
     */
    @SuppressWarnings("unused")
    private void addBoundaryWall(InvisibleWall w) {
        gameArea.getChildren().add(w.getView());
        obstacles.add(w);
        if (gameLoop != null) gameLoop.addEntity(w);
    }

    /**
     * Recalcula geometría de las 4 paredes según tamaño actual del gameArea.
     */
    private void updateBoundaryWalls() {
        if (wallTop == null || wallBottom == null || wallLeft == null || wallRight == null) return;

        double w = gameArea.getWidth();
        double h = gameArea.getHeight();
        if (w <= 1 || h <= 1) return;

        double tL = Math.max(1.0, BG_WALL_THICKNESS_LEFT);
        double tR = Math.max(1.0, BG_WALL_THICKNESS_RIGHT);
        double tT = Math.max(1.0, BG_WALL_THICKNESS_TOP);
        double tB = Math.max(1.0, BG_WALL_THICKNESS_BOTTOM);

        wallTop.setRect(0, 0, w, tT);
        wallBottom.setRect(0, h - tB, w, tB);
        wallLeft.setRect(0, 0, tL, h);
        wallRight.setRect(w - tR, 0, tR, h);
    }

    /**
     * Indica si un {@link Node} corresponde a alguna de las paredes invisibles.
     *
     * @param n node a comprobar
     * @return true si es una pared invisible
     */
    private boolean isBoundaryWallNode(Node n) {
        if (!boundaryWallsAdded || n == null) return false;
        return n == wallTop.getView()
                || n == wallBottom.getView()
                || n == wallLeft.getView()
                || n == wallRight.getView();
    }

    /**
     * Entidad pared invisible (rectángulo transparente con bounds para colisión).
     */
    private static final class InvisibleWall implements GameEntity {
        private final Rectangle r = new Rectangle(1, 1);

        private InvisibleWall() {
            r.setManaged(false);
            r.setMouseTransparent(true);
            r.setFill(Color.TRANSPARENT);
            r.setStroke(Color.TRANSPARENT);
            r.setOpacity(0.0);
        }

        /**
         * Define el rectángulo (en coords de layout) para colisión.
         */
        void setRect(double x, double y, double w, double h) {
            r.setLayoutX(x);
            r.setLayoutY(y);
            r.setWidth(Math.max(0, w));
            r.setHeight(Math.max(0, h));
        }

        @Override
        public void update(double dt) {
            // Estática
        }

        @Override
        public Node getView() {
            return r;
        }

        @Override
        public Bounds getBounds() {
            return new BoundingBox(r.getLayoutX(), r.getLayoutY(), r.getWidth(), r.getHeight());
        }
    }

    // =====================================================================
    // VALIDACIÓN DE SPAWN
    // =====================================================================

    /**
     * Valida si un enemigo puede spawnear en un rectángulo:
     * <ul>
     *   <li>Lejos del jugador</li>
     *   <li>No intersecta obstáculos/paredes</li>
     *   <li>No sale fuera del Pane</li>
     * </ul>
     *
     * @param ex x
     * @param ey y
     * @param ew ancho
     * @param eh alto
     * @return true si es un spawn válido
     */
    private boolean isValidSpawn(double ex, double ey, double ew, double eh) {
        if (player != null) {
            double pcx = player.getCenterX();
            double pcy = player.getCenterY();
            double ecx = ex + ew * 0.5;
            double ecy = ey + eh * 0.5;
            if (Math.hypot(ecx - pcx, ecy - pcy) < 80) return false;
        }

        Bounds spawn = new BoundingBox(ex, ey, ew, eh);

        for (GameEntity obs : obstacles) {
            if (obs == null) continue;
            Bounds b = obs.getBounds();
            if (b != null && b.intersects(spawn)) return false;
        }

        double w = gameArea.getWidth();
        double h = gameArea.getHeight();
        if (w > 0 && h > 0) {
            if (ex < 0 || ey < 0 || ex + ew > w || ey + eh > h) return false;
        }

        return true;
    }

    // =====================================================================
    // DISPARO
    // =====================================================================

    /**
     * Intenta disparar según el input actual (aim cardinal con flechas).
     */
    private void tryShootNow() {
        if (player == null || shootingService == null || input == null) return;

        double[] aim = input.getAimArrowCardinal();
        if (Math.abs(aim[0]) > 0.01 || Math.abs(aim[1]) > 0.01) {
            shootingService.setAim(aim[0], aim[1]);

            Bounds bounds = player.getBounds();
            double originX = bounds.getCenterX();
            double originY = bounds.getCenterY();

            shootingService.tryShoot(gameArea, gameLoop, originX, originY, player, proj -> sound.play("shot"));
        }
    }

    /**
     * Devuelve el centro del player como array [x,y] (para aim/IA).
     *
     * @return array {x,y}
     */
    private double[] getPlayerCenter() {
        if (player == null) return new double[] { 0, 0 };
        Bounds b = player.getBounds();
        return new double[] { b.getCenterX(), b.getCenterY() };
    }

    // =====================================================================
    // COLISIONES CON OBSTÁCULOS
    // =====================================================================

    /**
     * Resuelve colisiones player/enemigos contra obstáculos.
     */
    private void resolveObstacleCollisions() {
        if (player != null) {
            for (GameEntity obs : obstacles) {
                resolveCollision(player, obs);
            }
        }

        for (Enemy e : enemies) {
            for (GameEntity obs : obstacles) {
                resolveCollision(e, obs);
            }
        }
    }

    /**
     * Empuja una entidad dinámica fuera de una entidad estática cuando sus bounds intersectan.
     *
     * @param dynamic   entidad móvil
     * @param staticEnt entidad estática (obstáculo)
     */
    private void resolveCollision(GameEntity dynamic, GameEntity staticEnt) {
        if (staticEnt == shopKeeperEntity) return;
        if (dynamic.getView() == null || staticEnt.getView() == null) return;

        Bounds d = dynamic.getBounds();
        Bounds s = staticEnt.getBounds();

        if (!d.intersects(s)) return;

        double dx = d.getCenterX() - s.getCenterX();
        double dy = d.getCenterY() - s.getCenterY();

        double halfWidths = (d.getWidth() / 2.0) + (s.getWidth() / 2.0);
        double halfHeights = (d.getHeight() / 2.0) + (s.getHeight() / 2.0);

        double overlapX = halfWidths - Math.abs(dx);
        double overlapY = halfHeights - Math.abs(dy);

        if (overlapX > 0 && overlapY > 0) {
            if (overlapX < overlapY) {
                double sign = Math.signum(dx);
                dynamic.getView().setLayoutX(dynamic.getView().getLayoutX() + (overlapX * sign));
            } else {
                double sign = Math.signum(dy);
                dynamic.getView().setLayoutY(dynamic.getView().getLayoutY() + (overlapY * sign));
            }
        }
    }

    // =====================================================================
    // SEPARACIÓN ENTRE ENEMIGOS
    // =====================================================================

    /**
     * Aplica separación simple entre enemigos para evitar stacking.
     *
     * @param dt delta time
     */
    private void applyEnemySeparation(double dt) {
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e1 = enemies.get(i);
            for (int j = i + 1; j < enemies.size(); j++) {
                Enemy e2 = enemies.get(j);

                double dx = e1.getCenterX() - e2.getCenterX();
                double dy = e1.getCenterY() - e2.getCenterY();
                double distSq = dx * dx + dy * dy;

                if (distSq < 900) {
                    double dist = Math.sqrt(distSq);
                    if (dist < 0.1) dist = 0.1;

                    double force = (30.0 - dist) / dist;
                    double pushX = dx * force * dt * 50.0;
                    double pushY = dy * force * dt * 50.0;

                    e1.setPosition(e1.getX() + pushX, e1.getY() + pushY);
                    e2.setPosition(e2.getX() - pushX, e2.getY() - pushY);
                }
            }
        }
    }

    // =====================================================================
    // COLOCACIÓN DE INTERACTIVOS (ANTI-COLISIÓN)
    // =====================================================================

    /**
     * Si una entidad interactiva spawnea encima de obstáculos, la empuja “en escalera”
     * hasta que no colisione o hasta agotar tries.
     *
     * @param entity entidad interactiva
     */
    private void checkAndPushInteractive(GameEntity entity) {
        if (entity == null || entity.getView() == null) return;

        int tries = 0;
        boolean collided = true;

        while (collided && tries < 15) {
            collided = false;
            Bounds b = entity.getBounds();

            for (GameEntity obs : obstacles) {
                if (obs != entity && obs.getBounds().intersects(b)) {
                    collided = true;

                    entity.getView().setLayoutX(entity.getView().getLayoutX() + 60);
                    if (entity.getView().getLayoutX() > gameArea.getWidth() - 100) {
                        entity.getView().setLayoutX(100);
                        entity.getView().setLayoutY(entity.getView().getLayoutY() + 60);
                    }
                    break;
                }
            }
            tries++;
        }
    }

    // =====================================================================
    // GAME OVER / VICTORY
    // =====================================================================

    /**
     * Registra una muerte del jugador y dispara el trigger de logros con el total persistido.
     * Debe llamarse una sola vez por muerte real.
     */
    private void registerPlayerDeath() {
        int profileId = AppContext.getProfileId();
        db.recordRunEndAsyncWithDeathTotal(profileId, false, score, currentFloor)
                .thenAccept(totalDeaths -> {
                    System.out.println("[Achievements] Death registered for profile " + profileId
                            + " (total deaths=" + totalDeaths + ").");
                    achievements.onDeath(totalDeaths);
                });
    }

    /**
     * Muestra el overlay de Game Over, detiene el loop y guarda run.
     */
    private void showGameOverOverlay() {
        if (gameOverShown) return;
        gameOverShown = true;

        if (gameLoop != null) gameLoop.stop();
        registerPlayerDeath();

        gameOverOverlay = OverlayRouter.showOverlay(overlayLayer, "ui/game_over.fxml", 0.85, c -> {
            if (!(c instanceof GameOverController goc)) return;

            goc.setTitle(TXT_GAME_OVER);
            goc.setOnRetry(() -> {
                OverlayRouter.closeOverlay(overlayLayer, gameOverOverlay);
                gameOverOverlay = null;

                restartPending = true;
                SceneRouter.goWithFadeKeepSize("ui/game.fxml");
            });
            goc.setOnBackToMenu(() -> {
                OverlayRouter.closeOverlay(overlayLayer, gameOverOverlay);
                gameOverOverlay = null;

                backToMenu();
            });
        });
    }

    /**
     * Muestra overlay de victoria, cambia música a victory_theme y guarda run.
     */
    private void showVictoryOverlay() {
        if (gameOverShown) return;
        gameOverShown = true;

        AssetsManager.stopMusic();
        AssetsManager.playMusic("victory_theme.mp3", true);

        currentFloorMusicFile = null;
        currentBossMusicFile = null;
        musicStarted = true;

        if (gameLoop != null) gameLoop.stop();
        db.recordRunEndAsync(AppContext.getProfileId(), true, score, currentFloor);

        gameOverOverlay = OverlayRouter.showOverlay(overlayLayer, "ui/game_over.fxml", 0.85, c -> {
            if (!(c instanceof GameOverController goc)) return;

            goc.setTitle(TXT_VICTORIA);

            goc.setOnRetry(() -> {
                AssetsManager.stopMusic();

                OverlayRouter.closeOverlay(overlayLayer, gameOverOverlay);
                gameOverOverlay = null;

                restartPending = true;
                SceneRouter.goWithFadeKeepSize("ui/game.fxml");
            });

            goc.setOnBackToMenu(() -> {
                AssetsManager.stopMusic();

                OverlayRouter.closeOverlay(overlayLayer, gameOverOverlay);
                gameOverOverlay = null;

                backToMenu();
            });
        });
    }
}
