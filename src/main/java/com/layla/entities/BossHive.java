package com.layla.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.core.GameEntity;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeType;

/**
 * Boss "Hive":
 * - Se mantiene a una distancia objetivo del jugador.
 * - Dispara ráfagas en espiral.
 * - Spawnea drones que orbitan y disparan hacia afuera.
 */
public class BossHive extends Boss {

    // Ajustes del boss
    private static final int DRONES_COUNT = 4;
    private static final double DRONES_ORBIT_RADIUS = 95.0;

    // Lista de drones vivos (para limpiar en die()).
    private final List<Drone> drones = new ArrayList<>();

    // Para spawnear drones una sola vez.
    private boolean dronesSpawned = false;

    // Ángulo acumulado de la espiral.
    private double spiralAngle = 0.0;

    public BossHive(
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
        super(x, y, maxHp, parent, playerPos, onDeath, onSpawnProjectile, onRemoveProjectile, bossId);

        this.view.setFill(Color.PURPLE);
        this.view.setStroke(Color.BLACK);
        this.view.setStrokeWidth(4.0);
        this.view.setStrokeType(StrokeType.INSIDE);
        this.view.setEffect(new DropShadow(24, Color.MEDIUMPURPLE));

        this.speed = 28.0; // más lento
    }

    /**
     * Update principal.
     * - Si hp cae a 0 por cualquier motivo, llama a die() (robustez).
     * - Spawnea drones una sola vez.
     * - Se mueve para mantener distancia media.
     * - Dispara espiral.
     */
    @Override
    public void update(double dt) {
        if (dead) return;

        // Robustez: evita quedar "zombie" si hp llega a 0 fuera de takeDamage()
        if (hp <= 0.0) {
            die();
            return;
        }

        if (!dronesSpawned) {
            dronesSpawned = true;
            spawnDrones();
        }

        updateMovement(dt);
        updateAttacks(dt);
    }

    /**
     * Movimiento: se acerca o se aleja para mantenerse alrededor de una distancia objetivo.
     */
    private void updateMovement(double dt) {
        double[] p = playerPos.get();

        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        double dx = p[0] - bx;
        double dy = p[1] - by;

        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.001) dist = 0.001;

        double targetDist = (phase == 1) ? 280.0 : 320.0;

        // move = 1 => acercarse, -1 => alejarse, 0 => quedarse
        double move;
        if (dist > targetDist + 40.0) move = 1.0;
        else if (dist < targetDist - 40.0) move = -1.0;
        else move = 0.0;

