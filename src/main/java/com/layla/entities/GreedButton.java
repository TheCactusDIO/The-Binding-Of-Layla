package com.layla.entities;

import java.util.Objects;
import java.util.function.Consumer;

import com.layla.core.AssetsManager;
import com.layla.core.GameEntity;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Botón interactivo tipo "Greed" (Codicia) que reacciona a la proximidad del jugador.
 * <p>
 * Funcionalidad:
 * <ul>
 * <li>Se activa cuando el jugador camina sobre él (colisión).</li>
 * <li>Tiene un tiempo de enfriamiento (cooldown) para evitar pulsaciones múltiples accidentales.</li>
 * <li>El efecto de la pulsación se delega al callback {@code onPressed}.</li>
 * </ul>
 * <p>
 * Modos visuales:
 * <ul>
 * <li><strong>Modo Estándar:</strong> Botón rojo. Cambia de aspecto si hay una oleada activa (hundido/peligro) o si está listo (levantado).</li>
 * <li><strong>Modo Salida:</strong> Botón azul. Indica el fin del nivel o transición de piso. Ignora el estado de "peligro".</li>
 * </ul>
 */
public class GreedButton implements GameEntity {

    // --- Ajustes Visuales ---
    private static final double VIEW_SIZE = 44.0;
    private static final double VIEW_HALF = VIEW_SIZE / 2.0;

    private static final double BUTTON_SIZE_READY = 30.0;
    private static final double BUTTON_SIZE_ACTIVE = 26.0;
    private static final double TROPHY_ICON_SIZE = 22.0;

    private static final double PRESS_DISTANCE = 32.0;
    private static final double PRESS_COOLDOWN_SEC = 1.0;

    private static final Runnable NO_OP = () -> {};

    private final StackPane view;
    private final Rectangle base;
    private final Rectangle button;
    private final Text icon;
    private final ImageView trophyIcon;

    private final Consumer<GreedButton> onPressed;

    private double cooldown = 0.0;

    /** Estado de actividad: true = oleada en curso (botón hundido/peligro), false = listo para pulsar. */
    private boolean activeState = false;

    /** Si es true, el botón funciona como salida del nivel (color azul). */
    private boolean exitMode = false;
    private boolean victoryMode = false;

    // Caché para optimizar el redibujado
    private boolean lastActiveState = false;
    private boolean lastExitMode = false;
    private boolean lastVictoryMode = false;

    /**
     * Crea un nuevo botón Greed.
     *
     * @param x         Posición X central.
     * @param y         Posición Y central.
     * @param parent    Panel contenedor.
     * @param onPressed Acción a ejecutar al ser pulsado.
     */
    public GreedButton(double x, double y, Pane parent, Consumer<GreedButton> onPressed) {
        Objects.requireNonNull(parent, "parent");
        this.onPressed = (onPressed != null) ? onPressed : b -> NO_OP.run();

        view = new StackPane();
        view.setPrefSize(VIEW_SIZE, VIEW_SIZE);
        view.setLayoutX(x - VIEW_HALF);
        view.setLayoutY(y - VIEW_HALF);
        view.setEffect(new DropShadow(10, Color.BLACK));

        base = new Rectangle(VIEW_SIZE, VIEW_SIZE, Color.rgb(60, 60, 60));
        base.setArcWidth(10);
        base.setArcHeight(10);
        base.setStroke(Color.BLACK);
        base.setStrokeWidth(2);

        button = new Rectangle(BUTTON_SIZE_READY, BUTTON_SIZE_READY, Color.rgb(180, 40, 40));
        button.setArcWidth(6);
        button.setArcHeight(6);
        button.setStroke(Color.BLACK);
        button.setStrokeType(StrokeType.INSIDE);
        button.setStrokeWidth(2);

        icon = new Text("G");
        icon.setFont(Font.font("Verdana", FontWeight.BOLD, 18));
        icon.setFill(Color.web("#500000"));

        Image trophyImage = AssetsManager.loadImage("assets/images/trophy.png");
        trophyIcon = new ImageView(trophyImage);
        trophyIcon.setFitWidth(TROPHY_ICON_SIZE);
        trophyIcon.setFitHeight(TROPHY_ICON_SIZE);
        trophyIcon.setPreserveRatio(true);
        trophyIcon.setVisible(false);

        view.getChildren().addAll(base, button, icon, trophyIcon);
        parent.getChildren().add(view);

        // Inicializa el estado visual
        applyVisualState(true);
    }

