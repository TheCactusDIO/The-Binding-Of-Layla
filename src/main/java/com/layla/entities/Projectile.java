package com.layla.entities;

import java.util.function.Consumer;

import com.layla.core.GameEntity;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Proyectil simple con "bando".
 * - fromEnemy=false  -> bala del jugador
 * - fromEnemy=true   -> bala del enemigo
 *
 * El daño real lo aplican Player/Enemy en sus onCollision, usando isFromEnemy().
 */
public final class Projectile implements GameEntity {

    private final double dx, dy;       // dirección normalizada
    private final double speed;        // px/s
    private final double lifetime;     // s
    private final double damage;
    private double elapsed = 0.0;

    private final Pane boundsPane;
    private final Consumer<GameEntity> onRemove;
    private final Circle view;

    private final boolean fromEnemy;   // bando

    // Constructor por defecto: bala del jugador (fromEnemy = false)
    public Projectile(double dx, double dy, double speed, double lifetimeSeconds,
                      double damage, Pane boundsPane, Consumer<GameEntity> onRemove) {
        this(dx, dy, speed, lifetimeSeconds, damage, boundsPane, onRemove, false);
    }

    // Constructor con bando explícito
    public Projectile(double dx, double dy, double speed, double lifetimeSeconds,
                      double damage, Pane boundsPane, Consumer<GameEntity> onRemove,
                      boolean fromEnemy) {

        double len = Math.hypot(dx, dy);
        if (len == 0) { dx = 0; dy = -1; len = 1; }
        this.dx = dx / len;
        this.dy = dy / len;

        this.speed = speed;
        this.lifetime = lifetimeSeconds;
        this.damage = damage;
        this.boundsPane = boundsPane;
        this.onRemove = onRemove;
        this.fromEnemy = fromEnemy;

        this.view = new Circle(4);
        this.view.setFill(Color.WHITE);
        this.view.setStroke(Color.BLACK);
        this.view.setManaged(false);
    }

    @Override
    public void update(double dt) {
        elapsed += dt;

        view.setLayoutX(view.getLayoutX() + dx * speed * dt);
        view.setLayoutY(view.getLayoutY() + dy * speed * dt);

        if (elapsed >= lifetime || isOutOfPaneBounds()) {
            onRemove.accept(this);
        }
    }

    @Override
    public Node getView() { return view; }

    @Override
    public void onCollision(GameEntity other) {
        // No me destruyo al tocar al "aliado"
        if (!fromEnemy && other instanceof Player) return; // bala del jugador tocando al jugador
        if ( fromEnemy && other instanceof Enemy)  return; // bala enemiga tocando a un enemigo

        onRemove.accept(this);
    }

    public double getDamage() { return damage; }
    public boolean isFromEnemy() { return fromEnemy; }

    private boolean isOutOfPaneBounds() {
        double x = view.getLayoutX();
        double y = view.getLayoutY();
        double r = view.getRadius();
        double w = boundsPane.getWidth();
        double h = boundsPane.getHeight();
        return (x + r < 0) || (y + r < 0) || (x - r > w) || (y - r > h);
    }
}
