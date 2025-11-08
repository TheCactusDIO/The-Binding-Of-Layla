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
 * Enemigo básico: persigue al jugador y dispara ocasionalmente hacia él.
 * - Solo recibe daño de balas del jugador (Projectile.isFromEnemy()==false).
 * - Contacto con Player: daño al jugador (aplica i-frames en Player).
 */
public final class Enemy implements GameEntity {

    private static final double WIDTH = 22.0;
    private static final double HEIGHT = 22.0;
    private static final double EPSILON = 1e-6;

    private final Rectangle view = new Rectangle(WIDTH, HEIGHT, Color.DARKRED);
    private final Pane boundsPane;
    private final Supplier<double[]> playerCenterSupplier;
    private final double speed;
    private final Consumer<GameEntity> onRemove;
    private final Consumer<GameEntity> onSpawn; // para añadir el proyectil al loop

    private double health;
    private boolean dead;

    // Disparo enemigo
    private double shootTimer = 0.0;
    private final double shootIntervalMin = 1.5;
    private final double shootIntervalMax = 3.5;

    // sfx
    private final Consumer<String> playSfx;

    // hp base
    private final double maxHealth;

    public Enemy(Pane boundsPane,
                 Supplier<double[]> playerCenterSupplier,
                 double speed,
                 double health,
                 Consumer<GameEntity> onRemove,
                 Consumer<GameEntity> onSpawn,
                 Consumer<String> playSfx) {

        this.boundsPane = Objects.requireNonNull(boundsPane, "boundsPane");
        this.playerCenterSupplier = Objects.requireNonNull(playerCenterSupplier, "playerCenterSupplier");
        this.speed = speed;
        this.health = health;
        this.maxHealth = health;
        this.onRemove = Objects.requireNonNull(onRemove, "onRemove");
        this.onSpawn = Objects.requireNonNull(onSpawn, "onSpawn");
        this.playSfx = (playSfx != null ? playSfx : k -> {});

        view.setStroke(Color.BLACK);
        view.setManaged(false);
        resetShootTimer();
    }

    @Override
    public void update(double dt) {
        if (dead || dt <= 0.0) return;

        double[] playerCenter = playerCenterSupplier.get();
        if (playerCenter == null || playerCenter.length < 2) return;

        double enemyCenterX = view.getLayoutX() + WIDTH * 0.5;
        double enemyCenterY = view.getLayoutY() + HEIGHT * 0.5;

        double dirX = playerCenter[0] - enemyCenterX;
        double dirY = playerCenter[1] - enemyCenterY;
        double len = Math.hypot(dirX, dirY);
        if (len > EPSILON) {
            dirX /= len;
            dirY /= len;

            double nextX = clamp(view.getLayoutX() + dirX * speed * dt, 0.0, Math.max(0.0, boundsPane.getWidth()  - WIDTH));
            double nextY = clamp(view.getLayoutY() + dirY * speed * dt, 0.0, Math.max(0.0, boundsPane.getHeight() - HEIGHT));

            view.setLayoutX(nextX);
            view.setLayoutY(nextY);
        }

        // disparo
        shootTimer -= dt;
        if (shootTimer <= 0.0) {
            shootAtPlayer();
            resetShootTimer();
        }
    }

    @Override public Node getView() { return view; }
    @Override public Bounds getBounds() { return view.getBoundsInParent(); }

    @Override
    public void onCollision(GameEntity other) {
        if (dead) return;

        // Solo daño si la bala no es enemiga (viene del jugador)
        if (other instanceof Projectile projectile) {
            if (!projectile.isFromEnemy()) {
                takeDamage(projectile.getDamage());
                playSfx.accept("hit");
            }
            return;
        }

        if (other instanceof Player p) {
            p.takeDamage(1.0); // (usa 0.5 para medio corazón si quieres)
            playSfx.accept("hurt");
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

        // Crea proyectil enemigo con owner=this y fromEnemy=true
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

        onSpawn.accept(p); // GameLoop.addEntity
    }

    public void setPosition(double x, double y) { view.setLayoutX(x); view.setLayoutY(y); }
    public double getWidth() { return WIDTH; }
    public double getHeight() { return HEIGHT; }
    public double getHealth() { return health; }
    public boolean isDead() { return dead; }
    public double getMaxHealth() { return maxHealth; }

    private void takeDamage(double amount) {
        if (dead) return;
        health -= amount;
        if (health <= 0.0) {
            health = 0.0;
            dead = true;
            onRemove.accept(this);
        }
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private void resetShootTimer() {
        double span = shootIntervalMax - shootIntervalMin;
        shootTimer = shootIntervalMin + Math.random() * Math.max(0.0, span);
    }
}