    public void setAsVictory() {
        setAsExit();
        this.victoryMode = true;
        applyVisualState(true);
    }

    /**
     * Actualiza el cooldown y el estado visual si es necesario.
     *
     * @param dt Delta time.
     */
    @Override
    public void update(double dt) {
        if (dt > 0.0 && cooldown > 0.0) {
            cooldown = Math.max(0.0, cooldown - dt);
        }

        // Aplica cambios visuales solo si el estado ha cambiado
        applyVisualState(false);
    }

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public Bounds getBounds() {
        return view.getBoundsInParent();
    }

    /**
     * Detecta la colisión con el jugador para activar el botón.
     *
     * @param other La otra entidad.
     */
    @Override
    public void onCollision(GameEntity other) {
        if (!(other instanceof Player)) {
            return;
        }
        if (cooldown > 0.0) {
            return;
        }

        Bounds b1 = getBounds();
        Bounds b2 = other.getBounds();
        if (b1 == null || b2 == null) {
            return;
        }

        // Comprobación de distancia simple (centro a centro)
        double dist = Math.hypot(b1.getCenterX() - b2.getCenterX(), b1.getCenterY() - b2.getCenterY());
        if (dist < PRESS_DISTANCE) {
            onPressed.accept(this);
            cooldown = PRESS_COOLDOWN_SEC;
        }
    }

    /**
     * Establece el estado de actividad (hundido/peligro vs levantado).
     * Solo tiene efecto visual si NO está en modo salida.
     *
     * @param active true para indicar oleada activa/peligro.
     */
    public void setActiveState(boolean active) {
        this.activeState = active;
        applyVisualState(true);
    }

    /**
     * Configura el botón como salida del nivel.
     * Cambia el icono y el color a azul.
     */
    public void setAsExit() {
        this.exitMode = true;
        this.victoryMode = false;
        icon.setText("▼");
        applyVisualState(true);
    }

    /**
     * Aplica los estilos visuales según el estado actual.
     *
     * @param force Si es true, fuerza la actualización aunque el estado no haya cambiado.
     */
    private void applyVisualState(boolean force) {
        if (!force && activeState == lastActiveState && exitMode == lastExitMode
                && victoryMode == lastVictoryMode) {
            return;
        }
        lastActiveState = activeState;
        lastExitMode = exitMode;
        lastVictoryMode = victoryMode;

        boolean showTrophy = victoryMode && trophyIcon.getImage() != null;

        if (exitMode) {
            // Estilo de Salida (Azul)
            base.setStroke(Color.BLACK);
            button.setWidth(BUTTON_SIZE_READY);
            button.setHeight(BUTTON_SIZE_READY);
            button.setFill(Color.DODGERBLUE);
            icon.setFill(Color.web("#001a4d"));
            icon.setVisible(!showTrophy);
            trophyIcon.setVisible(showTrophy);
            return;
        }

        trophyIcon.setVisible(false);
        icon.setVisible(true);

        if (activeState) {
            // Estilo Activo/Peligro (Rojo intenso, hundido)
            button.setFill(Color.RED);
            button.setWidth(BUTTON_SIZE_ACTIVE);
            button.setHeight(BUTTON_SIZE_ACTIVE);
            base.setStroke(Color.DARKRED);
            icon.setFill(Color.web("#500000"));
        } else {
            // Estilo Listo (Rojo apagado, levantado)
            button.setFill(Color.rgb(180, 40, 40));
            button.setWidth(BUTTON_SIZE_READY);
            button.setHeight(BUTTON_SIZE_READY);
            base.setStroke(Color.BLACK);
            icon.setFill(Color.web("#500000"));
        }
    }
}
