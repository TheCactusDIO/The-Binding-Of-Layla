package com.layla.entities;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.core.GameEntity;
import com.layla.model.Enemy;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

public final class Projectile implements GameEntity {

    private static final double RADIUS = 5.0;

    private final Circle view = new Circle(RADIUS, Color.YELLOW);
    private final Pane pane;
    private final Consumer<GameEntity> onRemove;

    private double dirX;
    private double dirY;

    private final double speed;
    private final double lifetime;
    private final double damage;
    private final boolean fromEnemy;
    private final GameEntity owner;
    private final String sourceName;

    // Propiedades de comportamiento
    private int pierceRemaining;
    private int bounceRemaining;
    private final boolean homing;
    // FIX: Ahora es GameEntity para incluir al Boss
    private final Supplier<List<GameEntity>> targetSupplier;
    private final Set<GameEntity> hitWhitelist = new HashSet<>();

    private double time = 0.0;
    private static final double HOMING_RANGE = 450.0; // Aumentado ligeramente
    private static final double HOMING_TURN_SPEED = 5.0;

    public Projectile(double dirX, double dirY,
                      double speed, double lifetime, double damage,
                      boolean fromEnemy,
                      Pane pane, Consumer<GameEntity> onRemove,
                      GameEntity owner,
                      String sourceName,
                      int pierce, int bounce, boolean homing,
                      Supplier<List<GameEntity>> targetSupplier) {

        double len = Math.hypot(dirX, dirY);
        if (len < 1e-6) { dirX = 0; dirY = -1; len = 1; }
        this.dirX = dirX / len;
        this.dirY = dirY / len;

        this.speed = speed;
        this.lifetime = lifetime;
        this.damage = damage;
        this.fromEnemy = fromEnemy;
        this.owner = owner;
        this.sourceName = sourceName;
        this.pierceRemaining = pierce;
        this.bounceRemaining = bounce;
        this.homing = homing;
        this.targetSupplier = targetSupplier;

        this.pane = Objects.requireNonNull(pane, "pane");
        this.onRemove = Objects.requireNonNull(onRemove, "onRemove");

        view.setManaged(false);
        view.setStroke(Color.BLACK);
        view.setStrokeWidth(1.5);

        updateColor();
    }

    private void updateColor() {
        if (fromEnemy) {
            view.setFill(Color.ORANGERED);
        } else {
            if (homing) view.setFill(Color.PURPLE);
            else if (bounceRemaining > 0 && pierceRemaining > 0) view.setFill(Color.MAGENTA);
            else if (bounceRemaining > 0) view.setFill(Color.CYAN);
            else if (pierceRemaining > 0) view.setFill(Color.LIGHTGREEN);
            else view.setFill(Color.GOLD);
        }
    }

    public Projectile(double dirX, double dirY, double speed, double lifetime, double damage,
                      boolean fromEnemy, Pane pane, Consumer<GameEntity> onRemove,
                      GameEntity owner, String sourceName) {
        this(dirX, dirY, speed, lifetime, damage, fromEnemy, pane, onRemove, owner, sourceName, 0, 0, false, null);
    }

    public Projectile(double dirX, double dirY, double speed, double lifetime, double damage,
            boolean fromEnemy, Pane pane, Consumer<GameEntity> onRemove,
            GameEntity owner, String sourceName, int pierce, int bounce) {
    this(dirX, dirY, speed, lifetime, damage, fromEnemy, pane, onRemove, owner, sourceName, pierce, bounce, false, null);
    }

