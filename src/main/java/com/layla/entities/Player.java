package com.layla.entities;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.core.AssetsManager;
import com.layla.core.GameEntity;
import com.layla.core.InputService;
import com.layla.core.SpriteAnimator;
import com.layla.model.PlayerStatId;
import com.layla.services.StatsService;

import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public final class Player implements GameEntity {

    private static final double TAU_ACCEL    = 0.035;
    private static final double TAU_DECEL    = 0.090;
    private static final double TAU_REVERSE  = 0.045;

    // --- CONFIGURACIÓN DEL SPRITE ---
    // Ajusta esto al tamaño de cada "cuadradito" en tu PNG
    private static final int FRAME_W = 32;
    private static final int FRAME_H = 32;
    // Ajusta las filas según tu imagen (0 es la primera fila de arriba)
    private static final int ROW_DOWN = 0;
    private static final int ROW_SIDE = 1; // Derecha (se invierte para izquierda)
    private static final int ROW_UP   = 2;
    // Cuántos frames (dibujos) tiene cada animación
    private static final int ANIM_FRAMES = 8; // Ejemplo: 8 pasos al caminar
    private static final int COLUMNS_IN_SHEET = 10; // Cuantas columnas tiene tu PNG en total

    // View Components
    private final StackPane viewRoot = new StackPane();
    private final Rectangle debugBox = new Rectangle(26, 26, Color.TRANSPARENT);
    private final ImageView spriteView = new ImageView();

    private final SpriteAnimator animator;
    private final boolean hasSprite;

    private final Supplier<double[]> moveSupplier;
    private final Pane boundsPane;
    private final StatsService statsService;

    private double vx;
    private double vy;

    // Dirección visual actual (0=Abajo, 1=Derecha, 2=Arriba, 3=Izquierda)
    private int facingDir = 0;

    private double health = 6.0;
    private double maxHealth = 6.0;
    private boolean dead = false;

    private double invulnTimer = 0.0;
    private static final double INVULN_DURATION = 0.6;

    private final Consumer<String> playSfx;
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

        debugBox.setStroke(Color.BLACK);
        debugBox.setStrokeWidth(1);

        Image sheet = AssetsManager.loadImage("assets/images/player_sheet.png");
        if (sheet == null) {
            hasSprite = false;
            debugBox.setFill(Color.CYAN);
            // Animador dummy
            animator = new SpriteAnimator(1, 1, 1, 1, 1);
        } else {
            hasSprite = true;
            spriteView.setImage(sheet);
            spriteView.setFitWidth(40);
            spriteView.setFitHeight(40);
            // IMPORTANTE: Desactivar suavizado para pixel art nítido
            spriteView.setSmooth(false);

            debugBox.setFill(Color.TRANSPARENT);
            debugBox.setStroke(Color.TRANSPARENT); // Ocultar hitbox si hay sprite

            // Inicializar animador
            animator = new SpriteAnimator(FRAME_W, FRAME_H, ANIM_FRAMES, 12, COLUMNS_IN_SHEET);

            // Forzar el primer recorte inmediatamente para que no salga la hoja entera
            spriteView.setViewport(new Rectangle2D(0, 0, FRAME_W, FRAME_H));
        }

        viewRoot.getChildren().addAll(debugBox, spriteView);
        viewRoot.setLayoutX(100);
        viewRoot.setLayoutY(100);

        boundsPane.getChildren().add(viewRoot);
    }

    public Player(InputService input, Pane boundsPane, StatsService statsService) {
        this(Objects.requireNonNull(input, "input")::getMoveVector, boundsPane, statsService, null);
    }

    public void setLastHitSource(String source) { this.lastHitSource = source; }
    public String getLastHitSource() { return lastHitSource; }

    @Override
    public void update(double dt) {
        if (dt <= 0 || dead) return;

        if (invulnTimer > 0.0) invulnTimer = Math.max(0.0, invulnTimer - dt);
        viewRoot.setOpacity(invulnTimer > 0 && (invulnTimer % 0.1 > 0.05) ? 0.4 : 1.0);

        syncMaxHealthFromStats();
        handleMovement(dt);
        updateAnimation(dt);
    }

    private void handleMovement(double dt) {
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

        double nextX = viewRoot.getLayoutX() + vx * dt;
        double nextY = viewRoot.getLayoutY() + vy * dt;

        double maxX = Math.max(0.0, boundsPane.getWidth()  - getWidth());
        double maxY = Math.max(0.0, boundsPane.getHeight() - getHeight());
        if (nextX < 0.0)        { nextX = 0.0; vx = 0.0; }
        else if (nextX > maxX)  { nextX = maxX; vx = 0.0; }
        if (nextY < 0.0)        { nextY = 0.0; vy = 0.0; }
        else if (nextY > maxY)  { nextY = maxY; vy = 0.0; }

        viewRoot.setLayoutX(nextX);
        viewRoot.setLayoutY(nextY);
    }

    private void updateAnimation(double dt) {
        if (!hasSprite) return;

        boolean moving = Math.abs(vx) > 10.0 || Math.abs(vy) > 10.0;

        // Determinar dirección principal
        if (Math.abs(vx) > Math.abs(vy)) {
            if (Math.abs(vx) > 1.0) facingDir = (vx > 0) ? 1 : 3; // 1=Der, 3=Izq
        } else {
            if (Math.abs(vy) > 1.0) facingDir = (vy > 0) ? 0 : 2; // 0=Abajo, 2=Arriba
        }

        int targetRow = ROW_DOWN;
        boolean flip = false;

        switch (facingDir) {
            case 0: targetRow = ROW_DOWN; break;
            case 2: targetRow = ROW_UP; break;
            case 1: targetRow = ROW_SIDE; flip = false; break;
            case 3: targetRow = ROW_SIDE; flip = true; break; // Reusamos el sprite de lado invirtiéndolo
        }

        // Si se mueve, usa animación completa (8 frames), si no, solo el primer frame (quieto)
        // Ajusta ANIM_FRAMES según tu hoja de sprites
        if (moving) {
            animator.setAnimationConfig(targetRow, 0, ANIM_FRAMES, true);
        } else {
            // Quieto: Usamos la misma fila pero solo el primer frame (columna 0)
            animator.setAnimationConfig(targetRow, 0, 1, true);
        }

        // Voltear sprite horizontalmente si mira a la izquierda
        spriteView.setScaleX(flip ? -1 : 1);

        animator.update(dt);
        Rectangle2D viewport = animator.getCurrentViewport();
        spriteView.setViewport(viewport);
    }

    private static double pickTau(double v, double tv) {
        if (tv == 0.0) return TAU_DECEL;
        if (Math.signum(v) != Math.signum(tv) && v != 0.0) return TAU_REVERSE;
        return TAU_ACCEL;
    }

    @Override
    public Node getView() { return viewRoot; }

    @Override
    public Bounds getBounds() { return viewRoot.getBoundsInParent(); }

    public void setPosition(double x, double y) {
        viewRoot.setLayoutX(x);
        viewRoot.setLayoutY(y);
    }

    public double getWidth()  { return 26; }
    public double getHeight() { return 26; }

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
        viewRoot.setOpacity(0.5);
        viewRoot.setRotate(90);
    }

    private void syncMaxHealthFromStats() {
        double desiredMax = statsService.getStat(PlayerStatId.MAX_HEALTH);
        if (Math.abs(desiredMax - maxHealth) > 1e-6) {
            setMaxHealth(desiredMax);
        }
    }
}
