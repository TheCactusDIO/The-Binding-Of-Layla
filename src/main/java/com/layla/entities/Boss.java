package com.layla.entities;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.core.GameEntity;

import javafx.animation.PauseTransition;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeType;
import javafx.util.Duration;

public class Boss implements GameEntity {

    protected final Pane parent;
    protected final Circle view;
    protected final Supplier<double[]> playerPos;
    protected final Consumer<Boss> onDeath;
    protected final Consumer<GameEntity> onSpawnProjectile;
    // Callback para eliminar correctamente los proyectiles del GameLoop
    protected final Consumer<GameEntity> onRemoveProjectile;
    protected final String bossId;

    protected double hp;
    protected double maxHp;
    protected double speed = 45.0;
    protected boolean dead = false;

    protected double attackTimer = 0.0;
    protected int phase = 1;

    // =========================
    // THE SPREADER REWORK STATE
    // (solo se usa cuando getClass() == Boss.class)
    // =========================
    private static final double DESIRED_RANGE = 210.0;   // distancia “ideal” al jugador
    private static final double TOO_CLOSE     = 150.0;   // si está muy cerca, se aleja
    private static final double TOO_FAR       = 320.0;   // si está muy lejos, se acerca

    private static final double STRAFE_SPEED_MULT = 0.75;  // strafe lateral
    private static final double APPROACH_SPEED_MULT = 1.10;
    private static final double RETREAT_SPEED_MULT  = 1.25;

    private double patternTimer = 0.0;
    private int patternIndex = 0;

    private double spiralAngle = 0.0;
    private double strafeSign = 1.0;

    private double dashCooldown = 3.0;
    private double dashTimer = 0.0;
    private double dashDirX = 0.0;
    private double dashDirY = 0.0;
    private boolean dashing = false;

    private final ThreadLocalRandom rng = ThreadLocalRandom.current();

    public Boss(double x, double y, double maxHp, Pane parent,
                Supplier<double[]> playerPos,
                Consumer<Boss> onDeath,
                Consumer<GameEntity> onSpawnProjectile,
                Consumer<GameEntity> onRemoveProjectile,
                String bossId) {

        this.maxHp = maxHp;
        this.hp = maxHp;
        this.parent = Objects.requireNonNull(parent);
        this.playerPos = Objects.requireNonNull(playerPos);
        this.onDeath = Objects.requireNonNull(onDeath);
        this.onSpawnProjectile = Objects.requireNonNull(onSpawnProjectile);
        this.onRemoveProjectile = Objects.requireNonNull(onRemoveProjectile);
        this.bossId = bossId;

        this.view = new Circle(40.0, Color.DARKRED);
        this.view.setStroke(Color.BLACK);
        this.view.setStrokeWidth(4.0);
        this.view.setStrokeType(StrokeType.INSIDE);
        this.view.setEffect(new javafx.scene.effect.DropShadow(20, Color.RED));

        this.view.setLayoutX(x);
        this.view.setLayoutY(y);

        parent.getChildren().add(this.view);
    }

    @Override
    public void update(double dt) {
        if (dead || hp <= 0) return;

        // IMPORTANTÍSIMO:
        // Si esta clase NO es exactamente Boss (o sea, es un subclass),
        // mantenemos el comportamiento antiguo para no cambiar tus otros bosses.
        if (getClass() != Boss.class) {
            updateLegacy(dt);
            return;
        }

        updateSpreaderRework(dt);
    }

    // -------------------------
    // Legacy behaviour (old Boss)
    // -------------------------
    private void updateLegacy(double dt) {
        double[] pPos = playerPos.get();
        double dx = pPos[0] - view.getLayoutX();
        double dy = pPos[1] - view.getLayoutY();
        double dist = Math.sqrt(dx*dx + dy*dy);

        if (dist > 1.0) {
            view.setLayoutX(view.getLayoutX() + (dx / dist) * speed * dt);
            view.setLayoutY(view.getLayoutY() + (dy / dist) * speed * dt);
        }

        attackTimer += dt;
        if (attackTimer > 2.0) {
            performAttack(); // aquí ya filtramos para no reworkear a subclasses
            attackTimer = 0.0;
        }
    }

