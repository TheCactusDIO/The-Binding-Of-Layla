package com.layla.entities;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.core.GameEntity;

import javafx.animation.PauseTransition;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeType;
import javafx.util.Duration;

/**
 * Entidad base de jefe.
 *
 * Nota de diseño:
 * - Si la instancia es exactamente {@code Boss}, usa el comportamiento "Spreader rework".
 * - Si es una subclase, mantiene el comportamiento legacy para no alterar otros bosses.
 */
public class Boss implements GameEntity {

    // =========================
    // Dependencias / callbacks
    // =========================

    protected final Pane parent;
    protected final Circle view;
    protected final Supplier<double[]> playerPos;

    protected final Consumer<Boss> onDeath;
    protected final Consumer<GameEntity> onSpawnProjectile;

    /**
     * Callback para eliminar correctamente proyectiles del GameLoop.
     * El Projectile lo usará para pedir su eliminación sin dejar basura en listas internas.
     */
    protected final Consumer<GameEntity> onRemoveProjectile;

    /**
     * ID del boss (se usa para estadísticas o para "last hit source").
     * No se fuerza a non-null para no romper llamadas existentes, pero conviene que lo sea.
     */
    protected final String bossId;

    // =========================
    // Estado base del boss
    // =========================

    protected double hp;
    protected double maxHp;
    protected double speed = 45.0;

    protected boolean dead = false;
    protected int phase = 1;
    protected double attackTimer = 0.0;

    // =========================
    // Rework state (solo Boss.class)
    // =========================

    private static final double DESIRED_RANGE = 210.0;
    private static final double TOO_CLOSE = 150.0;
    private static final double TOO_FAR = 320.0;

    private static final double STRAFE_SPEED_MULT = 0.75;
    private static final double APPROACH_SPEED_MULT = 1.10;
    private static final double RETREAT_SPEED_MULT = 1.25;

    private static final double DASH_DURATION_SEC = 0.55;

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

    // =========================
    // Constructor
    // =========================

    public Boss(
            double x,
            double y,
            double maxHp,
            Pane parent,
            Supplier<double[]> playerPos,
            Consumer<Boss> onDeath,
            Consumer<GameEntity> onSpawnProjectile,
            Consumer<GameEntity> onRemoveProjectile,
            String bossId
    ) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.playerPos = Objects.requireNonNull(playerPos, "playerPos");
        this.onDeath = Objects.requireNonNull(onDeath, "onDeath");
        this.onSpawnProjectile = Objects.requireNonNull(onSpawnProjectile, "onSpawnProjectile");
        this.onRemoveProjectile = Objects.requireNonNull(onRemoveProjectile, "onRemoveProjectile");

        this.maxHp = maxHp;
        this.hp = maxHp;

        this.bossId = bossId; // se mantiene tal cual para no romper IDs existentes

        this.view = new Circle(40.0, Color.DARKRED);
        this.view.setStroke(Color.BLACK);
        this.view.setStrokeWidth(4.0);
        this.view.setStrokeType(StrokeType.INSIDE);
        this.view.setEffect(new DropShadow(20, Color.RED));

        this.view.setLayoutX(x);
        this.view.setLayoutY(y);