        if (move != 0.0) {
            view.setLayoutX(bx + (dx / dist) * speed * dt * move);
            view.setLayoutY(by + (dy / dist) * speed * dt * move);
        }
    }

    /**
     * Gestiona el timer del ataque y ejecuta la espiral.
     */
    private void updateAttacks(double dt) {
        attackTimer += dt;
        if (attackTimer > 1.9) {
            performAttack();
            attackTimer = 0.0;
        }
    }

    /**
     * Ataque en espiral.
     */
    @Override
    protected void performAttack() {
        if (dead) return;

        int count = (phase == 1) ? 10 : 14;
        double projSpeed = (phase == 1) ? 190.0 : 220.0;

        // Cada ataque gira un poco
        spiralAngle += (phase == 1) ? 0.35 : 0.55;

        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        for (int i = 0; i < count; i++) {
            double a = spiralAngle + (2.0 * Math.PI / count) * i;
            spawnProjectile(Math.cos(a), Math.sin(a), projSpeed, 3.0, 1.0, bx, by);
        }
    }

    /**
     * Spawnea los drones orbitando alrededor del boss.
     * Importante:
     * - Cada drone avisa cuando se destruye para quitarse de la lista "drones".
     */
    private void spawnDrones() {
        for (int i = 0; i < DRONES_COUNT; i++) {
            double a = (2.0 * Math.PI / DRONES_COUNT) * i;

            Drone d = new Drone(
                    this,
                    a,
                    DRONES_ORBIT_RADIUS,
                    parent,
                    onSpawnProjectile,
                    onRemoveProjectile,
                    bossId,
                    destroyed -> drones.remove(destroyed) // ✅ evita leaks y dobles referencias
            );

            drones.add(d);
            onSpawnProjectile.accept(d);
        }
    }

    /**
     * Helper centralizado para crear y spawnear proyectiles sin duplicar código.
     */
    private void spawnProjectile(double dirX, double dirY, double speed, double radius, double damage, double x, double y) {
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
        p.getView().setLayoutX(x);
        p.getView().setLayoutY(y);
        onSpawnProjectile.accept(p);
    }

    /**
     * Al morir:
     * - Destruye drones vivos (idempotente).
     * - Limpia lista.
     * - Llama a super.die().
     */
    @Override
    protected void die() {
        for (Drone d : new ArrayList<>(drones)) {
            d.forceDestroy();
        }
        drones.clear();
        super.die();
    }

    /**
     * Color normal tras daño.
     */
    @Override
    protected void updateColor() {
        view.setFill(Color.PURPLE);
    }

    // =========================================================
    // Drone orbitando que dispara hacia afuera
    // =========================================================

    /**
     * Drone:
     * - Orbita al boss.
     * - Dispara hacia afuera según su ángulo.
     *
     * Nota: destrucción idempotente para evitar dobles onRemove().
     */
    private static final class Drone implements GameEntity {

        private final BossHive boss;
        private final Pane parent;
        private final Circle view;

        private final Consumer<GameEntity> onSpawn;
        private final Consumer<GameEntity> onRemove;

        private final String bossId;
        private final Consumer<Drone> onDestroyed;

        private double angle;
        private final double radius;

        private double fireTimer = 0.0;
        private boolean destroyed = false;

        Drone(
                BossHive boss,
                double startAngle,
                double radius,
                Pane parent,
                Consumer<GameEntity> onSpawn,
                Consumer<GameEntity> onRemove,
                String bossId,
                Consumer<Drone> onDestroyed
        ) {
            this.boss = Objects.requireNonNull(boss, "boss");
            this.parent = Objects.requireNonNull(parent, "parent");
            this.onSpawn = Objects.requireNonNull(onSpawn, "onSpawn");
            this.onRemove = Objects.requireNonNull(onRemove, "onRemove");
            this.bossId = Objects.requireNonNull(bossId, "bossId");
            this.onDestroyed = (onDestroyed != null) ? onDestroyed : d -> {};

            this.angle = startAngle;
            this.radius = radius;

            this.view = new Circle(10, Color.HOTPINK);
            this.view.setStroke(Color.BLACK);
            this.view.setStrokeWidth(2);
            this.view.setStrokeType(StrokeType.INSIDE);
            this.view.setEffect(new DropShadow(16, Color.PINK));

            parent.getChildren().add(this.view);
            syncPos();
        }

        @Override
        public void update(double dt) {
            if (destroyed) return;

            // Si el boss ya no existe, el drone se auto-destruye una sola vez
            if (boss.dead || boss.hp <= 0.0) {
                forceDestroy();
                return;
            }

            // Orbitar
            double rotSpeed = (boss.phase == 1) ? 1.4 : 2.1;
            angle += rotSpeed * dt;
            syncPos();

            // Disparar
            fireTimer += dt;
            double interval = (boss.phase == 1) ? 1.25 : 0.85;
            if (fireTimer >= interval) {
                fireTimer = 0.0;
                shoot();
            }
        }

        /**
         * Sincroniza posición orbital respecto al boss.
         */
        private void syncPos() {
            double bx = boss.view.getLayoutX();
            double by = boss.view.getLayoutY();
            view.setLayoutX(bx + Math.cos(angle) * radius);
            view.setLayoutY(by + Math.sin(angle) * radius);
        }

        /**
         * Dispara hacia afuera (dirección del ángulo actual).
         */
        private void shoot() {
            double vx = Math.cos(angle);
            double vy = Math.sin(angle);

            double speed = (boss.phase == 1) ? 210.0 : 245.0;

            Projectile p = new Projectile(
                    vx, vy,
                    speed, 3.0, 1.0,
                    true,
                    parent,
                    onRemove,
                    boss,
                    bossId
            );

            p.getView().setLayoutX(view.getLayoutX());
            p.getView().setLayoutY(view.getLayoutY());

            onSpawn.accept(p);
        }

        /**
         * Destruye el drone de forma segura e idempotente.
         */
        void forceDestroy() {
            if (destroyed) return;
            destroyed = true;

            parent.getChildren().remove(view);
            onRemove.accept(this);

            // ✅ importante: quitarse de la lista del boss
            onDestroyed.accept(this);
        }

        @Override
        public Node getView() {
            return view;
        }

        @Override
        public Bounds getBounds() {
            double r = view.getRadius();
            double x = view.getLayoutX();
            double y = view.getLayoutY();
            return new BoundingBox(x - r, y - r, r * 2, r * 2);
        }
    }
}
