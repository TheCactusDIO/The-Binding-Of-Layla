package com.layla.entities;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.core.GameEntity;
import com.layla.core.InputService;
import com.layla.model.PlayerStatId;
import com.layla.services.StatsService;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public final class Player implements GameEntity {

    private static final double TAU_ACCEL    = 0.035;
    private static final double TAU_DECEL    = 0.090;
    private static final double TAU_REVERSE  = 0.045;

    private final Rectangle view = new Rectangle(26, 26);
    private final Supplier<double[]> moveSupplier;
    private final Pane boundsPane;
    private final StatsService statsService;

    private double vx;
    private double vy;

    private double health = 6.0;
    private double maxHealth = 6.0;
    private boolean dead = false;

    private double invulnTimer = 0.0;
    private static final double INVULN_DURATION = 0.6;

    private final Consumer<String> playSfx;

    // NUEVO: Rastreo de la fuente de daño
    private String lastHitSource = null;

    public Player(Supplier<double[]> moveSupplier,
                  Pane boundsPane,
                  StatsService statsService,
                  Consumer<String> playSfx) {
        this.moveSupplier = Objects.requireNonNull(moveSupplier, "moveSupplier");
        this.boundsPane   = Objects.requireNonNull(boundsPane, "boundsPane");
        this.statsService = (statsService != null) ? statsService : com.layla.AppContext.stats();
        this.playSfx      = (playSfx != null ? playSfx : k -> {});
        this.invulnTimer = 1.0;
        view.setFill(Color.RED);
        view.setStroke(Color.BLACK);
    }

    // Constructor de conveniencia
    public Player(InputService input, Pane boundsPane, StatsService statsService) {
        this(Objects.requireNonNull(input, "input")::getMoveVector, boundsPane, statsService, null);
    }

    // NUEVO: Getter y Setter para la fuente de daño
    public void setLastHitSource(String source) {
        this.lastHitSource = source;
    }

    public String getLastHitSource() {
        return lastHitSource;
    }

    @Override
    public void update(double dt) {
        if (dt <= 0 || dead) return;

        if (invulnTimer > 0.0) invulnTimer = Math.max(0.0, invulnTimer - dt);

        syncMaxHealthFromStats();

        double[] mv = moveSupplier.get();
        double maxSpeed = statsService.getStat(PlayerStatId.MOVE_SPEED);
        double targetVx = mv[0] * maxSpeed;
        double targetVy = mv[1] * maxSpeed;

        double tauX = pickTau(vx, targetVx);
        double tauY = pickTau(vy, targetVy);

        double ax = 1.0 - Math.exp(-dt / tauX);
        double ay = 1.0 - Math.exp(-dt / tauY);
        vx += (targetVx - vx) * ax;
        vy += (targetVy - vy) * ay;

        double speed = Math.hypot(vx, vy);
        if (speed > maxSpeed && speed > 0) {
            double s = maxSpeed / speed;
            vx *= s;
            vy *= s;
        }

        double nextX = view.getLayoutX() + vx * dt;
        double nextY = view.getLayoutY() + vy * dt;

        double maxX = Math.max(0.0, boundsPane.getWidth()  - view.getWidth());
        double maxY = Math.max(0.0, boundsPane.getHeight() - view.getHeight());
        if (nextX < 0.0)        { nextX = 0.0; vx = 0.0; }
        else if (nextX > maxX)  { nextX = maxX; vx = 0.0; }
        if (nextY < 0.0)        { nextY = 0.0; vy = 0.0; }
        else if (nextY > maxY)  { nextY = maxY; vy = 0.0; }

        view.setLayoutX(nextX);
        view.setLayoutY(nextY);
    }

    private static double pickTau(double v, double tv) {
        if (tv == 0.0) return TAU_DECEL;
        if (Math.signum(v) != Math.signum(tv) && v != 0.0) return TAU_REVERSE;
        return TAU_ACCEL;
    }

    @Override
    public Node getView() {
        return view;
    }

    public Bounds getBounds() {
        return view.getBoundsInParent();
    }

    @Override
    public void onCollision(GameEntity other) {
        if (dead) return;
        // Collision logic se maneja principalmente en Projectile o GameController para enemigos
    }

    public void setPosition(double x, double y) {
        view.setLayoutX(x);
        view.setLayoutY(y);
    }

    public double getWidth()  { return view.getWidth(); }
    public double getHeight() { return view.getHeight(); }

    public double getHealth() { return health; }
    public double getMaxHealth() { return maxHealth; }
    public boolean isDead() { return dead; }

    public void setHealth(double health) {
        this.health = Math.max(0.0, Math.min(health, maxHealth));
        if (this.health <= 0.0) die();
    }

    public void addHealth(double delta) {
        setHealth(this.health + delta);
    }

    public void setMaxHealth(double maxHealth) {
        this.maxHealth = Math.max(0.0, maxHealth);
        if (health > this.maxHealth) health = this.maxHealth;
        if (health <= 0.0 && !dead) die();
    }

    public void takeDamage(double amount) {
        if (dead || amount <= 0.0) return;
        if (invulnTimer > 0.0) return;

        health = Math.max(0.0, health - amount);
        if (health <= 0.0) {
            die();
        } else {
            invulnTimer = INVULN_DURATION;
            playSfx.accept("hurt");
        }
    }

    private void die() {
        if (dead) return;
        dead = true;
        health = 0.0;
        playSfx.accept("dead");
    }

    private void syncMaxHealthFromStats() {
        double desiredMax = statsService.getStat(PlayerStatId.MAX_HEALTH);
        if (Math.abs(desiredMax - maxHealth) > 1e-6) {
            setMaxHealth(desiredMax);
        }
    }
}
