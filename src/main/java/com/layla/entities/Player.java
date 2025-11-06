package com.layla.entities;

import java.util.Objects;
import java.util.function.Supplier;

import com.layla.core.GameEntity;
import com.layla.core.InputService;
import com.layla.model.StatType;
import com.layla.services.StatsService;

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

    private static final double TAU_ACCEL    = 0.035;  // acelera rápido
    private static final double TAU_DECEL    = 0.090;  // frena suave al soltar
    private static final double TAU_REVERSE  = 0.045;  // invertir dirección ágil

    private final Rectangle view = new Rectangle(26, 26);
    private final Supplier<double[]> moveSupplier;
    private final Pane boundsPane;
    private final StatsService statsService;

    private double vx;
    private double vy;

    // --- HEALTH ---
    private double health = 6.0;      // 3 corazones completos (2 HP = 1 corazón)
    private double maxHealth = 6.0;   // 3 corazones por defecto

    public Player(InputService input, Pane boundsPane, StatsService statsService) {
        this(Objects.requireNonNull(input, "input")::getMoveVector, boundsPane, statsService);
    }

    public Player(Supplier<double[]> moveSupplier, Pane boundsPane, StatsService statsService) {
        this.moveSupplier = Objects.requireNonNull(moveSupplier, "moveSupplier");
        this.boundsPane   = Objects.requireNonNull(boundsPane, "boundsPane");
        this.statsService = Objects.requireNonNull(statsService, "statsService");
        view.setFill(Color.RED);
        view.setStroke(Color.BLACK);
    }

    @Override
    public void update(double dt) {
        if (dt <= 0) return;

        // 1) Input (-1..1) -> velocidad objetivo
        double[] mv = moveSupplier.get();
        double maxSpeed = statsService.getStat(StatType.MOVE_SPEED);
        double targetVx = mv[0] * maxSpeed;
        double targetVy = mv[1] * maxSpeed;

        // 2) Taus por eje
        double tauX = pickTau(vx, targetVx);
        double tauY = pickTau(vy, targetVy);

        // 3) Suavizado exponencial independiente de fps
        double ax = 1.0 - Math.exp(-dt / tauX);
        double ay = 1.0 - Math.exp(-dt / tauY);
        vx += (targetVx - vx) * ax;
        vy += (targetVy - vy) * ay;

        // 4) Clamp de velocidad total
        double speed = Math.hypot(vx, vy);
        if (speed > maxSpeed && speed > 0) {
            double s = maxSpeed / speed;
            vx *= s;
            vy *= s;
        }

        // 5) Integración en mundo (layoutX/layoutY)
        double nextX = view.getLayoutX() + vx * dt;
        double nextY = view.getLayoutY() + vy * dt;

        // 6) Clamping contra límites del Pane
        double maxX = Math.max(0.0, boundsPane.getWidth()  - view.getWidth());
        double maxY = Math.max(0.0, boundsPane.getHeight() - view.getHeight());
        if (nextX < 0.0)        { nextX = 0.0; vx = 0.0; }
        else if (nextX > maxX)  { nextX = maxX; vx = 0.0; }
        if (nextY < 0.0)        { nextY = 0.0; vy = 0.0; }
        else if (nextY > maxY)  { nextY = maxY; vy = 0.0; }

        // 7) Aplicar posición final
        view.setLayoutX(nextX);
        view.setLayoutY(nextY);
    }

    private static double pickTau(double v, double tv) {
        if (tv == 0.0) return TAU_DECEL;                           // soltar → frenar suave
        if (Math.signum(v) != Math.signum(tv) && v != 0.0) return TAU_REVERSE; // invertir
        return TAU_ACCEL;                                          // acelerar
    }

    // --- GameEntity ---

    @Override
    public Node getView() {
        return view;
    }

    // Nota: si GameEntity no define getBounds(), no usar @Override aquí.
    public Bounds getBounds() {
        // Bounds en coordenadas del padre (coinciden con mundo al usar layoutX/Y)
        return view.getBoundsInParent();
    }

    @Override
    public void onCollision(GameEntity other) {
        // No-op por ahora (se gestionará más adelante)
    }

    // --- Utilidades públicas ---

    public void setPosition(double x, double y) {
        view.setLayoutX(x);
        view.setLayoutY(y);
    }

    public double getWidth()  { return view.getWidth(); }
    public double getHeight() { return view.getHeight(); }

    // ==================== HEALTH SYSTEM ====================

    public double getHealth() {
        return health;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    /** Ajusta la salud actual, con clamping [0, maxHealth]. */
    public void setHealth(double health) {
        this.health = Math.max(0.0, Math.min(health, maxHealth));
    }

    /** Aumenta o reduce la salud (ej: daño o curación). */
    public void addHealth(double delta) {
        setHealth(this.health + delta);
    }

    /** Ajusta la vida máxima. */
    public void setMaxHealth(double maxHealth) {
        this.maxHealth = Math.max(0.0, maxHealth);
        if (health > this.maxHealth) health = this.maxHealth;
    }
}
