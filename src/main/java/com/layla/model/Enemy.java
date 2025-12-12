package com.layla.model;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.AppContext;
import com.layla.core.AssetsManager;
import com.layla.core.GameEntity;
import com.layla.core.SpriteAnimator;
import com.layla.entities.Player;
import com.layla.entities.Projectile;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public final class Enemy implements GameEntity {

    private static final double WIDTH = 22.0;
    private static final double HEIGHT = 22.0;
    private static final double EPSILON = 1e-6;

    private final EnemyType type;

    // View components
    private final StackPane viewRoot = new StackPane();
    private final Rectangle debugBox = new Rectangle(WIDTH, HEIGHT);
    private final ImageView spriteView = new ImageView();
    private final SpriteAnimator animator;
    private final boolean hasSprite;

    private final Pane boundsPane;
    private final Supplier<double[]> playerCenterSupplier;
    private final Supplier<List<GameEntity>> obstaclesSupplier;
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

    private int burstShotsRemaining = 0;
    private double burstShotTimer = 0.0;
    private double burstCooldownTimer = 0.0;

    private final double[] tmpDir = new double[2];
    private final double collisionRadius = Math.min(WIDTH, HEIGHT) * 0.5;

    public Enemy(EnemyType type,
                 Pane boundsPane,
                 Supplier<double[]> playerCenterSupplier,
                 Supplier<List<GameEntity>> obstaclesSupplier,
                 Consumer<GameEntity> onRemove,
                 Consumer<GameEntity> onSpawn,
                 Consumer<String> playSfx,
                 double hpMultiplier,
                 double speedMultiplier,
                 double damageMultiplier) {

        this.type = Objects.requireNonNull(type, "type");
        this.boundsPane = Objects.requireNonNull(boundsPane, "boundsPane");
        this.playerCenterSupplier = Objects.requireNonNull(playerCenterSupplier, "playerCenterSupplier");
        this.obstaclesSupplier = obstaclesSupplier;
        this.onRemove = Objects.requireNonNull(onRemove, "onRemove");
        this.onSpawn = Objects.requireNonNull(onSpawn, "onSpawn");
        this.playSfx = (playSfx != null ? playSfx : k -> {});

        this.hpMultiplier = Math.max(0.0, hpMultiplier);
        this.speedMultiplier = Math.max(0.0, speedMultiplier);
        this.damageMultiplier = Math.max(0.0, damageMultiplier);

        // Visual setup
        debugBox.setStroke(Color.BLACK);
        debugBox.setFill(getColorForType(type)); // Fallback color

        Image sheet = AssetsManager.loadImage("assets/images/enemies_sheet.png");
        // Si no hay sheet específico, intentamos cargar iconos individuales como sprites estáticos
        if (sheet == null) {
             sheet = AssetsManager.loadImage("assets/images/enemies/" + type.name() + ".png");
        }

        if (sheet != null) {
            hasSprite = true;
            spriteView.setImage(sheet);
            spriteView.setFitWidth(32);
            spriteView.setFitHeight(32);
            debugBox.setFill(Color.TRANSPARENT);
            debugBox.setStroke(Color.TRANSPARENT);
        } else {
            hasSprite = false;
        }

        // Animador por defecto (32x32)
        animator = new SpriteAnimator(32, 32, 2, 6, 10);

        viewRoot.getChildren().addAll(debugBox, spriteView);
        viewRoot.setManaged(false); // GameLoop maneja posición

        parent().getChildren().add(viewRoot); // Añadir al pane

        EnemyProfile profile = AppContext.balance().profile(type);
        this.maxHealth = profile != null ? Math.max(0.0, profile.baseHp * hpMultiplier) : 0.0;
        this.hp = this.maxHealth;
    }

    private Pane parent() { return boundsPane; } // Helper

    private Color getColorForType(EnemyType t) {
        return switch (t) {
            case SHOOTER  -> Color.ORANGE;
            case MELEE    -> Color.CRIMSON;
            case TURRET   -> Color.DODGERBLUE;
            case TANK     -> Color.DARKOLIVEGREEN;
            case KAMIKAZE -> Color.MAGENTA;
        };
    }

    public EnemyType getType() { return type; }

    @Override
    public void update(double dt) {
        if (dead || dt <= 0.0) return;

        EnemyProfile profile = AppContext.balance().profile(type);
        if (profile == null) return;

        syncHealthWithProfile(profile);

        double[] playerCenter = playerCenterSupplier.get();
        aiTime += dt;

        handleMovement(profile, playerCenter, dt);
        updateAnimation(dt);

        if (type == EnemyType.TURRET) {
            handleTurretShooting(profile, playerCenter, dt);
        } else {
            handleDefaultShooting(profile, playerCenter, dt);
        }
    }

    private void updateAnimation(double dt) {
        if (!hasSprite) return;

        // Animación simple: siempre andando
        animator.update(dt);
        spriteView.setViewport(animator.getCurrentViewport());

        // Flip sprite hacia el jugador
        double[] pc = playerCenterSupplier.get();
        if (pc != null && pc.length >= 1) {
             double dx = pc[0] - getCenterX();
             if (Math.abs(dx) > 1.0) {
                 spriteView.setScaleX(dx > 0 ? 1 : -1);
             }
        }
    }

    // --- MOVIMIENTO (Resumido del original para brevedad, lógica intacta) ---
    private void handleMovement(EnemyProfile profile, double[] playerCenter, double dt) {
        boolean isStat = profile.stationary && type != EnemyType.SHOOTER;
        if (type == EnemyType.TURRET || isStat || !hasValidTarget(playerCenter)) return;

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
        moveWithSlide(dir, profile.speed * speedMultiplier * dt);
    }

    private void moveMeleeZigZag(EnemyProfile profile, double[] playerCenter, double dt) {
        double[] dir = directionTo(playerCenter);
        if (dir == null) return;
        double px = -dir[1]; double py = dir[0];
        double wave = Math.sin(aiTime * 6.0);
        double sideFactor = 0.45;
        double dx = dir[0] + px * wave * sideFactor;
        double dy = dir[1] + py * wave * sideFactor;
        double len = Math.hypot(dx, dy);
        if (len < EPSILON) return;
        dir[0] = dx / len; dir[1] = dy / len;
        applyJitter(dir, profile.jitter * 0.5);
        moveWithSlide(dir, profile.speed * speedMultiplier * dt);
    }

    private void moveShooterKiting(EnemyProfile profile, double[] playerCenter, double dt) {
        double dx = playerCenter[0] - getCenterX();
        double dy = playerCenter[1] - getCenterY();
        double dist = Math.hypot(dx, dy);
        if (dist < EPSILON) return;
        double dirX = dx / dist; double dirY = dy / dist;
        double minRange = 150.0; double maxRange = 250.0;
        double baseSpeed = (profile.speed < 10.0) ? 100.0 : profile.speed;
        double speed = baseSpeed * speedMultiplier;

        if (dist < minRange) { tmpDir[0] = -dirX; tmpDir[1] = -dirY; }
        else if (dist > maxRange) { tmpDir[0] = dirX; tmpDir[1] = dirY; }
        else {
            tmpDir[0] = -dirY; tmpDir[1] = dirX;
            speed *= 0.6;
            applyJitter(tmpDir, profile.jitter * 0.25);
            moveWithSlide(tmpDir, speed * dt);
            return;
        }
        applyJitter(tmpDir, profile.jitter);
        moveWithSlide(tmpDir, speed * dt);
    }

    private void moveTank(EnemyProfile profile, double[] playerCenter, double dt) {
        double[] dir = directionTo(playerCenter);
        if (dir == null) return;
        double hpRatio = maxHealth > 0.0 ? hp / maxHealth : 1.0;
        double speed = profile.speed * speedMultiplier;
        if (hpRatio <= 0.5) speed *= 1.4;
        applyJitter(dir, profile.jitter);
        moveWithSlide(dir, speed * dt);
    }

    private void moveKamikaze(EnemyProfile profile, double[] playerCenter, double dt) {
        double dx = playerCenter[0] - getCenterX();
        double dy = playerCenter[1] - getCenterY();
        double dist = Math.hypot(dx, dy);
        if (dist < EPSILON) return;
        double dirX = dx / dist; double dirY = dy / dist;
        double nearDist = 120.0; double farDist = 260.0;
        double factor;
        if (dist <= nearDist) factor = 1.6;
        else if (dist >= farDist) factor = 0.8;
        else { double t = (dist - nearDist) / (farDist - nearDist); factor = 1.6 + (0.8 - 1.6) * t; }
        double speed = profile.speed * speedMultiplier * factor;
        tmpDir[0] = dirX; tmpDir[1] = dirY;
        applyJitter(tmpDir, profile.jitter);
        moveWithSlide(tmpDir, speed * dt);
    }

    private void moveWithSlide(double[] dir, double distance) {
        if (distance <= 0.0) return;
        double deltaX = dir[0] * distance;
        double deltaY = dir[1] * distance;
        double currX = viewRoot.getLayoutX();
        double currY = viewRoot.getLayoutY();

        if (!checkCollision(currX + deltaX, currY)) currX += deltaX;
        if (!checkCollision(currX, currY + deltaY)) currY += deltaY;

        double maxX = Math.max(0.0, boundsPane.getWidth() - WIDTH);
        double maxY = Math.max(0.0, boundsPane.getHeight() - HEIGHT);
        viewRoot.setLayoutX(clamp(currX, 0.0, maxX));
        viewRoot.setLayoutY(clamp(currY, 0.0, maxY));
    }

    private boolean checkCollision(double x, double y) {
        if (obstaclesSupplier == null) return false;
        List<GameEntity> obstacles = obstaclesSupplier.get();
        if (obstacles == null || obstacles.isEmpty()) return false;
        BoundingBox myBounds = new BoundingBox(x, y, WIDTH, HEIGHT);
        double margin = 1.0;
        BoundingBox checkBounds = new BoundingBox(x + margin, y + margin, WIDTH - margin*2, HEIGHT - margin*2);
        for (GameEntity obs : obstacles) {
            if (obs.getBounds().intersects(checkBounds)) return true;
        }
        return false;
    }

    // --- DISPARO (Igual que antes) ---
    private void handleDefaultShooting(EnemyProfile profile, double[] playerCenter, double dt) {
        if (profile.fireRate > 0.0 && profile.projSpeed > 0.0 && profile.projRange > 0.0) {
            timeSinceShot += dt;
            double interval = (1.0 / profile.fireRate);
            if (timeSinceShot >= interval) {
                if (hasValidTarget(playerCenter)) {
                    timeSinceShot = 0.0;
                    shootTowards(playerCenter, profile);
                } else timeSinceShot = interval;
            }
        } else timeSinceShot = 0.0;
    }

    private void handleTurretShooting(EnemyProfile profile, double[] playerCenter, double dt) {
        double projSpeed = Math.max(0.0, profile.projSpeed);
        if (projSpeed <= 0.0) { burstShotsRemaining = 0; return; }
        double burstInterval = (1.0 / profile.fireRate);
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
        if (hasValidTarget(playerCenter)) shootTowards(playerCenter, profile);
        if (burstShotsRemaining <= 0) burstShotTimer = 0.0;
    }

    private void shootTowards(double[] target, EnemyProfile profile) {
        double[] dir = directionTo(target);
        if (dir == null) return;
        double projSpeed = Math.max(0.0, profile.projSpeed);
        double projRange = Math.max(0.0, profile.projRange);
        double projDamage = Math.max(0.0, profile.projDamage * damageMultiplier);
        if (projSpeed <= 0.0 || projRange <= 0.0 || projDamage <= 0.0) return;
        double lifetime = projRange / projSpeed;

        Projectile projectile = new Projectile(dir[0], dir[1], projSpeed, lifetime, projDamage,
                true, boundsPane, onRemove, this, type.name());
        projectile.getView().setLayoutX(getCenterX() - 4.0);
        projectile.getView().setLayoutY(getCenterY() - 4.0);
        onSpawn.accept(projectile);
    }

    // --- UTILS ---
    private void syncHealthWithProfile(EnemyProfile profile) {
        double desiredMax = Math.max(0.0, profile.baseHp * hpMultiplier);
        if (Math.abs(desiredMax - maxHealth) > 1e-6) {
            double ratio = maxHealth > 0.0 ? hp / maxHealth : 1.0;
            maxHealth = desiredMax;
            hp = Math.min(maxHealth, Math.max(0.0, ratio * maxHealth));
            if (hp <= 0.0) die();
        } else if (hp > maxHealth) hp = maxHealth;
    }

    private double[] directionTo(double[] target) {
        double cx = getCenterX(); double cy = getCenterY();
        double dx = target[0] - cx; double dy = target[1] - cy;
        double len = Math.hypot(dx, dy);
        if (len < EPSILON) return null;
        tmpDir[0] = dx / len; tmpDir[1] = dy / len;
        return tmpDir;
    }

    private void applyJitter(double[] dir, double jitterPercent) {
        double magnitude = Math.max(0.0, Math.min(1.0, jitterPercent * 0.01));
        if (magnitude <= 0.0) return;
        double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
        dir[0] += Math.cos(angle) * magnitude;
        dir[1] += Math.sin(angle) * magnitude;
        double len = Math.hypot(dir[0], dir[1]);
        if (len < EPSILON) { dir[0] = 0.0; dir[1] = 1.0; }
        else { dir[0] /= len; dir[1] /= len; }
    }

    @Override public Node getView() { return viewRoot; }
    @Override public Bounds getBounds() { return viewRoot.getBoundsInParent(); }

    // ... Implementación de onCollision y damage (adaptados para usar viewRoot o helpers) ...
    @Override
    public void onCollision(GameEntity other) {
        if (dead) return;
        if (other instanceof Player player) {
            EnemyProfile profile = AppContext.balance().profile(type);
            double dmg = (profile != null ? profile.contactDmg : AppContext.balance().enemyContactDamage);
            dmg *= damageMultiplier;
            if (dmg > 0.0) {
                player.setLastHitSource(type.name());
                player.takeDamage(dmg);
                playSfx.accept("hurt");
            }
        }
    }

    public double getWidth() { return WIDTH; }
    public double getHeight() { return HEIGHT; }
    public double getCollisionRadius() { return collisionRadius; }
    public double getCenterX() { return viewRoot.getLayoutX() + getWidth() * 0.5; }
    public double getCenterY() { return viewRoot.getLayoutY() + getHeight() * 0.5; }

    public void setPosition(double x, double y) {
        viewRoot.setLayoutX(x);
        viewRoot.setLayoutY(y);
    }

    public void applyDamage(double dmg) {
        if (dead || dmg <= 0.0) return;
        hp -= dmg;
        if (hp <= 0.0) die();
        else {
            flashHit();
            playSfx.accept("hit");
        }
    }

    public boolean isDead() {
        return dead;
    }

    private void die() {
        if (dead) return;
        dead = true;
        hp = 0.0;
        spawnDeathFx();
        onRemove.accept(this);
    }

    private boolean hasValidTarget(double[] playerCenter) {
        return playerCenter != null && playerCenter.length >= 2;
    }

    private void flashHit() {
        if (hitFlashTimer == null) {
            hitFlashTimer = new PauseTransition(Duration.millis(120));
            hitFlashTimer.setOnFinished(e -> {
                debugBox.setStroke(Color.TRANSPARENT);
                spriteView.setEffect(null);
            });
        } else hitFlashTimer.stop();

        debugBox.setStroke(Color.WHITE);
        // Podríamos poner un efecto de brillo en el ImageView
        spriteView.setEffect(new javafx.scene.effect.ColorAdjust(0, 0, 0.5, 0));
        hitFlashTimer.playFromStart();
    }

    private void spawnDeathFx() {
        Circle fx = new Circle(6, Color.ORANGERED);
        fx.setManaged(false);
        fx.setLayoutX(getCenterX()); fx.setLayoutY(getCenterY());
        GameEntity fxEntity = new GameEntity() {
            @Override public void update(double dt) {}
            @Override public Node getView() { return fx; }
        };
        onSpawn.accept(fxEntity);
        FadeTransition fade = new FadeTransition(Duration.millis(250), fx);
        fade.setFromValue(1.0); fade.setToValue(0.0);
        ScaleTransition scale = new ScaleTransition(Duration.millis(250), fx);
        scale.setFromX(1.0); scale.setFromY(1.0); scale.setToX(1.8); scale.setToY(1.8);
        ParallelTransition pt = new ParallelTransition(fx, fade, scale);
        pt.setOnFinished(e -> onRemove.accept(fxEntity));
        pt.play();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(v, max));
    }
}
