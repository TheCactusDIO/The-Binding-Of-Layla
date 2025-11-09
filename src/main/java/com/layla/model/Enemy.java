package com.layla.model;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.AppContext;
import com.layla.core.GameEntity;
import com.layla.entities.Player;
import com.layla.entities.Projectile;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

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

    private double hp;
    private double maxHealth;
    private double timeSinceShot = 0.0;
    private boolean dead = false;

    public Enemy(EnemyType type,
                 Pane boundsPane,
                 Supplier<double[]> playerCenterSupplier,
                 Consumer<GameEntity> onRemove,
                 Consumer<GameEntity> onSpawn,
                 Consumer<String> playSfx) {
        this.type = Objects.requireNonNull(type, "type");
        this.boundsPane = Objects.requireNonNull(boundsPane, "boundsPane");
        this.playerCenterSupplier = Objects.requireNonNull(playerCenterSupplier, "playerCenterSupplier");
        this.onRemove = Objects.requireNonNull(onRemove, "onRemove");
        this.onSpawn = Objects.requireNonNull(onSpawn, "onSpawn");
        this.playSfx = (playSfx != null ? playSfx : k -> {});

        view.setManaged(false);
        view.setStroke(Color.BLACK);

        EnemyProfile profile = AppContext.balance().profile(type);
        this.maxHealth = profile != null ? Math.max(0.0, profile.baseHp) : 0.0;
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
        if (!profile.stationary && playerCenter != null && playerCenter.length >= 2) {
            double[] dir = directionTo(playerCenter);
            if (dir != null) {
                applyJitter(dir, profile.jitter);
                move(dir, profile.speed * dt);
            }
        }

        if (profile.fireRate > 0.0 && profile.projSpeed > 0.0 && profile.projRange > 0.0) {
            timeSinceShot += dt;
            double interval = (profile.fireRate > 0.0) ? (1.0 / profile.fireRate) : Double.POSITIVE_INFINITY;
            if (timeSinceShot >= interval) {
                if (playerCenter != null && playerCenter.length >= 2) {
                    timeSinceShot = 0.0;
                    shootTowards(playerCenter, profile);
                } else {
                    timeSinceShot = interval; // wait for a valid target
                }
            }
        } else {
            timeSinceShot = 0.0;
        }
    }

    private void syncHealthWithProfile(EnemyProfile profile) {
        double desiredMax = Math.max(0.0, profile.baseHp);
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

    private double[] directionTo(double[] target) {
        double cx = getCenterX();
        double cy = getCenterY();
        double dx = target[0] - cx;
        double dy = target[1] - cy;
        double len = Math.hypot(dx, dy);
        if (len < EPSILON) return null;
        return new double[] { dx / len, dy / len };
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
        double projDamage = Math.max(0.0, profile.projDamage);
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
    }

    private void die() {
        if (dead) return;
        dead = true;
        hp = 0.0;
        onRemove.accept(this);
    }

    private double getCenterX() {
        return view.getLayoutX() + WIDTH * 0.5;
    }

    private double getCenterY() {
        return view.getLayoutY() + HEIGHT * 0.5;
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
