package com.layla.entities;

import java.util.Objects;
import java.util.function.Consumer;

import com.layla.core.GameEntity;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeType;

/**
 * Indicador visual de spawn.
 *
 * <p>Muestra un círculo semi-transparente que "pulsa" durante un tiempo y,
 * al finalizar, elimina su nodo y dispara {@code onFinish}.</p>
 *
 * <p>Nota: no tiene colisiones (usa el default no-op de {@link GameEntity#onCollision(GameEntity)}).</p>
 */
public final class SpawnIndicator implements GameEntity {

    private static final double RADIUS = 12.0;

    private static final double BASE_ALPHA = 0.2;
    private static final double PULSE_ALPHA = 0.4;
    private static final double STROKE_ALPHA_BONUS = 0.3;

    private static final double FREQ_BASE = 5.0;
    private static final double FREQ_EXTRA = 15.0;

    private final Pane parent;
    private final Circle view;
    private final double durationSeconds;
    private final Consumer<SpawnIndicator> onFinish;

    private double timerSeconds = 0.0;
    private boolean finished = false;

    /**
     * Crea un indicador centrado en (x, y).
     *
     * @param x centro X del indicador (layoutX)
     * @param y centro Y del indicador (layoutY)
     * @param duration duración en segundos (si es 0 o negativa, se completará en el primer update)
     * @param parent contenedor donde se añade el nodo
     * @param onFinish callback al finalizar (no null)
     */
    public SpawnIndicator(double x, double y, double duration, Pane parent, Consumer<SpawnIndicator> onFinish) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.onFinish = Objects.requireNonNull(onFinish, "onFinish");
        this.durationSeconds = duration;

        this.view = new Circle(RADIUS, Color.rgb(255, 0, 0, BASE_ALPHA));
        this.view.setStroke(Color.rgb(255, 0, 0, clamp01(BASE_ALPHA + STROKE_ALPHA_BONUS)));
        this.view.setStrokeWidth(2.0);
        this.view.setStrokeType(StrokeType.INSIDE);
        this.view.getStrokeDashArray().addAll(5d, 5d);

        this.view.setLayoutX(x);
        this.view.setLayoutY(y);

        this.parent.getChildren().add(this.view);
        this.view.toBack();
    }

    @Override
    public void update(double dt) {
        if (finished) return;

        timerSeconds += dt;

        if (durationSeconds <= 0.0) {
            finish();
            return;
        }

        double progress = timerSeconds / durationSeconds; // puede pasar de 1.0, no pasa nada
        double frequency = FREQ_BASE + (FREQ_EXTRA * progress);

        double alpha = BASE_ALPHA + PULSE_ALPHA * Math.abs(Math.sin(timerSeconds * frequency));
        double strokeAlpha = clamp01(alpha + STROKE_ALPHA_BONUS);

        view.setFill(Color.rgb(255, 0, 0, clamp01(alpha)));
        view.setStroke(Color.rgb(255, 0, 0, strokeAlpha));

        if (timerSeconds >= durationSeconds) {
            finish();
        }
    }

    private void finish() {
        finished = true;
        parent.getChildren().remove(view);
        onFinish.accept(this);
    }

    /** @return centro X (layoutX) del indicador. */
    public double getX() {
        return view.getLayoutX();
    }

    /** @return centro Y (layoutY) del indicador. */
    public double getY() {
        return view.getLayoutY();
    }

    @Override
    public Node getView() {
        return view;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
