package com.layla.entities;

import java.util.Objects;
import java.util.function.Consumer;

import com.layla.core.GameEntity;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

public final class Projectile implements GameEntity {

    private static final double RADIUS = 4.0;

    private final Circle view = new Circle(RADIUS, Color.YELLOW);
    private final Pane pane;
    private final Consumer<GameEntity> onRemove;

    private final double dirX;
    private final double dirY;
    private final double speed;
    private final double lifetime;
    private final double damage;
    private final boolean fromEnemy;
    private final GameEntity owner; // quien disparó, para ignorar autocolisión

    private double time = 0.0;

    // ---- Constructores ----
    // Antiguo (compat) → bala del jugador
    public Projectile(double dirX, double dirY,
                      double speed, double lifetime, double damage,
                      Pane pane, Consumer<GameEntity> onRemove) {
        this(dirX, dirY, speed, lifetime, damage, false, pane, onRemove, null);
    }

    // Nuevo completo
    public Projectile(double dirX, double dirY,
                      double speed, double lifetime, double damage,
                      boolean fromEnemy,
                      Pane pane, Consumer<GameEntity> onRemove,
                      GameEntity owner) {

        double len = Math.hypot(dirX, dirY);
        if (len < 1e-6) { dirX = 0; dirY = -1; len = 1; }
        this.dirX = dirX / len;
        this.dirY = dirY / len;

        this.speed = speed;
        this.lifetime = lifetime;
        this.damage = damage;
        this.fromEnemy = fromEnemy;
        this.owner = owner;

        this.pane = Objects.requireNonNull(pane, "pane");
        this.onRemove = Objects.requireNonNull(onRemove, "onRemove");

        view.setManaged(false);
        view.setStroke(Color.BLACK);
    }

    @Override public void update(double dt) {
        if (dt <= 0) return;

        // movimiento
        double nx = view.getLayoutX() + dirX * speed * dt;
        double ny = view.getLayoutY() + dirY * speed * dt;
        view.setLayoutX(nx);
        view.setLayoutY(ny);

        // lifetime
        time += dt;
        if (time >= lifetime) {
            onRemove.accept(this);
            return;
        }

        // fuera de pantalla → elimina
        if (isOutOfPaneBounds()) {
            onRemove.accept(this);
        }
    }

    @Override public Node getView() { return view; }
    @Override public Bounds getBounds() { return view.getBoundsInParent(); }

    @Override
    public void onCollision(GameEntity other) {
        // Ignora al owner (quien disparó)
        if (owner != null && other == owner) return;

        // Bala del jugador golpea a Enemy → hace daño y se elimina
        if (!fromEnemy && other instanceof Enemy e) {
            e.onCollision(this); // para que Enemy aplique daño si no lo hace ya
            onRemove.accept(this);
            return;
        }
        // Bala enemiga golpea a Player → daño y se elimina
        if (fromEnemy && other instanceof Player p) {
            p.takeDamage(damage);
            onRemove.accept(this);
            return;
        }
        // (Si añades obstáculos, elimínala aquí al colisionar con ellos)
    }

    public double getDamage() { return damage; }
    public boolean isFromEnemy() { return fromEnemy; }
    public double getSpeed() { return speed; }

    private boolean isOutOfPaneBounds() {
        double x = view.getLayoutX();
        double y = view.getLayoutY();
        return (x < -RADIUS || y < -RADIUS ||
                x > pane.getWidth() + RADIUS ||
                y > pane.getHeight() + RADIUS);
    }
}
