package com.layla.entities;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.core.GameEntity;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Enemigo básico: persigue al jugador y dispara hacia él.
 * - Velocidad, vida y proyectiles leen de AppContext.balance() en runtime.
 * - Contacto con Player hace daño (Player gestiona i-frames).
 */
public final class Enemy implements GameEntity {

    private static final double WIDTH = 22.0;
    private static final double HEIGHT = 22.0;
    private static final double EPSILON = 1e-6;

    private final Rectangle view = new Rectangle(WIDTH, HEIGHT, Color.DARKRED);
    private final Pane boundsPane;
    private final Supplier<double[]> playerCenterSupplier;
    /** Factor per-instance para variar la velocidad relativa al valor global. */
    private final double speedFactor;
    private final Consumer<GameEntity> onRemove;
    /** Para añadir entidades (p.ej. proyectiles enemigos) al loop. */
    private final Consumer<GameEntity> onSpawn;
    /** SFX opcional. */
    private final Consumer<String> playSfx;

    // Salud (dinámica)
    private double maxHealth;
    private double health;
    private boolean dead = false;

    // Disparo enemigo (temporizador en segundos)
    private double shootTimer = 0.0;

    public Enemy(Pane boundsPane,
                 Supplier<double[]> playerCenterSupplier,
                 double speedFactor,
                 double startHp,
                 Consumer<GameEntity> onRemove,
                 Consumer<GameEntity> onSpawn,
                 Consumer<String> playSfx) {
        this.boundsPane = Objects.requireNonNull(boundsPane, "boundsPane");
        this.playerCenterSupplier = Objects.requireNonNull(playerCenterSupplier, "playerCenterSupplier");
        this.speedFactor = speedFactor;
        this.maxHealth = Math.max(0.0, startHp);
        this.health = this.maxHealth;
        this.onRemove = Objects.requireNonNull(onRemove, "onRemove");
        this.onSpawn = Objects.requireNonNull(onSpawn, "onSpawn");
        this.playSfx = (playSfx != null ? playSfx : k -> {});

        view.setStroke(Color.BLACK);
        view.setManaged(false);
        resetShootTimer(); // primer valor
    }

    @Override
    public void update(double dt) {
        if (dead || dt <= 0.0) return;

        double desiredMax = com.layla.AppContext.balance().enemyBaseHp;
            if (Math.abs(desiredMax - maxHealth) > 1e-9) {
                double ratio = (maxHealth > 0.0) ? (health / maxHealth) : 1.0;
                maxHealth = Math.max(0.0, desiredMax);
                setHealth(ratio * maxHealth); // clampa + muerte si toca
            }

        // --- Movimiento con velocidad global dinámica ---
        double baseSpeed = com.layla.AppContext.balance().enemySpeedAvg;
        double speed = baseSpeed * speedFactor;

        double[] playerCenter = playerCenterSupplier.get();
        if (playerCenter == null || playerCenter.length < 2) return;

        double enemyCenterX = view.getLayoutX() + WIDTH * 0.5;
        double enemyCenterY = view.getLayoutY() + HEIGHT * 0.5;

        double dirX = playerCenter[0] - enemyCenterX;
        double dirY = playerCenter[1] - enemyCenterY;
        double len  = Math.hypot(dirX, dirY);
        if (len < EPSILON) return;

        dirX /= len;
        dirY /= len;

        double nextX = view.getLayoutX() + dirX * speed * dt;
        double nextY = view.getLayoutY() + dirY * speed * dt;

        double maxX = Math.max(0.0, boundsPane.getWidth()  - WIDTH);
        double maxY = Math.max(0.0, boundsPane.getHeight() - HEIGHT);

        nextX = clamp(nextX, 0.0, maxX);
        nextY = clamp(nextY, 0.0, maxY);

        view.setLayoutX(nextX);
        view.setLayoutY(nextY);

        // --- Disparo enemigo (cadencia dinámica con jitter) ---
        shootTimer -= dt;
        if (shootTimer <= 0.0) {
            shootAtPlayer();
            resetShootTimer(); // recomputa con enemyFireRate actual
        }
    }

    @Override public Node getView() { return view; }
    @Override public Bounds getBounds() { return view.getBoundsInParent(); }

    @Override
    public void onCollision(GameEntity other) {
        if (dead) return;

        if (other instanceof Projectile projectile) {
            // Recibe daño solo de balas del jugador
            if (!projectile.isFromEnemy()) {
                takeDamage(projectile.getDamage());
                playSfx.accept("hit");
            }
            return;
        }

        if (other instanceof Player p) {
            double dmg = com.layla.AppContext.balance().enemyContactDamage;
            if (dmg > 0) {
                p.takeDamage(dmg);
                playSfx.accept("hurt");
            }
        }
    }

    private void shootAtPlayer() {
        var bal = com.layla.AppContext.balance();

        double ox = view.getLayoutX() + WIDTH  * 0.5;
        double oy = view.getLayoutY() + HEIGHT * 0.5;

        double[] pc = playerCenterSupplier.get();
        if (pc == null || pc.length < 2) return;

        double dx = pc[0] - ox;
        double dy = pc[1] - oy;
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) { dx = 0; dy = 1; len = 1; }
        dx /= len; dy /= len;

        // Proyectil enemigo (fromEnemy=true, owner=this)
        Projectile p = new Projectile(
                dx, dy,
                bal.enemyProjSpeed,
                bal.enemyProjRange,
                bal.enemyProjDamage,
                true,
                boundsPane,
                onRemove,
                this
        );
        p.getView().setLayoutX(ox - 4.0);
        p.getView().setLayoutY(oy - 4.0);

        onSpawn.accept(p);
    }

    /** Calcula el siguiente intervalo (segundos) en base a enemyFireRate y jitter. */
    private double nextFireIntervalSeconds() {
        var bal = com.layla.AppContext.balance();
        double rate = Math.max(0.0, bal.enemyFireRate); // disparos/s
        if (rate <= 0.0) return Double.POSITIVE_INFINITY; // desactivar disparo si 0
        double base = 1.0 / rate; // segundos por disparo
        double jitter = Math.max(0.0, Math.min(0.95, bal.enemyFireJitter)); // [0..0.95]
        double min = base * (1.0 - jitter);
        double max = base * (1.0 + jitter);
        return min + Math.random() * Math.max(0.0, max - min);
    }

    private void resetShootTimer() {
        shootTimer = nextFireIntervalSeconds();
    }

    // ---------- Salud ----------
    public void setMaxHealth(double newMax) {
        newMax = Math.max(0.0, newMax);
        if (Math.abs(newMax - maxHealth) < 1e-9) return;
        double ratio = (maxHealth > 0.0) ? (health / maxHealth) : 1.0;
        maxHealth = newMax;
        setHealth(ratio * maxHealth);
    }

    public void setHealth(double newHp) {
        if (dead) return;
        health = clamp(newHp, 0.0, maxHealth);
        if (health <= 0.0) die();
    }

    public void addHealth(double delta) { setHealth(health + delta); }
    private void takeDamage(double amount) {
        if (dead || amount <= 0.0) return;
        setHealth(health - amount);
    }

    private void die() {
        if (dead) return;
        dead = true;
        health = 0.0;
        onRemove.accept(this);
    }

    // ---------- Getters útiles ----------
    public void setPosition(double x, double y) { view.setLayoutX(x); view.setLayoutY(y); }
    public double getWidth() { return WIDTH; }
    public double getHeight() { return HEIGHT; }
    public double getHealth() { return health; }
    public boolean isDead() { return dead; }
    public double getMaxHealth() { return maxHealth; }

    // ---------- Util ----------
    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
