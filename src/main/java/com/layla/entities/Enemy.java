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
 * Enemigo básico: persigue al jugador y dispara ocasionalmente en 4 direcciones.
 * - Solo recibe daño de balas del jugador (Projectile.isFromEnemy() == false).
 * - Contacto con el Player: 0.5 de daño al jugador.
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

    private double health;
    private boolean dead;

    // --- disparo enemigo ---
    private final java.util.function.Consumer<com.layla.core.GameEntity> onSpawn;
    private double shootTimer = 0.0;
    private final double shootIntervalMin = 1.5; // s
    private final double shootIntervalMax = 3.5; // s
    private final double shootBias = 0.15; // leve sesgo a la componente dominante
    private final Pane pane; // para spawnear proyectiles

    // --- sfx ---
    private final java.util.function.Consumer<String> playSfx;

    // --- hp base ---
    private final double maxHealth;

    public Enemy(Pane boundsPane,
                 Supplier<double[]> playerCenterSupplier,
                 double speed,
                 double health,
                 Consumer<GameEntity> onRemove,
                 java.util.function.Consumer<GameEntity> onSpawn,
                 java.util.function.Consumer<String> playSfx) {

        this.boundsPane = Objects.requireNonNull(boundsPane, "boundsPane");
        this.pane = boundsPane;
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
        if (len < EPSILON) return;

        dirX /= len;
        dirY /= len;

        double nextX = view.getLayoutX() + dirX * speed * dt;
        double nextY = view.getLayoutY() + dirY * speed * dt;

        double maxX = Math.max(0.0, boundsPane.getWidth() - WIDTH);
        double maxY = Math.max(0.0, boundsPane.getHeight() - HEIGHT);
        nextX = clamp(nextX, 0.0, maxX);
        nextY = clamp(nextY, 0.0, maxY);

        view.setLayoutX(nextX);
        view.setLayoutY(nextY);

        // Disparo ocasional hacia el jugador (4 direcciones con leve sesgo)
        shootTimer -= dt;
        if (shootTimer <= 0.0) {
            tryShootAtPlayer();
            resetShootTimer();
        }
    }

    @Override public Node getView() { return view; }
    @Override public Bounds getBounds() { return view.getBoundsInParent(); }

    @Override
    public void onCollision(GameEntity other) {
        if (dead) return;

        // Impacto de proyectil: solo daño si la bala NO es enemiga (i.e., del jugador)
        if (other instanceof Projectile projectile) {
            if (!projectile.isFromEnemy()) {
                takeDamage(projectile.getDamage());
                playSfx.accept("hit");
            }
            return;
        }

       // Contacto con el jugador: aplica daño aquí (solo desde Enemy -> Player)
        if (other instanceof Player p) {
            p.takeDamage(1.0);      // ajusta a 0.5 si quieres medio corazón = 0.5 HP
            playSfx.accept("hurt"); // opcional: sonido de daño al player
        }
    }

    private void tryShootAtPlayer() {
        double[] playerCenter = playerCenterSupplier.get();
        if (playerCenter == null || playerCenter.length < 2) return;

        // vector al jugador
        double cx = view.getLayoutX() + WIDTH * 0.5;
        double cy = view.getLayoutY() + HEIGHT * 0.5;
        double dx = playerCenter[0] - cx;
        double dy = playerCenter[1] - cy;
        double len = Math.hypot(dx, dy);
        if (len < EPSILON) return;
        double ndx = dx / len, ndy = dy / len;

        // dirección cardinal primaria (H/V) con leve sesgo
        double scoreH = Math.abs(dx) + shootBias * Math.abs(ndx);
        double scoreV = Math.abs(dy) + shootBias * Math.abs(ndy);
        double sx = 0.0, sy = 0.0;
        if (scoreH >= scoreV) sx = (dx >= 0 ? 1.0 : -1.0);
        else                   sy = (dy >= 0 ? 1.0 : -1.0);

        // parámetros del proyectil enemigo
        double projSpeed = 100.0;
        double projRange = 1.2;
        double projDamage = 1.0;

        // origen: centro del enemigo
        double spawnX = cx;
        double spawnY = cy;

        // Crea el proyectil y publícalo (fromEnemy = true)
        Projectile bullet = new Projectile(sx, sy, projSpeed, projRange, projDamage, pane, g -> {}, true);
        bullet.getView().setLayoutX(spawnX);
        bullet.getView().setLayoutY(spawnY);
        onSpawn.accept(bullet);
    }

    public void setPosition(double x, double y) {
        view.setLayoutX(x);
        view.setLayoutY(y);
    }

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

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private void resetShootTimer() {
        double span = shootIntervalMax - shootIntervalMin;
        shootTimer = shootIntervalMin + Math.random() * Math.max(0.0, span);
    }
}
