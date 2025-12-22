package com.layla.entities;

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
 * Boss "Sniper":
 * - Mantiene distancia del jugador (huye si estás cerca, se acerca si estás lejos).
 * - En rango medio, hace strafe/orbita lateral.
 * - Ataque principal: ráfaga apuntada.
 * - Cada 3 ataques: lanza un orbe lento que explota en anillo.
 */
public class BossSniper extends Boss {

    // Distancias deseadas respecto al jugador.
    private static final double DESIRED_MIN_DIST = 220.0;
    private static final double DESIRED_MAX_DIST = 360.0;

    // Timing del ataque.
    private static final double ATTACK_INTERVAL_SEC = 1.7;

    // Contador de ataques para alternar comportamiento y patrón.
    private int attackCount = 0;

    public BossSniper(
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

        // Look distinto
        this.view.setFill(Color.DARKSLATEBLUE);
        this.view.setStroke(Color.LIGHTBLUE);
        this.view.setStrokeWidth(4.0);
        this.view.setStrokeType(StrokeType.INSIDE);
        this.view.setEffect(new DropShadow(22, Color.DEEPSKYBLUE));

        this.speed = 35.0;
    }

    /**
     * Update principal:
     * - Robusto: si hp cae a 0 por cualquier vía, muere.
     * - Movimiento según distancia.
     * - Ataque por timer.
     */
    @Override
    public void update(double dt) {
        if (dead) return;

        // Robustez: evita quedar "zombie" si hp llega a 0 fuera de takeDamage()
        if (hp <= 0.0) {
            die();
            return;
        }

        updateMovement(dt);
        updateAttackTimer(dt);
    }

    /**
     * Movimiento:
     * - Si está muy cerca: se aleja.
     * - Si está muy lejos: se acerca.
     * - Si está en rango: orbita/strafe (perpendicular al vector hacia el jugador).
     */
    private void updateMovement(double dt) {
        double[] p = playerPos.get();

        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        double dx = p[0] - bx;
        double dy = p[1] - by;

        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.001) dist = 0.001;

        double ndx = dx / dist;
        double ndy = dy / dist;

        double moveX;
        double moveY;

        if (dist < DESIRED_MIN_DIST) {
            // huye
            moveX = -ndx;
            moveY = -ndy;
        } else if (dist > DESIRED_MAX_DIST) {
            // se acerca un poco para no quedarse fuera
            moveX = ndx;
            moveY = ndy;
        } else {
            // strafe/orbita: perpendicular al vector al player
            double px = -ndy;
            double py = ndx;

            // alterna dirección según el número de ataques ya hechos (patrón simple)
            double dir = (attackCount % 2 == 0) ? 1.0 : -1.0;

            moveX = px * dir;
            moveY = py * dir;
        }

