package com.layla.entities;

import java.util.Objects;
import java.util.function.Supplier;

import com.layla.core.GameEntity;
import com.layla.core.InputService;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Controllable player entity driven by keyboard input.
 */
public final class Player implements GameEntity {

    private static final double MAX_SPEED   = 320.0;  // antes 260
    private static final double TAU_ACCEL   = 0.035;  // acelera rápido
    private static final double TAU_DECEL   = 0.090;  // frena suave
    private static final double TAU_REVERSE = 0.045;  // invertir dirección ágil
    private static final double ACCELERATION = 850.0;
    private static final double FRICTION = 900.0;

    private final Rectangle view = new Rectangle(26, 26);
    private final Supplier<double[]> moveSupplier;
    private final Pane boundsPane;

    private double vx;
    private double vy;

    public Player(InputService input, Pane boundsPane) {
        this(Objects.requireNonNull(input, "input")::getMoveVector, boundsPane);
    }

    public Player(Supplier<double[]> moveSupplier, Pane boundsPane) {
        this.moveSupplier = Objects.requireNonNull(moveSupplier, "moveSupplier");
        this.boundsPane = Objects.requireNonNull(boundsPane, "boundsPane");
        view.setFill(Color.RED);
        view.setStroke(Color.BLACK);
    }

   @Override
    public void update(double dt) {
        if (dt <= 0) return;

        // Objetivo de velocidad según input
        double[] mv = moveSupplier.get();
        double targetVx = mv[0] * MAX_SPEED;
        double targetVy = mv[1] * MAX_SPEED;

        // Elige tau por eje: acelerar, frenar o invertir
        double tauX = pickTau(vx, targetVx);
        double tauY = pickTau(vy, targetVy);

        // Suavizado exponencial (frame-rate independiente)
        double ax = 1.0 - Math.exp(-dt / tauX);
        double ay = 1.0 - Math.exp(-dt / tauY);

        vx += (targetVx - vx) * ax;
        vy += (targetVy - vy) * ay;

        // Clamp por seguridad
        double speed = Math.hypot(vx, vy);
        if (speed > MAX_SPEED && speed > 0) {
            double s = MAX_SPEED / speed;
            vx *= s; vy *= s;
        }

        // Integración + clamping en Pane
        double nextX = view.getTranslateX() + vx * dt;
        double nextY = view.getTranslateY() + vy * dt;

        double maxX = Math.max(0.0, boundsPane.getWidth()  - view.getWidth());
        double maxY = Math.max(0.0, boundsPane.getHeight() - view.getHeight());

        if (nextX < 0.0) { nextX = 0.0; vx = 0.0; }
        else if (nextX > maxX) { nextX = maxX; vx = 0.0; }

        if (nextY < 0.0) { nextY = 0.0; vy = 0.0; }
        else if (nextY > maxY) { nextY = maxY; vy = 0.0; }

        view.setTranslateX(nextX);
        view.setTranslateY(nextY);
    }

    // Selecciona constante de tiempo según situación
    private static double pickTau(double v, double tv) {
        if (tv == 0.0) return TAU_DECEL;                 // soltar → frenar suave
        if (Math.signum(v) != Math.signum(tv) && v != 0) return TAU_REVERSE; // invertir
        return TAU_ACCEL;                                 // acelerar
    }

    private double applyFriction(double velocity, double dt) {
        double drag = FRICTION * dt;
        double abs = Math.abs(velocity);
        if (abs <= drag) {
            return 0.0;
        }
        return velocity > 0 ? velocity - drag : velocity + drag;
    }

    private void limitVelocity() {
        double speed = Math.hypot(vx, vy);
        if (speed > MAX_SPEED && speed > 0) {
            double scale = MAX_SPEED / speed;
            vx *= scale;
            vy *= scale;
        }
    }

    private void updatePosition(double dt) {
        double nextX = view.getTranslateX() + vx * dt;
        double nextY = view.getTranslateY() + vy * dt;

        double maxX = Math.max(0.0, boundsPane.getWidth() - view.getWidth());
        double maxY = Math.max(0.0, boundsPane.getHeight() - view.getHeight());

        if (nextX < 0.0) {
            nextX = 0.0;
            vx = 0.0;
        } else if (nextX > maxX) {
            nextX = maxX;
            vx = 0.0;
        }

        if (nextY < 0.0) {
            nextY = 0.0;
            vy = 0.0;
        } else if (nextY > maxY) {
            nextY = maxY;
            vy = 0.0;
        }

        view.setTranslateX(nextX);
        view.setTranslateY(nextY);
    }

    @Override
    public Node getView() {
        return view;
    }

    public void setPosition(double x, double y) {
        view.setTranslateX(x);
        view.setTranslateY(y);
    }

    public double getWidth() {
        return view.getWidth();
    }

    public double getHeight() {
        return view.getHeight();
    }
}
