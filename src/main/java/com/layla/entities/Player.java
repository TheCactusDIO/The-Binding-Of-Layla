package com.layla.entities;

import java.util.Objects;
import java.util.function.Supplier;

import com.layla.core.GameEntity;
import com.layla.core.InputService;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Controllable player entity driven by keyboard input.
 * - Movimiento en coordenadas de mundo usando layoutX/layoutY (no translate).
 * - Suavizado con constantes de tiempo (tau) por eje.
 * - Clamping dentro de boundsPane.
 */
public final class Player implements GameEntity {

    private static final double MAX_SPEED    = 320.0;  // px/s
    private static final double TAU_ACCEL    = 0.035;  // acelera rápido
    private static final double TAU_DECEL    = 0.090;  // frena suave al soltar
    private static final double TAU_REVERSE  = 0.045;  // invertir dirección ágil

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

        // 1) Leer input normalizado (-1..1) y convertir a velocidad objetivo
        double[] mv = moveSupplier.get();
        double targetVx = mv[0] * MAX_SPEED;
        double targetVy = mv[1] * MAX_SPEED;

        // 2) Elegir tau por eje: acelerar, frenar o invertir
        double tauX = pickTau(vx, targetVx);
        double tauY = pickTau(vy, targetVy);

        // 3) Suavizado exponencial independiente del frame-rate
        double ax = 1.0 - Math.exp(-dt / tauX);
        double ay = 1.0 - Math.exp(-dt / tauY);

        vx += (targetVx - vx) * ax;
        vy += (targetVy - vy) * ay;

        // 4) Clamp de velocidad total por seguridad
        double speed = Math.hypot(vx, vy);
        if (speed > MAX_SPEED && speed > 0) {
            double s = MAX_SPEED / speed;
            vx *= s;
            vy *= s;
        }

        // 5) Integración en coordenadas de mundo (layoutX/layoutY)
        double nextX = view.getLayoutX() + vx * dt;
        double nextY = view.getLayoutY() + vy * dt;

        // 6) Clamping contra los límites del Pane
        double maxX = Math.max(0.0, boundsPane.getWidth()  - view.getWidth());
        double maxY = Math.max(0.0, boundsPane.getHeight() - view.getHeight());

        if (nextX < 0.0)        { nextX = 0.0; vx = 0.0; }
        else if (nextX > maxX)  { nextX = maxX; vx = 0.0; }

        if (nextY < 0.0)        { nextY = 0.0; vy = 0.0; }
        else if (nextY > maxY)  { nextY = maxY; vy = 0.0; }

        // 7) Aplicar posición final (solo layout, nunca translate)
        view.setLayoutX(nextX);
        view.setLayoutY(nextY);
    }

    private static double pickTau(double v, double tv) {
        if (tv == 0.0) return TAU_DECEL;                          // soltar → frenar suave
        if (Math.signum(v) != Math.signum(tv) && v != 0.0) return TAU_REVERSE; // invertir
        return TAU_ACCEL;                                         // acelerar
    }

    // --- GameEntity ---

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public Bounds getBounds() {
        // Bounds en coordenadas del padre (coinciden con mundo al usar layoutX/Y)
        return view.getBoundsInParent();
    }

    @Override
    public void onCollision(GameEntity other) {
        // No-op por ahora (colisiones se gestionarán más adelante)
    }

    // --- Utilidades públicas ---

    public void setPosition(double x, double y) {
        view.setLayoutX(x);
        view.setLayoutY(y);
    }

    public double getWidth()  { return view.getWidth(); }
    public double getHeight() { return view.getHeight(); }
}
