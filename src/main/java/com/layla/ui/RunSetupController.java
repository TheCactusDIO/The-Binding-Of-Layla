package com.layla.ui;

import com.layla.AppContext;
import com.layla.model.CharacterType;
import com.layla.services.AchievementService;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Pantalla de preparación de partida:
 * - Selección de personaje.
 * - Modo difícil (bloqueable por logro).
 * - Acceso a "modo personalizado" (Developer Tools) mediante overlay.
 * - Lanzar partida y mostrar intro overlay.
 */
public final class RunSetupController implements ViewLifecycle {

    // ---------- Constantes (IDs, estilos y rutas) ----------
    private static final String ACH_UNLOCK_FRAGILE   = "ACH_SURVIVOR";
    private static final String ACH_UNLOCK_HARDMODE  = "ACH_FLOOR_MASTER_1";

    private static final String FXML_SETTINGS = "ui/settings.fxml";
    private static final String FXML_GAME     = "ui/game.fxml";
    private static final String FXML_MAINMENU = "ui/main_menu.fxml";

    private static final String STYLE_SELECTED =
            "-fx-background-color: #444; -fx-border-color: #ffd54f; -fx-border-width: 2; " +
            "-fx-background-radius: 8; -fx-border-radius: 8;";

    private static final String STYLE_UNSELECTED =
            "-fx-background-color: #222; -fx-border-color: #444; -fx-border-width: 2; " +
            "-fx-background-radius: 8; -fx-border-radius: 8; -fx-cursor: hand;";

    // Intro overlay timings
    private static final int INTRO_FADE_IN_MS  = 350;
    private static final int INTRO_HOLD_MS     = 1600;
    private static final int INTRO_FADE_OUT_MS = 300;

    // SFX timing (si tu WAV dura ~4s)
    private static final int SFX_MS = 4000;

    // ---------- FXML ----------
    @FXML private StackPane root;
    @FXML private VBox charBoxLayla;
    @FXML private VBox charBoxFragile;
    @FXML private CheckBox hardModeCheck;
    @FXML private Label fragileLockLabel;

    // ---------- Estado ----------
    private CharacterType selected = CharacterType.LAYLA;
    private final AchievementService achievements = AppContext.achievements();
    private Node settingsOverlay;

    @FXML
    private void initialize() {
        // Aquí no hace falta poner textos: ya están hardcodeados en el FXML.
        // Dejamos initialize() para wiring/ajustes futuros.
    }

    /**
     * Se ejecuta al entrar a la escena:
     * - Aplica bloqueos por logros.
     * - Resetea selección por defecto.
     */
    @Override
    public void onEnter() {
        boolean fragileUnlocked = achievements.isUnlocked(ACH_UNLOCK_FRAGILE);
        boolean hardModeUnlocked = achievements.isUnlocked(ACH_UNLOCK_HARDMODE);

        applyFragileLockState(fragileUnlocked);
        applyHardModeLockState(hardModeUnlocked);

        // Selección por defecto al entrar
        selectCharacter(CharacterType.LAYLA);
    }

    // -----------------------------
    // Estados de bloqueo
    // -----------------------------

    /** Bloquea/desbloquea el personaje "The Fragile" y muestra el aviso correspondiente. */
    private void applyFragileLockState(boolean unlocked) {
        if (charBoxFragile == null) return;

        if (!unlocked) {
            charBoxFragile.setDisable(true);
            charBoxFragile.setOpacity(0.4);

            if (fragileLockLabel != null) {
                fragileLockLabel.setText("BLOQUEADO (Requiere el logro «Superviviente»)");
                fragileLockLabel.setVisible(true);
            }
        } else {
            charBoxFragile.setDisable(false);
            charBoxFragile.setOpacity(1.0);

            if (fragileLockLabel != null) {
                fragileLockLabel.setVisible(false);
            }
        }
    }

    /** Bloquea/desbloquea el checkbox de modo difícil y ajusta su texto. */
    private void applyHardModeLockState(boolean unlocked) {
        if (hardModeCheck == null) return;

        if (!unlocked) {
            hardModeCheck.setDisable(true);
            hardModeCheck.setSelected(false);
            hardModeCheck.setText("Modo difícil (bloqueado - supera Sótano 1)");
        } else {
            hardModeCheck.setDisable(false);
            hardModeCheck.setText("Modo difícil");
        }
    }

    // -----------------------------
    // Selección de personaje
    // -----------------------------

    /** Selecciona Layla al hacer click. */
    @FXML
    private void onSelectLayla() {
        selectCharacter(CharacterType.LAYLA);
    }

    /** Selecciona The Fragile si está desbloqueado. */
    @FXML
    private void onSelectFragile() {
        if (achievements.isUnlocked(ACH_UNLOCK_FRAGILE)) {
            selectCharacter(CharacterType.THE_FRAGILE);
        }
    }

