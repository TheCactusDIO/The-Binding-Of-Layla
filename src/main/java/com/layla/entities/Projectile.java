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

/**
 * Proyectil genérico del juego.
 *
 * Soporta:
 * - Pierce (atraviesa X objetivos antes de destruirse)
 * - Bounce (rebota contra bordes del Pane un nº limitado de veces)
 * - Homing (solo para proyectiles NO enemigos)
 *
 * Nota importante de robustez:
 * Este objeto NO debe eliminarse directamente de la lista que itera el GameLoop.
 * En su lugar llama a onRemove(this), y el GameLoop debería encolar la eliminación.
 */
public final class Projectile implements GameEntity {

    private static final double RADIUS = 5.0;

    private static final double HOMING_RANGE = 450.0;
    private static final double HOMING_TURN_SPEED = 5.0;

    private final Circle view = new Circle(RADIUS, Color.YELLOW);

    private final Pane pane;
    private final Consumer<GameEntity> onRemove;

    private final double speed;
    private final double lifetime;
    private final double damage;

    private final boolean fromEnemy;
    private final GameEntity owner;
    private final String sourceName;

    private double dirX;
    private double dirY;

    private int pierceRemaining;
    private int bounceRemaining;

    private final boolean homing;
    private final Supplier<List<GameEntity>> targetSupplier;

    /** Evita golpear al mismo target múltiples veces (especialmente con pierce). */
    private final Set<GameEntity> alreadyHit = new HashSet<>();

    private double timeAlive = 0.0;

    /** Flag idempotente para evitar dobles onRemove(). */
    private boolean removed = false;

    public Projectile(
            double dirX,
            double dirY,
            double speed,
            double lifetime,
            double damage,
            boolean fromEnemy,
            Pane pane,
            Consumer<GameEntity> onRemove,
            GameEntity owner,
            String sourceName,
            int pierce,
            int bounce,
            boolean homing,
            Supplier<List<GameEntity>> targetSupplier
    ) {
        this.pane = Objects.requireNonNull(pane, "pane");
        this.onRemove = Objects.requireNonNull(onRemove, "onRemove");

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

        setDirectionNormalized(dirX, dirY);

        view.setManaged(false);
        view.setStroke(Color.BLACK);
        view.setStrokeWidth(1.5);

        updateColor();
    }

    // Constructores de conveniencia
    public Projectile(
            double dirX, double dirY,
            double speed, double lifetime, double damage,
            boolean fromEnemy,
            Pane pane, Consumer<GameEntity> onRemove,
            GameEntity owner, String sourceName
    ) {
        this(dirX, dirY, speed, lifetime, damage, fromEnemy, pane, onRemove, owner, sourceName, 0, 0, false, null);
    }

    public Projectile(
            double dirX, double dirY,
            double speed, double lifetime, double damage,
            boolean fromEnemy,
            Pane pane, Consumer<GameEntity> onRemove,
            GameEntity owner, String sourceName,
            int pierce, int bounce
    ) {
        this(dirX, dirY, speed, lifetime, damage, fromEnemy, pane, onRemove, owner, sourceName, pierce, bounce, false, null);
    }

    /**
     * Update por frame:
     * - Homing (si aplica)
     * - Movimiento + rebotes en bordes
     * - Caducidad por lifetime
     * - Destrucción si sale fuera (sin rebote)
     */
    @Override
    public void update(double dt) {
        if (removed || dt <= 0.0) return;

        if (homing && !fromEnemy && targetSupplier != null) {
            updateHoming(dt);
        }

        final double prevX = view.getLayoutX();
        final double prevY = view.getLayoutY();

        double nextX = prevX + dirX * speed * dt;
        double nextY = prevY + dirY * speed * dt;

        boolean bounced = false;

        // Si el pane aún no está “layouted”, getWidth/getHeight pueden ser 0.
        // Evitamos lógica rara de rebote en ese caso.
        final double width = pane.getWidth();
        final double height = pane.getHeight();

        if (bounceRemaining > 0 && width > RADIUS * 2 && height > RADIUS * 2) {
            double minX = RADIUS;
            double minY = RADIUS;
            double maxX = width - RADIUS;
            double maxY = height - RADIUS;

            if (nextX <= minX) { nextX = minX; dirX = -dirX; bounced = true; }
            else if (nextX >= maxX) { nextX = maxX; dirX = -dirX; bounced = true; }

            if (nextY <= minY) { nextY = minY; dirY = -dirY; bounced = true; }
            else if (nextY >= maxY) { nextY = maxY; dirY = -dirY; bounced = true; }

            if (bounced) {
                bounceRemaining--;
                // Si quieres, aquí podrías actualizar color cuando se consumen bounces/pierce.
                // updateColor();
            }
        }

        view.setLayoutX(nextX);
        view.setLayoutY(nextY);

        timeAlive += dt;
        if (timeAlive >= lifetime) {
            requestRemove();
            return;
        }

        // Si no rebotó y se fue fuera de bounds, se elimina.
        if (!bounced && width > 0 && height > 0 && isOutOfPaneBounds(width, height)) {
            requestRemove();
        }
    }

