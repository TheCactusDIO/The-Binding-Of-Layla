package com.layla.model;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.AppContext;
import com.layla.core.GameEntity;
import com.layla.entities.Player;
import com.layla.entities.Projectile;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public final class Enemy implements GameEntity {

    private static final double WIDTH = 22.0;
    private static final double HEIGHT = 22.0;
    private static final double EPSILON = 1e-6;

    private final EnemyType type;
    private final Rectangle view = new Rectangle(WIDTH, HEIGHT, Color.DARKRED);
    private final Pane boundsPane;
    private final Supplier<double[]> playerCenterSupplier;
    private final Consumer<GameEntity> onRemove;
    private final Consumer<GameEntity> onSpawn;
    private final Consumer<String> playSfx;
    private final double hpMultiplier;
    private final double speedMultiplier;
    private final double damageMultiplier;

    private double hp;
    private double maxHealth;
    private double timeSinceShot = 0.0;
    private double aiTime = 0.0;
    private boolean dead = false;
    private PauseTransition hitFlashTimer;

    // Burst shooting (torretas)
    private int burstShotsRemaining = 0;
    private double burstShotTimer = 0.0;
    private double burstCooldownTimer = 0.0;

    // Buffer reutilizable para direcciones (evitar new double[] cada frame)
    private final double[] tmpDir = new double[2];
    private final double collisionRadius = Math.min(getWidth(), getHeight()) * 0.5;
    public Enemy(EnemyType type,
                 Pane boundsPane,
                 Supplier<double[]> playerCenterSupplier,
                 Consumer<GameEntity> onRemove,
                 Consumer<GameEntity> onSpawn,
                 Consumer<String> playSfx,
                 double hpMultiplier,
                 double speedMultiplier,
                 double damageMultiplier) {
        this.type = Objects.requireNonNull(type, "type");
        this.boundsPane = Objects.requireNonNull(boundsPane, "boundsPane");
        this.playerCenterSupplier = Objects.requireNonNull(playerCenterSupplier, "playerCenterSupplier");
        this.onRemove = Objects.requireNonNull(onRemove, "onRemove");
        this.onSpawn = Objects.requireNonNull(onSpawn, "onSpawn");
        this.playSfx = (playSfx != null ? playSfx : k -> {});
        this.hpMultiplier = Math.max(0.0, hpMultiplier);
        this.speedMultiplier = Math.max(0.0, speedMultiplier);
        this.damageMultiplier = Math.max(0.0, damageMultiplier);

        view.setManaged(false);
        view.setStroke(Color.BLACK);

        applyTypeStyle(); // Color por enemigo

        EnemyProfile profile = AppContext.balance().profile(type);
        this.maxHealth = profile != null ? Math.max(0.0, profile.baseHp * hpMultiplier) : 0.0;
        this.hp = this.maxHealth;
    }

    public EnemyType getType() {
        return type;
    }

    @Override
    public void update(double dt) {
        if (dead || dt <= 0.0) return;

        EnemyProfile profile = AppContext.balance().profile(type);
        if (profile == null) return;

        syncHealthWithProfile(profile);

        double[] playerCenter = playerCenterSupplier.get();
        aiTime += dt;

        handleMovement(profile, playerCenter, dt);

        if (type == EnemyType.TURRET) {
            handleTurretShooting(profile, playerCenter, dt);
        } else {
            handleDefaultShooting(profile, playerCenter, dt);
        }
    }

    private void syncHealthWithProfile(EnemyProfile profile) {
        double desiredMax = Math.max(0.0, profile.baseHp * hpMultiplier);
        if (Math.abs(desiredMax - maxHealth) > 1e-6) {
            double ratio = maxHealth > 0.0 ? hp / maxHealth : 1.0;
            maxHealth = desiredMax;
            hp = Math.min(maxHealth, Math.max(0.0, ratio * maxHealth));
            if (hp <= 0.0) {
                die();
            }
        } else if (hp > maxHealth) {
            hp = maxHealth;
        }
    }

    /**
     * Devuelve un vector dirección normalizado (target - enemy) en tmpDir o null si muy cerca.
     */
    private double[] directionTo(double[] target) {
        double cx = getCenterX();
        double cy = getCenterY();
        double dx = target[0] - cx;
        double dy = target[1] - cy;
        double len = Math.hypot(dx, dy);
        if (len < EPSILON) return null;
        tmpDir[0] = dx / len;
        tmpDir[1] = dy / len;
        return tmpDir;
    }

    private void applyJitter(double[] dir, double jitterPercent) {
        double magnitude = Math.max(0.0, Math.min(1.0, jitterPercent * 0.01));
        if (magnitude <= 0.0) return;
        double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
        double jx = Math.cos(angle) * magnitude;
        double jy = Math.sin(angle) * magnitude;
        dir[0] += jx;
        dir[1] += jy;
        double len = Math.hypot(dir[0], dir[1]);
        if (len < EPSILON) {
            dir[0] = 0.0;
            dir[1] = 1.0;
        } else {
            dir[0] /= len;
            dir[1] /= len;
        }
    }

    private void move(double[] dir, double distance) {
        if (distance <= 0.0) return;
        double nextX = view.getLayoutX() + dir[0] * distance;
        double nextY = view.getLayoutY() + dir[1] * distance;

        double maxX = Math.max(0.0, boundsPane.getWidth() - WIDTH);
        double maxY = Math.max(0.0, boundsPane.getHeight() - HEIGHT);

        view.setLayoutX(clamp(nextX, 0.0, maxX));
        view.setLayoutY(clamp(nextY, 0.0, maxY));
    }

    private void shootTowards(double[] target, EnemyProfile profile) {
        double[] dir = directionTo(target);
        if (dir == null) return;

        double projSpeed = Math.max(0.0, profile.projSpeed);
        double projRange = Math.max(0.0, profile.projRange);
        double projDamage = Math.max(0.0, profile.projDamage * damageMultiplier);
        if (projSpeed <= 0.0 || projRange <= 0.0 || projDamage <= 0.0) return;

        double lifetime = projRange / projSpeed;
        if (!Double.isFinite(lifetime) || lifetime <= 0.0) return;

        Projectile projectile = new Projectile(
                dir[0], dir[1],
                projSpeed,
                lifetime,
                projDamage,
                true,
                boundsPane,
                onRemove,
                this
        );
        projectile.getView().setLayoutX(getCenterX() - 4.0);
        projectile.getView().setLayoutY(getCenterY() - 4.0);
        onSpawn.accept(projectile);
    }

    private void applyTypeStyle() {
        // Color principal por tipo
        switch (type) {
            case SHOOTER  -> view.setFill(Color.ORANGE);
            case MELEE    -> view.setFill(Color.CRIMSON);
            case TURRET   -> view.setFill(Color.DODGERBLUE);
            case TANK     -> view.setFill(Color.DARKOLIVEGREEN);
            case KAMIKAZE -> view.setFill(Color.MAGENTA);
        }

        // Look más “pill” suave
        view.setArcWidth(6);
        view.setArcHeight(6);

        // Si es estacionario, trazo discontinuo
        EnemyProfile p = AppContext.balance().profile(type);
        view.setStrokeWidth(1.5);
        view.getStrokeDashArray().clear();
        if (p != null && p.stationary) {
            view.getStrokeDashArray().setAll(6.0, 4.0);
        }
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
        if (dead) return;

        if (other instanceof Projectile projectile && !projectile.isFromEnemy()) {
            applyDamage(projectile.getDamage());
            playSfx.accept("hit");
            return;
        }

        if (other instanceof Player player) {
            EnemyProfile profile = AppContext.balance().profile(type);
            double dmg = (profile != null ? profile.contactDmg : AppContext.balance().enemyContactDamage);
            dmg *= damageMultiplier;
            if (dmg > 0.0) {
                player.takeDamage(dmg);
                playSfx.accept("hurt");
            }
        }
    }

    public double getWidth() { return WIDTH; }
    public double getHeight() { return HEIGHT; }
    public double getHealth() { return hp; }
    public double getMaxHealth() { return maxHealth; }
    public boolean isDead() { return dead; }
    public double getCollisionRadius() { return collisionRadius; }
    public double getCenterX() { return view.getLayoutX() + getWidth() * 0.5; }
    public double getCenterY() { return view.getLayoutY() + getHeight() * 0.5; }
    public double getHpMultiplier() { return hpMultiplier; }
    public double getSpeedMultiplier() { return speedMultiplier; }
    public double getDamageMultiplier() { return damageMultiplier; }

    public void setPosition(double x, double y) {
        view.setLayoutX(x);
        view.setLayoutY(y);
    }

    public void setMaxHealth(double newMax) {
        newMax = Math.max(0.0, newMax);
        double ratio = maxHealth > 0.0 ? hp / maxHealth : 1.0;
        maxHealth = newMax;
        hp = Math.min(maxHealth, ratio * maxHealth);
    }

    public void setHealth(double newHp) {
        if (dead) return;
        hp = clamp(newHp, 0.0, maxHealth);
        if (hp <= 0.0) {
            die();
        }
    }

    public void addHealth(double delta) {
        setHealth(hp + delta);
    }

    public void applyDamage(double dmg) {
        if (dead || dmg <= 0.0) return;
        setHealth(hp - dmg);
        if (!dead) {
            flashHit();
        }
    }

    private void die() {
        if (dead) return;
        dead = true;
        hp = 0.0;
        spawnDeathFx();
        onRemove.accept(this);
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    // ===================== AI HELPERS =====================

    private boolean hasValidTarget(double[] playerCenter) {
        return playerCenter != null && playerCenter.length >= 2;
    }

    private void handleMovement(EnemyProfile profile, double[] playerCenter, double dt) {
        if (type == EnemyType.TURRET || profile.stationary || !hasValidTarget(playerCenter)) {
            return;
        }

        switch (type) {
            case MELEE    -> moveMeleeZigZag(profile, playerCenter, dt);
            case SHOOTER  -> moveShooterKiting(profile, playerCenter, dt);
            case TANK     -> moveTank(profile, playerCenter, dt);
            case KAMIKAZE -> moveKamikaze(profile, playerCenter, dt);
            default       -> moveChasingPlayer(profile, playerCenter, dt);
        }
    }

    private void moveChasingPlayer(EnemyProfile profile, double[] playerCenter, double dt) {
        double[] dir = directionTo(playerCenter);
        if (dir == null) return;
        applyJitter(dir, profile.jitter);
        move(dir, profile.speed * speedMultiplier * dt);
    }

    private void moveMeleeZigZag(EnemyProfile profile, double[] playerCenter, double dt) {
        double[] dir = directionTo(playerCenter);
        if (dir == null) return;
        double px = -dir[1];
        double py = dir[0];
        double wave = Math.sin(aiTime * 6.0);
        double sideFactor = 0.45;
        double dx = dir[0] + px * wave * sideFactor;
        double dy = dir[1] + py * wave * sideFactor;
        double len = Math.hypot(dx, dy);
        if (len < EPSILON) return;
        dir[0] = dx / len;
        dir[1] = dy / len;
        applyJitter(dir, profile.jitter * 0.5);
        move(dir, profile.speed * speedMultiplier * dt);
    }

    private void moveShooterKiting(EnemyProfile profile, double[] playerCenter, double dt) {
        double dx = playerCenter[0] - getCenterX();
        double dy = playerCenter[1] - getCenterY();
        double dist = Math.hypot(dx, dy);
        if (dist < EPSILON) return;

        double dirX = dx / dist;
        double dirY = dy / dist;

        double minRange = 140.0;
        double maxRange = 220.0;
        double speed = profile.speed * speedMultiplier;

        double moveX, moveY;

        if (dist < minRange) {
            // Huir del jugador
            moveX = -dirX;
            moveY = -dirY;
        } else if (dist > maxRange) {
            // Acercarse
            moveX = dirX;
            moveY = dirY;
        } else {
            // Zona óptima: se mueve poco, con jitter suave
            tmpDir[0] = dirX;
            tmpDir[1] = dirY;
            applyJitter(tmpDir, profile.jitter * 0.25);
            move(tmpDir, speed * dt * 0.2);
            return;
        }

        tmpDir[0] = moveX;
        tmpDir[1] = moveY;
        applyJitter(tmpDir, profile.jitter);
        move(tmpDir, speed * dt);
    }

    private void moveTank(EnemyProfile profile, double[] playerCenter, double dt) {
        double[] dir = directionTo(playerCenter);
        if (dir == null) return;
        double hpRatio = maxHealth > 0.0 ? hp / maxHealth : 1.0;
        double speed = profile.speed * speedMultiplier;
        if (hpRatio <= 0.5) {
            speed *= 1.4;
        }
        applyJitter(dir, profile.jitter);
        move(dir, speed * dt);
    }

    private void moveKamikaze(EnemyProfile profile, double[] playerCenter, double dt) {
        double dx = playerCenter[0] - getCenterX();
        double dy = playerCenter[1] - getCenterY();
        double dist = Math.hypot(dx, dy);
        if (dist < EPSILON) return;

        double dirX = dx / dist;
        double dirY = dy / dist;

        double nearDist = 120.0;
        double farDist = 260.0;
        double factor;
        if (dist <= nearDist) {
            factor = 1.6;
        } else if (dist >= farDist) {
            factor = 0.8;
        } else {
            double t = (dist - nearDist) / (farDist - nearDist);
            factor = 1.6 + (0.8 - 1.6) * t;
        }

        double speed = profile.speed * speedMultiplier * factor;
        tmpDir[0] = dirX;
        tmpDir[1] = dirY;
        applyJitter(tmpDir, profile.jitter);
        move(tmpDir, speed * dt);
    }

    private void handleDefaultShooting(EnemyProfile profile, double[] playerCenter, double dt) {
        if (profile.fireRate > 0.0 && profile.projSpeed > 0.0 && profile.projRange > 0.0) {
            timeSinceShot += dt;
            double interval = (profile.fireRate > 0.0) ? (1.0 / profile.fireRate) : Double.POSITIVE_INFINITY;
            if (timeSinceShot >= interval) {
                if (hasValidTarget(playerCenter)) {
                    timeSinceShot = 0.0;
                    shootTowards(playerCenter, profile);
                } else {
                    timeSinceShot = interval;
                }
            }
        } else {
            timeSinceShot = 0.0;
        }
    }

    private void handleTurretShooting(EnemyProfile profile, double[] playerCenter, double dt) {
        double projSpeed = Math.max(0.0, profile.projSpeed);
        double projRange = Math.max(0.0, profile.projRange);
        double projDamage = Math.max(0.0, profile.projDamage * damageMultiplier);
        if (projSpeed <= 0.0 || projRange <= 0.0 || projDamage <= 0.0) {
            burstShotsRemaining = 0;
            burstShotTimer = 0.0;
            burstCooldownTimer = 0.0;
            return;
        }

        double burstInterval = (profile.fireRate > 0.0) ? (1.0 / profile.fireRate) : Double.POSITIVE_INFINITY;
        final double perShotDelay = 0.1;
        final int burstSize = 3;

        if (burstShotsRemaining > 0) {
            burstShotTimer += dt;
            if (burstShotTimer >= perShotDelay) {
                burstShotTimer = 0.0;
                fireBurstShot(playerCenter, profile);
            }
        } else if (Double.isFinite(burstInterval)) {
            burstCooldownTimer += dt;
            if (burstCooldownTimer >= burstInterval && hasValidTarget(playerCenter)) {
                burstCooldownTimer = 0.0;
                burstShotsRemaining = burstSize;
                burstShotTimer = 0.0;
                fireBurstShot(playerCenter, profile);
            }
        }
    }

    private void fireBurstShot(double[] playerCenter, EnemyProfile profile) {
        if (burstShotsRemaining <= 0) return;
        burstShotsRemaining--;
        if (hasValidTarget(playerCenter)) {
            shootTowards(playerCenter, profile);
        }
        if (burstShotsRemaining <= 0) {
            burstShotTimer = 0.0;
        }
    }

    // ===================== FX / HIT =====================

    private void flashHit() {
        if (hitFlashTimer == null) {
            hitFlashTimer = new PauseTransition(Duration.millis(120));
            hitFlashTimer.setOnFinished(e -> {
                view.setStroke(Color.BLACK);
                try {
                    applyTypeStyle();
                } catch (Throwable ignored) {
                    if (view.getFill() instanceof Color c) {
                        view.setFill(Color.DARKRED);
                    }
                }
            });
        } else {
            hitFlashTimer.stop();
        }

        view.setStroke(Color.WHITE);
        if (view.getFill() instanceof Color c) {
            view.setFill(c.brighter());
        }
        hitFlashTimer.playFromStart();
    }

    private void spawnDeathFx() {
        Circle fx = new Circle(6, Color.ORANGERED);
        fx.setManaged(false);
        fx.setLayoutX(getCenterX());
        fx.setLayoutY(getCenterY());

        GameEntity fxEntity = new GameEntity() {
            @Override public void update(double dt) {}
            @Override public Node getView() { return fx; }
            @Override public void onCollision(GameEntity other) {}
        };

        onSpawn.accept(fxEntity);

        FadeTransition fade = new FadeTransition(Duration.millis(250), fx);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(250), fx);
        scale.setFromX(1.0);
        scale.setFromY(1.0);
        scale.setToX(1.8);
        scale.setToY(1.8);

        ParallelTransition pt = new ParallelTransition(fx, fade, scale);
        pt.setOnFinished(e -> onRemove.accept(fxEntity));
        pt.play();
    }
}
