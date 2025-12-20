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

public class BossHive extends Boss {

    private final List<Drone> drones = new ArrayList<>();
    private boolean dronesSpawned = false;

    // ángulo acumulado para espiral
    private double spiralAngle = 0.0;

    public BossHive(double x, double y, double maxHp, Pane parent,
                    Supplier<double[]> playerPos,
                    Consumer<Boss> onDeath,
                    Consumer<GameEntity> onSpawnProjectile,
                    Consumer<GameEntity> onRemoveProjectile,
                    String bossId) {
        super(x, y, maxHp, parent, playerPos, onDeath, onSpawnProjectile, onRemoveProjectile, bossId);

        this.view.setFill(Color.PURPLE);
        this.view.setStroke(Color.BLACK);
        this.view.setStrokeWidth(4.0);
        this.view.setStrokeType(StrokeType.INSIDE);
        this.view.setEffect(new DropShadow(24, Color.MEDIUMPURPLE));

        this.speed = 28.0; // más lento
    }

    @Override
    public void update(double dt) {
        if (dead || hp <= 0) return;

        if (!dronesSpawned) {
            dronesSpawned = true;
            spawnDrones();
        }

        // Movimiento: se acerca un poco, pero intenta mantenerse a distancia media
        double[] p = playerPos.get();
        double bx = view.getLayoutX();
        double by = view.getLayoutY();
        double dx = p[0] - bx;
        double dy = p[1] - by;
        double dist = Math.sqrt(dx*dx + dy*dy);
        if (dist < 0.001) dist = 0.001;

        double targetDist = (phase == 1) ? 280.0 : 320.0;
        double move = 0.0;
        if (dist > targetDist + 40) move = 1.0;
        else if (dist < targetDist - 40) move = -1.0;

        if (move != 0.0) {
            view.setLayoutX(bx + (dx / dist) * speed * dt * move);
            view.setLayoutY(by + (dy / dist) * speed * dt * move);
        }

        // Ataque: espiral
        attackTimer += dt;
        if (attackTimer > 1.9) {
            performAttack();
            attackTimer = 0.0;
        }
    }

    @Override
    protected void performAttack() {
        if (dead) return;

        int count = (phase == 1) ? 10 : 14;
        double projSpeed = (phase == 1) ? 190.0 : 220.0;

        // espiral: cada ataque gira un poco
        spiralAngle += (phase == 1) ? 0.35 : 0.55;

        for (int i = 0; i < count; i++) {
            double a = spiralAngle + (2 * Math.PI / count) * i;
            double vx = Math.cos(a);
            double vy = Math.sin(a);

            Projectile p = new Projectile(
                vx, vy,
                projSpeed, 3.0, 1.0,
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

    private void spawnDrones() {
        int n = 4;
        double radius = 95.0;

        for (int i = 0; i < n; i++) {
            double a = (2 * Math.PI / n) * i;
            Drone d = new Drone(
                this,
                a,
                radius,
                parent,
                onSpawnProjectile,
                onRemoveProjectile,
                bossId
            );
            drones.add(d);
            onSpawnProjectile.accept(d);
        }
    }

    @Override
    protected void die() {
        for (Drone d : new ArrayList<>(drones)) {
            d.forceDestroy();
        }
        drones.clear();
        super.die();
    }

    @Override
    protected void updateColor() {
        view.setFill(Color.PURPLE);
    }

    // === Drone orbitando que dispara hacia afuera/rotando ===
    private static final class Drone implements GameEntity {
        private final BossHive boss;
        private final Pane parent;
        private final Circle view;
        private final Consumer<GameEntity> onSpawn;
        private final Consumer<GameEntity> onRemove;
        private final String bossId;

        private double angle;
        private final double radius;

        private double fireTimer = 0.0;

        Drone(BossHive boss,
              double startAngle,
              double radius,
              Pane parent,
              Consumer<GameEntity> onSpawn,
              Consumer<GameEntity> onRemove,
              String bossId) {

            this.boss = Objects.requireNonNull(boss);
            this.parent = Objects.requireNonNull(parent);
            this.onSpawn = Objects.requireNonNull(onSpawn);
            this.onRemove = Objects.requireNonNull(onRemove);
            this.bossId = Objects.requireNonNull(bossId);

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
            if (boss.dead || boss.hp <= 0) {
                forceDestroy();
                return;
            }

            // orbitar
            double rotSpeed = (boss.phase == 1) ? 1.4 : 2.1;
            angle += rotSpeed * dt;

            syncPos();

            // disparar
            fireTimer += dt;
            double interval = (boss.phase == 1) ? 1.25 : 0.85;
            if (fireTimer >= interval) {
                fireTimer = 0.0;
                shoot();
            }
        }

        private void syncPos() {
            double bx = boss.view.getLayoutX();
            double by = boss.view.getLayoutY();
            double x = bx + Math.cos(angle) * radius;
            double y = by + Math.sin(angle) * radius;
            view.setLayoutX(x);
            view.setLayoutY(y);
        }

        private void shoot() {
            // dispara hacia “afuera” (misma dirección que su ángulo)
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

        void forceDestroy() {
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
