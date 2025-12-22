package com.layla.entities;

import java.util.Objects;
import java.util.function.Consumer;

import com.layla.core.GameEntity;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Botón interactivo tipo "Greed" que reacciona a la proximidad/colisión del jugador.
 *
 * <p>Comportamiento:</p>
 * <ul>
 *   <li>Cuando el jugador lo pisa (distancia al centro &lt; umbral) y no hay cooldown, dispara {@code onPressed}.</li>
 *   <li>El controlador externo decide qué significa “pulsar” (activar oleada, salir de sala, etc.).</li>
 *   <li>Soporta dos modos visuales:
 *     <ul>
 *       <li>Modo normal Greed: cambia estética según {@link #setActiveState(boolean)}.</li>
 *       <li>Modo salida: {@link #setAsExit()} cambia icono/estética y no se ve afectado por activeState.</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class GreedButton implements GameEntity {

    // --- Visual tuning ---
    private static final double VIEW_SIZE = 44.0;
    private static final double VIEW_HALF = VIEW_SIZE / 2.0;

    private static final double BUTTON_SIZE_READY = 30.0;
    private static final double BUTTON_SIZE_ACTIVE = 26.0;

    private static final double PRESS_DISTANCE = 32.0;
    private static final double PRESS_COOLDOWN_SEC = 1.0;

    private static final Runnable NO_OP = () -> {};

    private final StackPane view;
    private final Rectangle base;
    private final Rectangle button;
    private final Text icon;

    private final Consumer<GreedButton> onPressed;

    private double cooldown = 0.0;

    /** True = ronda/oleada activa (hundido/peligro). False = listo/levantado. */
    private boolean activeState = false;

    /** True si este botón representa salida (siguiente piso). */
    private boolean exitMode = false;

    // Para no re-aplicar estilos cada frame si no cambió nada
    private boolean lastActiveState = false;
    private boolean lastExitMode = false;

    /**
     * Crea un botón Greed en coordenadas (x,y) y lo añade al {@code parent}.
     *
     * @param x      centro X del botón
     * @param y      centro Y del botón
     * @param parent contenedor JavaFX
     * @param onPressed callback al “pisar/pulsar” el botón (si es null, no hace nada)
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

        view.getChildren().addAll(base, button, icon);
        parent.getChildren().add(view);

        // Inicializa estilos coherentes
        applyVisualState(true);
    }

    @Override
    public void update(double dt) {
        if (dt > 0.0 && cooldown > 0.0) {
            cooldown = Math.max(0.0, cooldown - dt);
        }

        // Solo recalcula estilos si cambió algo
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

        double dist = Math.hypot(b1.getCenterX() - b2.getCenterX(), b1.getCenterY() - b2.getCenterY());
        if (dist < PRESS_DISTANCE) {
            onPressed.accept(this);
            cooldown = PRESS_COOLDOWN_SEC;
        }
    }

    /**
     * Cambia el estado visual normal del botón (solo afecta si NO está en modo salida).
     *
     * @param active true = oleada activa/peligro (hundido), false = listo (levantado)
     */
    public void setActiveState(boolean active) {
        this.activeState = active;
        applyVisualState(true);
    }

    /**
     * Convierte este botón en un botón de salida (siguiente piso).
     * <p>Importante: en el código original el azul se perdía en {@code update()}; aquí ya no.</p>
     */
    public void setAsExit() {
        this.exitMode = true;
        icon.setText("▼");
        applyVisualState(true);
    }

    private void applyVisualState(boolean force) {
        if (!force && activeState == lastActiveState && exitMode == lastExitMode) {
            return;
        }
        lastActiveState = activeState;
        lastExitMode = exitMode;

        if (exitMode) {
            // Modo salida: no depende de activeState
            base.setStroke(Color.BLACK);
            button.setWidth(BUTTON_SIZE_READY);
            button.setHeight(BUTTON_SIZE_READY);
            button.setFill(Color.DODGERBLUE);
            icon.setFill(Color.web("#001a4d"));
            return;
        }

        if (activeState) {
            // Hundido / peligro
            button.setFill(Color.RED);
            button.setWidth(BUTTON_SIZE_ACTIVE);
            button.setHeight(BUTTON_SIZE_ACTIVE);
            base.setStroke(Color.DARKRED);
            icon.setFill(Color.web("#500000"));
        } else {
            // Listo / levantado
            button.setFill(Color.rgb(180, 40, 40));
            button.setWidth(BUTTON_SIZE_READY);
            button.setHeight(BUTTON_SIZE_READY);
            base.setStroke(Color.BLACK);
            icon.setFill(Color.web("#500000"));
        }
    }
}