    /** Aplica el estilo de seleccionado/no seleccionado y guarda la elección. */
    private void selectCharacter(CharacterType type) {
        selected = type;

        if (charBoxLayla != null) {
            charBoxLayla.setStyle(type == CharacterType.LAYLA ? STYLE_SELECTED : STYLE_UNSELECTED);
        }
        if (charBoxFragile != null) {
            charBoxFragile.setStyle(type == CharacterType.THE_FRAGILE ? STYLE_SELECTED : STYLE_UNSELECTED);
        }
    }

    // -----------------------------
    // Acciones
    // -----------------------------

    /**
     * Abre el overlay de settings directamente en herramientas avanzadas / dev tools.
     * (Se llama "Modo personalizado" en UI).
     */
    @FXML
    private void onCustomMode() {
        if (settingsOverlay != null) return;
        if (root == null) return;

        settingsOverlay = OverlayRouter.showOverlay(root, FXML_SETTINGS, 0.90, controller -> {
            if (controller instanceof SettingsController sc) {
                sc.setStatsService(AppContext.stats());
                sc.setOverlayHost(root);

                // Forzar que aparezca la sección avanzada
                sc.setExpandDeveloperTools(true);

                sc.setOnClose(() -> {
                    OverlayRouter.closeOverlay(root, settingsOverlay);
                    settingsOverlay = null;
                });

                sc.onShow();
            }
        });
    }

    /**
     * Inicia partida:
     * - Guarda selección (personaje + modo difícil).
     * - Para la música del menú.
     * - Va a game.fxml con transición.
     * - Cuando existe GameController, muestra overlay de intro y lanza la partida.
     */
    @FXML
    private void onPlay() {
        AppContext.setSelectedCharacter(selected);
        AppContext.setHardMode(hardModeCheck != null && hardModeCheck.isSelected());

        System.out.println("[RunSetup] Empezar partida: " + selected + ", modo difícil=" + AppContext.isHardMode());

        com.layla.core.AssetsManager.stopMusic();
        SceneRouter.goWithFadeKeepSize(FXML_GAME);

        // Esperamos a que exista GameController (después del cambio de escena)
        SceneRouter.whenControllerIs(GameController.class, this::showIntroOverlayAndStart);
    }

    /** Vuelve al menú principal. */
    @FXML
    private void onBack() {
        SceneRouter.goWithFadeKeepSize(FXML_MAINMENU);
    }

    // -----------------------------
    // Intro overlay (arranque de partida)
    // -----------------------------

    /**
     * Muestra el overlay de intro (nombre de piso + subtítulo) y al terminar:
     * - reproduce SFX de inicio
     * - hace signalGameStart()
     * - arranca música de piso si aplica
     */
    private void showIntroOverlayAndStart(GameController gc) {
        var overlayLayer = gc.getOverlayLayer();
        if (overlayLayer == null || overlayLayer.getScene() == null) {
            gc.startFloorMusicIfNeeded();
            return;
        }

        // Capa negra a pantalla completa (overlay)
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: black;");
        overlay.setOpacity(1.0);

        var rootNode = overlayLayer.getScene().getRoot();
        if (rootNode instanceof Region r) {
            overlay.prefWidthProperty().bind(r.widthProperty());
            overlay.prefHeightProperty().bind(r.heightProperty());
        }

        // Contenido (título + subtítulo)
        VBox vbox = new VBox(8);
        vbox.setAlignment(Pos.CENTER);
        vbox.setMouseTransparent(true);

        String titleText = "Sótano I";
        if (AppContext.isHardMode()) titleText += " (Difícil)";

        Label title = new Label(titleText);
        title.getStyleClass().add("intro-title");

        String subtitleText = (selected == CharacterType.THE_FRAGILE)
                ? "El Frágil"
                : "The Binding of Layla";

        Label subtitle = new Label(subtitleText);
        subtitle.getStyleClass().add("intro-subtitle");

        vbox.getChildren().addAll(title, subtitle);
        overlay.getChildren().add(vbox);
        overlayLayer.getChildren().add(overlay);

        // Animación de intro (fade in -> hold -> fade out)
        FadeTransition fadeIn = new FadeTransition(Duration.millis(INTRO_FADE_IN_MS), vbox);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        PauseTransition hold = new PauseTransition(Duration.millis(INTRO_HOLD_MS));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(INTRO_FADE_OUT_MS), vbox);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

        new SequentialTransition(fadeIn, hold, fadeOut).play();

        // SFX + arranque real del gameplay
        com.layla.core.AssetsManager.setSfxVolume(1.0);
        com.layla.core.AssetsManager.playSfx("game_start.wav");

        PauseTransition finish = new PauseTransition(Duration.millis(SFX_MS));
        finish.setOnFinished(ev -> {
            overlayLayer.getChildren().remove(overlay);
            gc.signalGameStart();
            gc.startFloorMusicIfNeeded();
        });
        finish.play();
    }
}