        view.setLayoutX(bx + moveX * speed * dt);
        view.setLayoutY(by + moveY * speed * dt);
    }

    /**
     * Suma el timer y dispara cuando toca.
     */
    private void updateAttackTimer(double dt) {
        attackTimer += dt;
        if (attackTimer > ATTACK_INTERVAL_SEC) {
            performAttack();
            attackTimer = 0.0;
        }
    }

    /**
     * Ataque del sniper:
     * - Cada 3 ataques: orbe lento que explota en anillo.
     * - Si no: ráfaga apuntada con leve spread.
     */
    @Override
    protected void performAttack() {
        if (dead) return;

        attackCount++;

        // 1 de cada 3 ataques
        if (attackCount % 3 == 0) {
            spawnExplodingOrb();
            return;
        }

        spawnAimedBurst();
    }

    /**
     * Ráfaga apuntada al jugador, con spread.
     */
    private void spawnAimedBurst() {
        double[] p = playerPos.get();
        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        double dx = p[0] - bx;
        double dy = p[1] - by;

        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.001) dist = 0.001;

        double aimX = dx / dist;
        double aimY = dy / dist;

        int burst = (phase == 1) ? 3 : 5;
        double spreadDeg = (phase == 1) ? 8.0 : 12.0;

        int mid = burst / 2; // burst 3 -> 1, burst 5 -> 2

        for (int i = 0; i < burst; i++) {
            int off = i - mid;

            double a = Math.toRadians(off * spreadDeg);

            // Rotar (aimX, aimY) por ángulo a
            double rx = aimX * Math.cos(a) - aimY * Math.sin(a);
            double ry = aimX * Math.sin(a) + aimY * Math.cos(a);

            spawnProjectile(rx, ry, 330.0, 3.0, 1.0, bx, by, this);
        }
    }

    /**
     * Spawnea el orbe que explota en anillo tras un delay.
     */
    private void spawnExplodingOrb() {
        double[] p = playerPos.get();
        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        double dx = p[0] - bx;
        double dy = p[1] - by;

        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.001) dist = 0.001;

        double dirX = dx / dist;
        double dirY = dy / dist;

        ExplodingOrb orb = new ExplodingOrb(
                bx, by,
                dirX, dirY,
                110.0,
                (phase == 1) ? 0.9 : 0.7,   // delay hasta explotar
                (phase == 1) ? 10 : 14,     // balas
                (phase == 1) ? 190.0 : 220.0,
                parent,
                onSpawnProjectile,
                onRemoveProjectile,
                this,
                bossId
        );

        onSpawnProjectile.accept(orb);
    }

    /**
     * Helper centralizado para crear y spawnear proyectiles.
     */
    private void spawnProjectile(
            double dirX,
            double dirY,
            double speed,
            double radius,
            double damage,
            double x,
            double y,
            Boss owner
    ) {
        Projectile proj = new Projectile(
                dirX, dirY,
                speed,
                radius, damage,
                true,
                parent,
                onRemoveProjectile,
                owner,
                bossId
        );
        proj.getView().setLayoutX(x);
        proj.getView().setLayoutY(y);
        onSpawnProjectile.accept(proj);
    }

    /**
     * Color normal tras daño.
     */
    @Override
    protected void updateColor() {
        view.setFill(Color.DARKSLATEBLUE);
    }

    // =========================================================
    // Orb que explota en anillo
    // =========================================================

    /**
     * Orbe:
     * - Se mueve hacia delante.
     * - Tras un delay, explota en anillo y se destruye.
     *
     * Importante: destrucción idempotente para evitar dobles onRemove().
     */
    private static final class ExplodingOrb implements GameEntity {

        private final Pane parent;
        private final Circle view;

        private final Consumer<GameEntity> onSpawn;
        private final Consumer<GameEntity> onRemove;

        private final Boss owner;
        private final String bossId;

        private final double dirX;
        private final double dirY;
        private final double speed;

        private double timeLeft;

        private final int ringCount;
        private final double ringSpeed;

        private boolean destroyed = false;

        ExplodingOrb(
                double x,
                double y,
                double dirX,
                double dirY,
                double speed,
                double delay,
                int ringCount,
                double ringSpeed,
                Pane parent,
                Consumer<GameEntity> onSpawn,
                Consumer<GameEntity> onRemove,
                Boss owner,
                String bossId
        ) {
            this.parent = Objects.requireNonNull(parent, "parent");
            this.onSpawn = Objects.requireNonNull(onSpawn, "onSpawn");
            this.onRemove = Objects.requireNonNull(onRemove, "onRemove");
            this.owner = Objects.requireNonNull(owner, "owner");
            this.bossId = Objects.requireNonNull(bossId, "bossId");

            this.dirX = dirX;
            this.dirY = dirY;
            this.speed = speed;

            this.timeLeft = delay;
            this.ringCount = ringCount;
            this.ringSpeed = ringSpeed;

            this.view = new Circle(10, Color.CYAN);
            this.view.setStroke(Color.DARKBLUE);
            this.view.setStrokeWidth(2);
            this.view.setEffect(new DropShadow(14, Color.DEEPSKYBLUE));
            this.view.setLayoutX(x);
            this.view.setLayoutY(y);

            parent.getChildren().add(this.view);
        }

        @Override
        public void update(double dt) {
            if (destroyed) return;

            // mover
            view.setLayoutX(view.getLayoutX() + dirX * speed * dt);
            view.setLayoutY(view.getLayoutY() + dirY * speed * dt);

            timeLeft -= dt;
            if (timeLeft <= 0.0) {
                explode();
                destroy();
            }
        }

        /**
         * Explota en anillo (proyectiles radiales).
         */
        private void explode() {
            double x = view.getLayoutX();
            double y = view.getLayoutY();

            for (int i = 0; i < ringCount; i++) {
                double a = (2.0 * Math.PI / ringCount) * i;
                double vx = Math.cos(a);
                double vy = Math.sin(a);

                Projectile p = new Projectile(
                        vx, vy,
                        ringSpeed, 3.0, 1.0,
                        true,
                        parent,
                        onRemove,
                        owner,
                        bossId
                );
                p.getView().setLayoutX(x);
                p.getView().setLayoutY(y);
                onSpawn.accept(p);
            }
        }

        /**
         * Destruye el orbe de forma segura e idempotente.
         */
        private void destroy() {
            if (destroyed) return;
            destroyed = true;

            parent.getChildren().remove(view);
            onRemove.accept(this);
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