    @Override public void update(double dt) {
        if (dt <= 0) return;

        if (homing && !fromEnemy && targetSupplier != null) {
            updateHoming(dt);
        }

        double prevX = view.getLayoutX();
        double prevY = view.getLayoutY();

        double nextX = prevX + dirX * speed * dt;
        double nextY = prevY + dirY * speed * dt;

        double minX = RADIUS;
        double minY = RADIUS;
        double maxX = pane.getWidth() - RADIUS;
        double maxY = pane.getHeight() - RADIUS;

        boolean bounced = false;

        if (bounceRemaining > 0) {
            if (nextX <= minX) { nextX = minX; dirX = -dirX; bounced = true; }
            else if (nextX >= maxX) { nextX = maxX; dirX = -dirX; bounced = true; }

            if (nextY <= minY) { nextY = minY; dirY = -dirY; bounced = true; }
            else if (nextY >= maxY) { nextY = maxY; dirY = -dirY; bounced = true; }

            if (bounced) bounceRemaining--;
        }

        view.setLayoutX(nextX);
        view.setLayoutY(nextY);

        time += dt;
        if (time >= lifetime) {
            onRemove.accept(this);
            return;
        }

        if (!bounced && isOutOfPaneBounds()) {
            onRemove.accept(this);
        }
    }

    private void updateHoming(double dt) {
        List<GameEntity> targets = targetSupplier.get();
        if (targets == null || targets.isEmpty()) return;

        GameEntity closest = null;
        double closestDistSq = HOMING_RANGE * HOMING_RANGE;
        double px = view.getLayoutX();
        double py = view.getLayoutY();

        for (GameEntity e : targets) {
            boolean isDead = false;
            // FIX: Comprobar muerte según tipo
            if (e instanceof Enemy en) isDead = en.isDead();
            else if (e instanceof Boss b) isDead = b.isDead();

            if (isDead) continue;

            double dx = e.getBounds().getCenterX() - px;
            double dy = e.getBounds().getCenterY() - py;
            double distSq = dx*dx + dy*dy;
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = e;
            }
        }

        if (closest != null) {
            double dx = closest.getBounds().getCenterX() - px;
            double dy = closest.getBounds().getCenterY() - py;
            double currentAngle = Math.atan2(dirY, dirX);
            double targetAngle = Math.atan2(dy, dx);

            double diff = targetAngle - currentAngle;
            while (diff <= -Math.PI) diff += 2*Math.PI;
            while (diff > Math.PI) diff -= 2*Math.PI;

            double turn = HOMING_TURN_SPEED * dt;
            if (Math.abs(diff) < turn) currentAngle = targetAngle;
            else if (diff > 0) currentAngle += turn;
            else currentAngle -= turn;

            dirX = Math.cos(currentAngle);
            dirY = Math.sin(currentAngle);
        }
    }

    @Override public Node getView() { return view; }
    @Override public Bounds getBounds() { return view.getBoundsInParent(); }

    @Override
    public void onCollision(GameEntity other) {
        if (owner != null && other == owner) return;
        if (hitWhitelist.contains(other)) return;

        boolean hit = false;

        if (!fromEnemy && other instanceof com.layla.model.Enemy e) {
            if (!e.isDead()) {
                e.applyDamage(damage);
                hit = true;
            }
        }
        else if (!fromEnemy && other instanceof com.layla.entities.Boss b) {
            if (!b.isDead()) {
                b.takeDamage(damage);
                hit = true;
            }
        }
        else if (fromEnemy && other instanceof Player p) {
            if (sourceName != null) p.setLastHitSource(sourceName);
            p.takeDamage(damage);
            hit = true;
        }

        if (hit) {
            hitWhitelist.add(other);
            if (pierceRemaining > 0) {
                pierceRemaining--;
            } else {
                onRemove.accept(this);
            }
        }
    }

    public double getDamage() { return damage; }
    public boolean isFromEnemy() { return fromEnemy; }
    public double getSpeed() { return speed; }

    private boolean isOutOfPaneBounds() {
        double x = view.getLayoutX();
        double y = view.getLayoutY();
        return (x < -RADIUS * 2 || y < -RADIUS * 2 ||
                x > pane.getWidth() + RADIUS * 2 ||
                y > pane.getHeight() + RADIUS * 2);
    }
}