        parent.getChildren().add(this.view);
    }

    // =========================
    // Game loop
    // =========================

    /**
     * Actualiza el boss.
     * Si {@code hp <= 0} por cualquier razón, se fuerza muerte para evitar "entidades zombis".
     */
    @Override
    public void update(double dt) {
        if (dead) return;

        if (hp <= 0.0) {
            die();
            return;
        }

        // Mantener legacy en subclases
        if (getClass() != Boss.class) {
            updateLegacy(dt);
            return;
        }

        updateSpreaderRework(dt);
    }

    /**
     * Comportamiento antiguo: persigue al jugador y cada X segundos lanza radial spam.
     * Se conserva para subclases para evitar cambios de gameplay no deseados.
     */
    private void updateLegacy(double dt) {
        double[] pPos = playerPos.get();

        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        double dx = pPos[0] - bx;
        double dy = pPos[1] - by;

        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > 1.0) {
            view.setLayoutX(bx + (dx / dist) * speed * dt);
            view.setLayoutY(by + (dy / dist) * speed * dt);
        }

        attackTimer += dt;
        if (attackTimer > 2.0) {
            performAttack();
            attackTimer = 0.0;
        }
    }

    /**
     * Rework del boss base:
     * - Mantiene rango, hace strafe, y alterna patrones de presión.
     * - Tiene dash con telegraph simple.
     */
    private void updateSpreaderRework(double dt) {
        double[] pPos = playerPos.get();
        double px = pPos[0];
        double py = pPos[1];

        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        double dx = px - bx;
        double dy = py - by;

        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.0001) dist = 0.0001;

        double ndx = dx / dist;
        double ndy = dy / dist;

        // -------------------------
        // Dash
        // -------------------------
        if (dashing) {
            dashTimer -= dt;

            double dashSpeed = (phase == 1) ? 290.0 : 360.0;
            view.setLayoutX(bx + dashDirX * dashSpeed * dt);
            view.setLayoutY(by + dashDirY * dashSpeed * dt);

            // Trail shots durante dash (fase 2 más agresivo)
            if (phase >= 2 && rng.nextDouble() < 0.18) {
                spawnMiniSpiralBurst(2, 180.0);
            }

            if (dashTimer <= 0.0) {
                dashing = false;
                dashCooldown = (phase == 1) ? 3.2 : 2.2;

                // Shockwave al terminar dash
                spawnRing(10 + phase * 2, 220.0 + phase * 30.0, 3.0, 1.0);
            }

        } else {
            // -------------------------
            // Movimiento con strafe
            // -------------------------

            // Perpendicular para strafe
            double sx = -ndy * strafeSign;
            double sy =  ndx * strafeSign;

            double mult;
            if (dist < TOO_CLOSE) mult = RETREAT_SPEED_MULT;
            else if (dist > TOO_FAR) mult = APPROACH_SPEED_MULT;
            else mult = 1.0;

            double moveX = ndx * mult;
            double moveY = ndy * mult;

            // Dentro del rango ideal, más strafe que seguimiento
            double strafeWeight = (dist > DESIRED_RANGE) ? 0.30 : 0.60;
            double followWeight = 1.0 - strafeWeight;

            double finalX = moveX * followWeight + sx * strafeWeight * STRAFE_SPEED_MULT;
            double finalY = moveY * followWeight + sy * strafeWeight * STRAFE_SPEED_MULT;

            // Si está muy cerca, fuerza retirada
            if (dist < TOO_CLOSE) {
                finalX = -ndx * RETREAT_SPEED_MULT + sx * 0.45;
                finalY = -ndy * RETREAT_SPEED_MULT + sy * 0.45;
            }

            view.setLayoutX(bx + finalX * speed * dt);
            view.setLayoutY(by + finalY * speed * dt);

            // Cambia strafe de vez en cuando para no ser “mecánico”
            if (rng.nextDouble() < 0.01) strafeSign *= -1.0;

            // Cooldown de dash
            dashCooldown -= dt;
            if (dashCooldown <= 0.0 && dist > 120.0) {
                startDashTelegraphed(ndx, ndy);
            }
        }

        // -------------------------
        // Ataques por tiempo
        // -------------------------
        patternTimer += dt;

        double baseAttackInterval = (phase == 1) ? 2.5 : 1.5;
        attackTimer += dt;
        if (attackTimer >= baseAttackInterval) {
            performAttack();
            attackTimer = 0.0;
        }

        // Cambio de patrón
        double patternDuration = (phase == 1) ? 4.2 : 3.3;
        if (patternTimer >= patternDuration) {
            patternTimer = 0.0;
            patternIndex = (patternIndex + 1) % 3;
        }

        // Patrones con "presión" continua
        switch (patternIndex) {
            case 0 -> { // Spiral pressure
                spiralAngle += dt * ((phase == 1) ? 3.2 : 4.6);
                if (rng.nextDouble() < ((phase == 1) ? 0.12 : 0.20)) {
                    spawnSpiralShot(spiralAngle, 240.0 + phase * 40.0);
                }
            }
            case 1 -> { // Aimed fan bursts
                if (rng.nextDouble() < ((phase == 1) ? 0.05 : 0.16)) {
                    spawnAimedFan(5 + phase * 2, 0.35, 260.0 + phase * 40.0);
                }
            }
            default -> { // Denial rings
                if (rng.nextDouble() < ((phase == 1) ? 0.04 : 0.10)) {
                    spawnRing(12 + phase * 2, 170.0, 3.0, 1.0);
                }
            }
        }
    }

    /**
     * Inicia un dash con un telegraph visual corto (stroke temporal).
     */
    private void startDashTelegraphed(double ndx, double ndy) {
        dashing = true;
        dashTimer = DASH_DURATION_SEC;
        dashDirX = ndx;
        dashDirY = ndy;

        view.setStroke(Color.ORANGE);

        PauseTransition pt = new PauseTransition(Duration.millis(140));
        pt.setOnFinished(e -> {
            if (!dead) {
                view.setStroke(phase >= 2 ? Color.YELLOW : Color.BLACK);
            }
        });
        pt.play();
    }

    // ======================
    // Ataques
    // ======================

    /**
     * Ejecuta el ataque principal.
     * - Rework si {@code getClass() == Boss.class}
     * - Legacy radial para subclases
     */
    protected void performAttack() {
        if (dead) return;

        if (getClass() != Boss.class) {
            performAttackLegacy();
            return;
        }

        // Núcleo del Spreader: fan dirigido + anillo extra en fase 2
        spawnAimedFan(7 + phase * 2, 0.50, 280.0 + phase * 30.0);

        if (phase >= 2) {
            spawnRing(8, 210.0, 3.0, 1.0);
        }
    }

    /**
     * Ataque legacy: spam radial.
     */
    private void performAttackLegacy() {
        int projectiles = 8 + (phase * 2);
        for (int i = 0; i < projectiles; i++) {
            double angle = (2.0 * Math.PI / projectiles) * i;
            spawnProjectile(Math.cos(angle), Math.sin(angle), 200.0, 2.0, 1.0);
        }
    }

    // ======================
    // Helpers de proyectiles
    // ======================

    /**
     * Crea y spawnea un proyectil desde la posición actual del boss.
     */
    private void spawnProjectile(double dirX, double dirY, double speed, double radius, double damage) {
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

        double bx = view.getLayoutX();
        double by = view.getLayoutY();
        p.getView().setLayoutX(bx);
        p.getView().setLayoutY(by);

        onSpawnProjectile.accept(p);
    }

    /**
     * Disparo único en espiral.
     */
    private void spawnSpiralShot(double angle, double speed) {
        spawnProjectile(Math.cos(angle), Math.sin(angle), speed, 3.0, 1.0);
    }

    /**
     * Mini ráfaga espiral (usada como presión extra).
     */
    private void spawnMiniSpiralBurst(int count, double speed) {
        for (int i = 0; i < count; i++) {
            spiralAngle += 0.55;
            spawnSpiralShot(spiralAngle, speed);
        }
    }

    /**
     * Dispara un abanico apuntado al jugador.
     */
    private void spawnAimedFan(int shots, double spreadRadians, double speed) {
        double[] pPos = playerPos.get();

        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        double dx = pPos[0] - bx;
        double dy = pPos[1] - by;
        double base = Math.atan2(dy, dx);

        int half = shots / 2;
        for (int i = -half; i <= half; i++) {
            double t = (half == 0) ? 0.0 : (i / (double) half);
            double ang = base + t * spreadRadians;
            spawnProjectile(Math.cos(ang), Math.sin(ang), speed, 3.0, 1.0);
        }
    }

    /**
     * Dispara un anillo radial.
     */
    private void spawnRing(int projectiles, double speed, double radius, double damage) {
        for (int i = 0; i < projectiles; i++) {
            double angle = (2.0 * Math.PI / projectiles) * i;
            spawnProjectile(Math.cos(angle), Math.sin(angle), speed, radius, damage);
        }
    }

    // ======================
    // Daño, fases y muerte
    // ======================

    /**
     * Aplica daño y gestiona feedback visual y cambios de fase.
     */
    public void takeDamage(double amount) {
        if (dead) return;

        hp -= amount;

        // Flash rápido al recibir daño
        view.setFill(Color.WHITE);
        PauseTransition flash = new PauseTransition(Duration.millis(100));
        flash.setOnFinished(e -> {
            if (!dead) updateColor();
        });
        flash.play();

        if (hp <= 0.0) {
            die();
        } else {
            checkPhaseChange();
        }
    }

    /**
     * Actualiza el color base del boss después del flash.
     * Se deja simple para no alterar estética; lo puedes especializar en subclases.
     */
    protected void updateColor() {
        view.setFill(Color.DARKRED);
    }

    /**
     * Cambia a fase 2 al bajar del 50 por ciento de vida.
     */
    protected void checkPhaseChange() {
        if (phase == 1 && hp < maxHp * 0.5) {
            phase = 2;
            speed *= 1.35;
            view.setStroke(Color.YELLOW);

            // En fase 2, permite que el próximo dash llegue antes
            dashCooldown = Math.min(dashCooldown, 1.8);
        }
    }

    /**
     * Marca muerto, elimina el nodo y notifica al sistema.
     */
    protected void die() {
        if (dead) return;
        dead = true;
        parent.getChildren().remove(view);
        onDeath.accept(this);
    }

    // ======================
    // Getters y colisión
    // ======================

    public double getHp() { return hp; }
    public double getMaxHp() { return maxHp; }
    public boolean isDead() { return dead; }

    @Override public Node getView() { return view; }
    @Override public Bounds getBounds() { return view.getBoundsInParent(); }

    /**
     * Marca el boss como fuente del último golpe al jugador (para estadísticas o UI).
     */
    @Override
    public void onCollision(GameEntity other) {
        if (other instanceof Player p) {
            p.setLastHitSource(bossId);
        }
    }
}