    // -------------------------
    // THE SPREADER REWORK
    // -------------------------
    private void updateSpreaderRework(double dt) {
        double[] pPos = playerPos.get();
        double px = pPos[0];
        double py = pPos[1];

        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        double dx = px - bx;
        double dy = py - by;
        double dist = Math.sqrt(dx*dx + dy*dy);
        if (dist < 0.0001) dist = 0.0001;

        double ndx = dx / dist;
        double ndy = dy / dist;

        // Dashing state
        if (dashing) {
            dashTimer -= dt;
            double dashSpeed = (phase == 1 ? 290.0 : 360.0);
            view.setLayoutX(bx + dashDirX * dashSpeed * dt);
            view.setLayoutY(by + dashDirY * dashSpeed * dt);

            // mini trail shots durante dash (fase 2 más agresivo)
            if (phase >= 2 && rng.nextDouble() < 0.18) {
                spawnMiniSpiralBurst(2, 180.0);
            }

            if (dashTimer <= 0.0) {
                dashing = false;
                dashCooldown = (phase == 1 ? 3.2 : 2.2);

                // shockwave al terminar dash
                spawnRing(10 + phase * 2, 220.0 + (phase * 30.0), 3.0, 1.0);
            }
        } else {
            // Mantener rango y hacer strafe alrededor del jugador
            // Vector perpendicular para strafe
            double sx = -ndy * strafeSign;
            double sy =  ndx * strafeSign;

            double mult;
            if (dist < TOO_CLOSE) mult = RETREAT_SPEED_MULT;
            else if (dist > TOO_FAR) mult = APPROACH_SPEED_MULT;
            else mult = 1.0;

            // Combinación: acercarse/alejarse + strafe
            double moveX = ndx * mult;
            double moveY = ndy * mult;

            // Si está dentro del rango ideal, reduce “follow” y aumenta strafe
            double strafeWeight = (dist > DESIRED_RANGE ? 0.30 : 0.60);
            double followWeight  = 1.0 - strafeWeight;

            double finalX = moveX * followWeight + sx * strafeWeight * STRAFE_SPEED_MULT;
            double finalY = moveY * followWeight + sy * strafeWeight * STRAFE_SPEED_MULT;

            // Si estamos muy cerca, invertimos follow (retirada)
            if (dist < TOO_CLOSE) {
                finalX = -ndx * RETREAT_SPEED_MULT + sx * 0.45;
                finalY = -ndy * RETREAT_SPEED_MULT + sy * 0.45;
            }

            view.setLayoutX(bx + finalX * speed * dt);
            view.setLayoutY(by + finalY * speed * dt);

            // Ocasionalmente cambia el sentido del strafe para que no sea “predecible”
            if (rng.nextDouble() < 0.01) strafeSign *= -1.0;

            // Dash cooldown
            dashCooldown -= dt;
            if (dashCooldown <= 0.0 && dist > 120.0) {
                dashing = true;
                dashTimer = 0.55; // duración del dash
                dashDirX = ndx;
                dashDirY = ndy;

                // telegraph simple: cambia stroke un momento
                view.setStroke(Color.ORANGE);
                PauseTransition pt = new PauseTransition(Duration.millis(140));
                pt.setOnFinished(e -> { if (!dead) view.setStroke(phase >= 2 ? Color.YELLOW : Color.BLACK); });
                pt.play();
            }
        }

        // Patterns
        patternTimer += dt;

        // Ataque base más frecuente (fase 2 más rápido)
        double baseAttackInterval = (phase == 1 ? 1.25 : 0.85);
        attackTimer += dt;

        if (attackTimer >= baseAttackInterval) {
            performAttack();
            attackTimer = 0.0;
        }

        // Cambiar patrón cada X segundos
        double patternDuration = (phase == 1 ? 4.2 : 3.3);
        if (patternTimer >= patternDuration) {
            patternTimer = 0.0;
            patternIndex = (patternIndex + 1) % 3;
        }

        // Ejecutar “ataques continuos” por patrón
        switch (patternIndex) {
            case 0 -> { // SPIRAL PRESSURE (constante)
                spiralAngle += dt * (phase == 1 ? 3.2 : 4.6);
                if (rng.nextDouble() < (phase == 1 ? 0.25 : 0.40)) {
                    spawnSpiralShot(spiralAngle, 240.0 + phase * 40.0);
                }
            }
            case 1 -> { // AIMED FAN (burst más dirigido)
                if (rng.nextDouble() < (phase == 1 ? 0.10 : 0.16)) {
                    spawnAimedFan(5 + phase * 2, 0.35, 260.0 + phase * 40.0);
                }
            }
            default -> { // DENIAL RING (zonas)
                if (rng.nextDouble() < (phase == 1 ? 0.06 : 0.10)) {
                    spawnRing(12 + phase * 2, 170.0, 3.0, 1.0);
                }
            }
        }
    }


