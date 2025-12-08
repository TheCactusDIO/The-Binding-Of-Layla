package com.layla.ui;

import com.layla.AppContext;
import com.layla.model.CharacterType;
import com.layla.services.AchievementService;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class RunSetupController implements ViewLifecycle {

    @FXML private VBox charBoxLayla;
    @FXML private VBox charBoxFragile;
    @FXML private CheckBox hardModeCheck;
    @FXML private Button playButton;
    @FXML private Label fragileLockLabel;

    // NUEVO: Spinners para custom modifiers
    @FXML private Spinner<Double> spinHp;
    @FXML private Spinner<Double> spinDmg;
    @FXML private Spinner<Double> spinCount;
    @FXML private Spinner<Integer> spinWaves;

    private CharacterType selected = CharacterType.LAYLA;
    private final AchievementService achievements = AppContext.achievements();

    @FXML
    private void initialize() {
        // Inicializar Spinners
        if (spinHp != null) {
            spinHp.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.1, 10.0, 1.0, 0.1));
        }
        if (spinDmg != null) {
            spinDmg.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.1, 10.0, 1.0, 0.1));
        }
        if (spinCount != null) {
            spinCount.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.1, 5.0, 1.0, 0.1));
        }
        if (spinWaves != null) {
            spinWaves.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 5));
        }
    }

    @Override
    public void onEnter() {
        boolean fragileUnlocked = achievements.isUnlocked("ACH_SURVIVOR");
        boolean hardModeUnlocked = achievements.isUnlocked("ACH_FLOOR_MASTER_1");

        // Configurar estado de The Fragile
        if (!fragileUnlocked) {
            charBoxFragile.setDisable(true);
            charBoxFragile.setOpacity(0.4);
            if (fragileLockLabel != null) {
                fragileLockLabel.setText("LOCKED (Requires 'Survivor' achievement)");
                fragileLockLabel.setVisible(true);
            }
        } else {
            charBoxFragile.setDisable(false);
            charBoxFragile.setOpacity(1.0);
            if (fragileLockLabel != null) fragileLockLabel.setVisible(false);
        }

        // Configurar Hard Mode
        if (!hardModeUnlocked) {
            hardModeCheck.setDisable(true);
            hardModeCheck.setText("Hard Mode (Locked - Beat Basement 1)");
        } else {
            hardModeCheck.setDisable(false);
            hardModeCheck.setText("Hard Mode");
        }

        // Reset modifiers UI
        if(spinHp!=null) spinHp.getValueFactory().setValue(1.0);
        if(spinDmg!=null) spinDmg.getValueFactory().setValue(1.0);
        if(spinCount!=null) spinCount.getValueFactory().setValue(1.0);
        if(spinWaves!=null) spinWaves.getValueFactory().setValue(5);

        selectCharacter(CharacterType.LAYLA);
    }

    @FXML
    private void onSelectLayla() {
        selectCharacter(CharacterType.LAYLA);
    }

    @FXML
    private void onSelectFragile() {
        if (AppContext.achievements().isUnlocked("ACH_SURVIVOR")) {
            selectCharacter(CharacterType.THE_FRAGILE);
        }
    }

    private void selectCharacter(CharacterType type) {
        this.selected = type;

        // Estilos visuales de selección (borde dorado para seleccionado)
        String selectedStyle = "-fx-background-color: #444; -fx-border-color: #ffd54f; -fx-border-width: 2; -fx-background-radius: 8; -fx-border-radius: 8;";
        String unselectedStyle = "-fx-background-color: #222; -fx-border-color: #444; -fx-border-width: 2; -fx-background-radius: 8; -fx-border-radius: 8; -fx-cursor: hand;";

        if (type == CharacterType.LAYLA) {
            if (charBoxLayla != null) charBoxLayla.setStyle(selectedStyle);
            if (charBoxFragile != null) charBoxFragile.setStyle(unselectedStyle);
        } else {
            if (charBoxLayla != null) charBoxLayla.setStyle(unselectedStyle);
            if (charBoxFragile != null) charBoxFragile.setStyle(selectedStyle);
        }
    }

    @FXML
    private void onPlay() {
        // Guardar configuración en AppContext
        AppContext.setSelectedCharacter(selected);
        AppContext.setHardMode(hardModeCheck.isSelected());

        // NUEVO: Guardar modificadores custom
        var mods = AppContext.getRunModifiers();
        mods.enemyHpMult = spinHp.getValue();
        mods.enemyDmgMult = spinDmg.getValue();
        mods.spawnRateMult = spinCount.getValue();
        mods.wavesPerFloor = spinWaves.getValue();

        System.out.println("[RunSetup] Starting run: " + selected + ", HardMode=" + hardModeCheck.isSelected() +
                           ", Mods=[HP:x" + mods.enemyHpMult + ", Dmg:x" + mods.enemyDmgMult + "]");

        // Iniciar juego (lógica copiada y adaptada de MainMenuController para la intro)
        com.layla.core.AssetsManager.stopMusic();
        SceneRouter.goWithFadeKeepSize("ui/game.fxml");

        SceneRouter.whenControllerIs(GameController.class, gc -> {
            var overlayLayer = gc.getOverlayLayer();
            if (overlayLayer == null) {
                gc.startFloorMusicIfNeeded();
                return;
            }
            var overlay = new javafx.scene.layout.StackPane();
            overlay.setStyle("-fx-background-color: black;");
            overlay.setOpacity(1.0);

            // Binding seguro
            var rootPane = overlayLayer.getScene().getRoot();
            if (rootPane instanceof javafx.scene.layout.Region r) {
                overlay.prefWidthProperty().bind(r.widthProperty());
                overlay.prefHeightProperty().bind(r.heightProperty());
            }

            var vbox = new javafx.scene.layout.VBox(8);
            vbox.setAlignment(javafx.geometry.Pos.CENTER);
            vbox.setMouseTransparent(true);

            String titleText = "Basement I";
            if (AppContext.isHardMode()) titleText += " (Hard)";

            var title = new javafx.scene.control.Label(titleText);
            title.getStyleClass().add("intro-title");

            String sub = selected == CharacterType.THE_FRAGILE ? "The Fragile One" : "The Binding of Layla";
            var subtitle = new javafx.scene.control.Label(sub);
            subtitle.getStyleClass().add("intro-subtitle");

            vbox.getChildren().addAll(title, subtitle);
            overlay.getChildren().add(vbox);
            overlayLayer.getChildren().add(overlay);

            var fadeIn = new FadeTransition(Duration.millis(350), vbox);
            fadeIn.setFromValue(0); fadeIn.setToValue(1);
            var hold   = new PauseTransition(Duration.millis(1600));
            var fadeOut= new FadeTransition(Duration.millis(300), vbox);
            fadeOut.setFromValue(1); fadeOut.setToValue(0);
            new javafx.animation.SequentialTransition(fadeIn, hold, fadeOut).play();

            final int SFX_MS = 4000;
            com.layla.core.AssetsManager.setSfxVolume(1.0);
            com.layla.core.AssetsManager.playSfx("game_start.wav");
            var finish = new PauseTransition(Duration.millis(SFX_MS));
            finish.setOnFinished(ev -> {
                overlayLayer.getChildren().remove(overlay);
                gc.signalGameStart();
                gc.startFloorMusicIfNeeded();
            });
            finish.play();
        });
    }

    @FXML
    private void onBack() {
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }
}
