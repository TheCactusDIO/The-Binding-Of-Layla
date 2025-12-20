package com.layla.entities;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
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

public class BossSniper extends Boss {

    private final ThreadLocalRandom rng = ThreadLocalRandom.current();

    // Mantiene distancia
    private double desiredMinDist = 220.0;
    private double desiredMaxDist = 360.0;

    private int attackCount = 0;

    public BossSniper(double x, double y, double maxHp, Pane parent,
                      Supplier<double[]> playerPos,
                      Consumer<Boss> onDeath,
                      Consumer<GameEntity> onSpawnProjectile,
                      Consumer<GameEntity> onRemoveProjectile,
                      String bossId) {
        super(x, y, maxHp, parent, playerPos, onDeath, onSpawnProjectile, onRemoveProjectile, bossId);

        // Look distinto
        this.view.setFill(Color.DARKSLATEBLUE);
        this.view.setStroke(Color.LIGHTBLUE);
        this.view.setStrokeWidth(4.0);
        this.view.setStrokeType(StrokeType.INSIDE);
        this.view.setEffect(new DropShadow(22, Color.DEEPSKYBLUE));

        this.speed = 35.0;
    }

    @Override
    public void update(double dt) {
        if (dead || hp <= 0) return;

        // Movimiento: se aleja si estás muy cerca, si no “orbita” ligeramente
        double[] p = playerPos.get();
        double bx = view.getLayoutX();
        double by = view.getLayoutY();
        double dx = p[0] - bx;
        double dy = p[1] - by;
        double dist = Math.sqrt(dx*dx + dy*dy);
        if (dist < 0.001) dist = 0.001;

        double moveX = 0.0;
        double moveY = 0.0;

        if (dist < desiredMinDist) {
            // huye
            moveX = -(dx / dist);
            moveY = -(dy / dist);
        } else if (dist > desiredMaxDist) {
            // se acerca un poco para no quedarse fuera
            moveX = (dx / dist);
            moveY = (dy / dist);
        } else {
            // strafe/orbita: perpendicular al vector al player
            double px = -(dy / dist);
            double py = (dx / dist);
            // alterna dirección a veces
            double dir = (attackCount % 2 == 0) ? 1.0 : -1.0;
            moveX = px * dir;
            moveY = py * dir;
        }

        view.setLayoutX(bx + moveX * speed * dt);
        view.setLayoutY(by + moveY * speed * dt);

        // Ataque
        attackTimer += dt;
        if (attackTimer > 1.7) {
            performAttack();
            attackTimer = 0.0;
        }
    }

    @Override
    protected void performAttack() {
        if (dead) return;

        attackCount++;

        // 1 de cada 3 ataques: dispara un "orb" lento que explota en anillo
        if (attackCount % 3 == 0) {
            spawnExplodingOrb();
            return;
        }

        // Ráfaga apuntada
        double[] p = playerPos.get();
        double bx = view.getLayoutX();
        double by = view.getLayoutY();
        double dx = p[0] - bx;
        double dy = p[1] - by;
        double dist = Math.sqrt(dx*dx + dy*dy);
        if (dist < 0.001) dist = 0.001;

        double aimX = dx / dist;
        double aimY = dy / dist;

        int burst = (phase == 1) ? 3 : 5;
        double spreadDeg = (phase == 1) ? 8.0 : 12.0;

        for (int i = 0; i < burst; i++) {
            // offsets: -2,-1,0,1,2 (para burst=5) o -1,0,1 (para burst=3)
            int mid = burst / 2;
            int off = i - mid;

            double a = Math.toRadians(off * spreadDeg);

            // Rotar vector aim por ángulo a
            double rx = aimX * Math.cos(a) - aimY * Math.sin(a);
            double ry = aimX * Math.sin(a) + aimY * Math.cos(a);

            Projectile proj = new Projectile(
                rx, ry,
                330.0, 3.0, 1.0,
                true,
                parent,
                onRemoveProjectile,
                this,
                bossId
            );
            proj.getView().setLayoutX(bx);
            proj.getView().setLayoutY(by);
            onSpawnProjectile.accept(proj);
        }
    }

    private void spawnExplodingOrb() {
        double[] p = playerPos.get();
        double bx = view.getLayoutX();
        double by = view.getLayoutY();
        double dx = p[0] - bx;
        double dy = p[1] - by;
        double dist = Math.sqrt(dx*dx + dy*dy);
        if (dist < 0.001) dist = 0.001;

        double dirX = dx / dist;
        double dirY = dy / dist;

        ExplodingOrb orb = new ExplodingOrb(
            bx, by,
            dirX, dirY,
            110.0,
            (phase == 1) ? 0.9 : 0.7, // delay hasta explotar
            (phase == 1) ? 10 : 14,    // balas
            (phase == 1) ? 190.0 : 220.0,
            parent,
            onSpawnProjectile,
            onRemoveProjectile,
            this,
            bossId
        );

        onSpawnProjectile.accept(orb);
    }

    @Override
    protected void updateColor() {
        view.setFill(Color.DARKSLATEBLUE);
    }

    // === Orb que explota en anillo ===
    private static final class ExplodingOrb implements GameEntity {
        private final Pane parent;
        private final Circle view;
        private final Consumer<GameEntity> onSpawn;
        private final Consumer<GameEntity> onRemove;
        private final Boss owner;
        private final String bossId;

        private final double dirX, dirY;
        private final double speed;
        private double timeLeft;
        private final int ringCount;
        private final double ringSpeed;

        ExplodingOrb(double x, double y,
                     double dirX, double dirY,
                     double speed,
                     double delay,
                     int ringCount,
                     double ringSpeed,
                     Pane parent,
                     Consumer<GameEntity> onSpawn,
                     Consumer<GameEntity> onRemove,
                     Boss owner,
                     String bossId) {

            this.parent = Objects.requireNonNull(parent);
            this.onSpawn = Objects.requireNonNull(onSpawn);
            this.onRemove = Objects.requireNonNull(onRemove);
            this.owner = Objects.requireNonNull(owner);
            this.bossId = Objects.requireNonNull(bossId);

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
            // mover
            view.setLayoutX(view.getLayoutX() + dirX * speed * dt);
            view.setLayoutY(view.getLayoutY() + dirY * speed * dt);

            timeLeft -= dt;
            if (timeLeft <= 0.0) {
                explode();
                destroy();
            }
        }

        private void explode() {
            for (int i = 0; i < ringCount; i++) {
                double a = (2 * Math.PI / ringCount) * i;
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
                p.getView().setLayoutX(view.getLayoutX());
                p.getView().setLayoutY(view.getLayoutY());
                onSpawn.accept(p);
            }
        }

        private void destroy() {
            parent.getChildren().remove(view);
            onRemove.accept(this);
        }

        @Override public Node getView() { return view; }

        @Override
        public Bounds getBounds() {
            return new BoundingBox(view.getLayoutX() - view.getRadius(), view.getLayoutY() - view.getRadius(),
                    view.getRadius() * 2, view.getRadius() * 2);
        }
    }
}