    /**
     * Ajusta dirección de forma segura (si llega un vector casi 0, usa hacia arriba).
     */
    private void setDirectionNormalized(double x, double y) {
        double len = Math.hypot(x, y);
        if (len < 1e-6) {
            x = 0;
            y = -1;
            len = 1;
        }
        this.dirX = x / len;
        this.dirY = y / len;
    }

    /**
     * Homing hacia el target más cercano dentro de rango.
     * Gira con velocidad limitada para no “snapear” instantáneo.
     */
    private void updateHoming(double dt) {
        List<GameEntity> targets;
        try {
            targets = targetSupplier.get();
        } catch (Exception ignore) {
            return;
        }
        if (targets == null || targets.isEmpty()) return;

        final double px = view.getLayoutX();
        final double py = view.getLayoutY();

        GameEntity closest = null;
        double closestDistSq = HOMING_RANGE * HOMING_RANGE;

        for (GameEntity e : targets) {
            if (e == null) continue;

            boolean isDead = false;
            if (e instanceof Enemy en) isDead = en.isDead();
            else if (e instanceof Boss b) isDead = b.isDead();
            if (isDead) continue;

            Bounds b = e.getBounds();
            double dx = b.getCenterX() - px;
            double dy = b.getCenterY() - py;

            double distSq = dx * dx + dy * dy;
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = e;
            }
        }

        if (closest == null) return;

        Bounds cb = closest.getBounds();
        double tx = cb.getCenterX() - px;
        double ty = cb.getCenterY() - py;

        double currentAngle = Math.atan2(dirY, dirX);
        double targetAngle = Math.atan2(ty, tx);

        double diff = targetAngle - currentAngle;
        while (diff <= -Math.PI) diff += 2 * Math.PI;
        while (diff > Math.PI) diff -= 2 * Math.PI;

        double turn = HOMING_TURN_SPEED * dt;
        if (Math.abs(diff) <= turn) {
            currentAngle = targetAngle;
        } else {
            currentAngle += (diff > 0) ? turn : -turn;
        }

        dirX = Math.cos(currentAngle);
        dirY = Math.sin(currentAngle);
    }

    /**
     * Manejo de colisiones:
     * - Ignora colisión con el propio owner
     * - Evita repetir daño al mismo objetivo
     * - Aplica daño según tipo
     */
    @Override
    public void onCollision(GameEntity other) {
        if (removed || other == null) return;
        if (owner != null && other == owner) return;
        if (alreadyHit.contains(other)) return;

        boolean hit = false;
        boolean forceDestroy = false;

        if (!fromEnemy && other instanceof Enemy e) {
            if (!e.isDead()) {
                e.applyDamage(damage);
                hit = true;
            }
        } else if (!fromEnemy && other instanceof Boss b) {
            if (!b.isDead()) {
                b.takeDamage(damage);
                hit = true;
            }
        } else if (fromEnemy && other instanceof Player p) {
            if (sourceName != null) p.setLastHitSource(sourceName);
            p.takeDamage(damage);
            hit = true;
        } else if (other instanceof Rock) {
            // Por ahora las rocas absorben disparos (sin rebote físico en roca).
            hit = true;
            forceDestroy = true;
        }

        if (!hit) return;

        alreadyHit.add(other);

        if (forceDestroy) {
            requestRemove();
            return;
        }

        if (pierceRemaining > 0) {
            pierceRemaining--;
            // Si quieres que el color refleje pierce restante:
            // updateColor();
        } else {
            requestRemove();
        }
    }

    /**
     * Solicita eliminación del proyectil (idempotente).
     * No elimina directamente de listas: delega al GameLoop mediante onRemove.
     */
    private void requestRemove() {
        if (removed) return;
        removed = true;
        onRemove.accept(this);
    }

    @Override public Node getView() { return view; }
    @Override public Bounds getBounds() { return view.getBoundsInParent(); }

    public double getDamage() { return damage; }
    public boolean isFromEnemy() { return fromEnemy; }
    public double getSpeed() { return speed; }

    /**
     * Comprueba si está claramente fuera del área visible del pane.
     */
    private boolean isOutOfPaneBounds(double width, double height) {
        double x = view.getLayoutX();
        double y = view.getLayoutY();

        return (x < -RADIUS * 2 || y < -RADIUS * 2 ||
                x > width + RADIUS * 2 ||
                y > height + RADIUS * 2);
    }

    /**
     * Color según tipo de proyectil (enemigo, homing, pierce/bounce...).
     * Solo se llama al crear (si quieres, puedes llamarlo cuando cambien pierce/bounce).
     */
    private void updateColor() {
        if (fromEnemy) {
            view.setFill(Color.ORANGERED);
            return;
        }

        if (homing) view.setFill(Color.PURPLE);
        else if (bounceRemaining > 0 && pierceRemaining > 0) view.setFill(Color.MAGENTA);
        else if (bounceRemaining > 0) view.setFill(Color.CYAN);
        else if (pierceRemaining > 0) view.setFill(Color.LIGHTGREEN);
        else view.setFill(Color.GOLD);
    }
}