    protected void performAttack() {
        if (dead) return;

        // Si es un subclass, NO queremos el rework aquí por defecto.
        if (getClass() != Boss.class) {
            performAttackLegacy();
            return;
        }

        // Burst “core” del Spreader:
        // - Un fan al jugador
        // - + un mini anillo si está en fase 2 (para que no sea gratis acercarse)
        spawnAimedFan(7 + phase * 2, 0.50, 280.0 + phase * 30.0);

        if (phase >= 2) {
            spawnRing(8, 210.0, 3.0, 1.0);
        }
    }

    // Old radial spam (legacy)
    private void performAttackLegacy() {
        int projectiles = 8 + (phase * 2);
        for (int i = 0; i < projectiles; i++) {
            double angle = (2 * Math.PI / projectiles) * i;
            double dirX = Math.cos(angle);
            double dirY = Math.sin(angle);

            Projectile p = new Projectile(
                dirX, dirY,
                200.0, 3.0, 1.0,
                true,
                parent,
                onRemoveProjectile,
                this,
                bossId
            );

            p.getView().setLayoutX(view.getLayoutX());
            p.getView().setLayoutY(view.getLayoutY());

            onSpawnProjectile.accept(p);
        }
    }

    // ======================
    // Projectile helpers
    // ======================
    private void spawnSpiralShot(double angle, double speed) {
        double dirX = Math.cos(angle);
        double dirY = Math.sin(angle);

        Projectile p = new Projectile(
            dirX, dirY,
            speed,
            3.0, 1.0,
            true,
            parent,
            onRemoveProjectile,
            this,
            bossId
        );
        p.getView().setLayoutX(view.getLayoutX());
        p.getView().setLayoutY(view.getLayoutY());
        onSpawnProjectile.accept(p);
    }

    private void spawnMiniSpiralBurst(int count, double speed) {
        for (int i = 0; i < count; i++) {
            spiralAngle += 0.55;
            spawnSpiralShot(spiralAngle, speed);
        }
    }

    private void spawnAimedFan(int shots, double spreadRadians, double speed) {
        double[] pPos = playerPos.get();
        double dx = pPos[0] - view.getLayoutX();
        double dy = pPos[1] - view.getLayoutY();
        double base = Math.atan2(dy, dx);

        int half = shots / 2;
        for (int i = -half; i <= half; i++) {
            double t = (half == 0) ? 0.0 : (i / (double)half);
            double ang = base + t * spreadRadians;

            double dirX = Math.cos(ang);
            double dirY = Math.sin(ang);

            Projectile p = new Projectile(
                dirX, dirY,
                speed,
                3.0, 1.0,
                true,
                parent,
                onRemoveProjectile,
                this,
                bossId
            );
            p.getView().setLayoutX(view.getLayoutX());
            p.getView().setLayoutY(view.getLayoutY());
            onSpawnProjectile.accept(p);
        }
    }

    private void spawnRing(int projectiles, double speed, double radius, double damage) {
        for (int i = 0; i < projectiles; i++) {
            double angle = (2 * Math.PI / projectiles) * i;
            double dirX = Math.cos(angle);
            double dirY = Math.sin(angle);

            Projectile p = new Projectile(
                dirX, dirY,
                speed,
                radius, damage,
                true,
                parent,
                onRemoveProjectile,
                this,
                bossId
            );
            p.getView().setLayoutX(view.getLayoutX());
            p.getView().setLayoutY(view.getLayoutY());
            onSpawnProjectile.accept(p);
        }
    }

    public void takeDamage(double amount) {
        if (dead) return;
        hp -= amount;

        view.setFill(Color.WHITE);
        PauseTransition flash = new PauseTransition(Duration.millis(100));
        flash.setOnFinished(e -> {
            if (!dead) updateColor();
        });
        flash.play();

        if (hp <= 0) {
            die();
        } else {
            checkPhaseChange();
        }
    }

    protected void updateColor() {
        view.setFill(Color.DARKRED);
    }

    protected void checkPhaseChange() {
        if (hp < maxHp * 0.5 && phase == 1) {
            phase = 2;
            speed *= 1.35;
            view.setStroke(Color.YELLOW);

            // Phase 2: un poquito menos de cooldown para dash
            dashCooldown = Math.min(dashCooldown, 1.8);
        }
    }

    protected void die() {
        if (dead) return;
        dead = true;
        parent.getChildren().remove(view);
        onDeath.accept(this);
    }

    public double getHp() { return hp; }
    public double getMaxHp() { return maxHp; }
    public boolean isDead() { return dead; }

    @Override public Node getView() { return view; }
    @Override public Bounds getBounds() { return view.getBoundsInParent(); }

    @Override
    public void onCollision(GameEntity other) {
        if (other instanceof Player p) {
            p.setLastHitSource(bossId);
        }
    }
}
