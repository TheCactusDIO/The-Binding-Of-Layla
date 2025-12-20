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

public class BossCharger extends Boss {

    private enum State { CHASE, WINDUP, DASH, COOLDOWN }

    private State state = State.CHASE;

    private double windupTimer = 0.0;
    private double dashTimer = 0.0;
    private double cooldownTimer = 0.0;

    private double dashDirX = 0.0;
    private double dashDirY = 0.0;

    private double dashSpeed = 420.0;

    private double mineDropTimer = 0.0;
    private final List<Mine> mines = new ArrayList<>();

    public BossCharger(double x, double y, double maxHp, Pane parent,
                       Supplier<double[]> playerPos,
                       Consumer<Boss> onDeath,
                       Consumer<GameEntity> onSpawnProjectile,
                       Consumer<GameEntity> onRemoveProjectile,
                       String bossId) {
        super(x, y, maxHp, parent, playerPos, onDeath, onSpawnProjectile, onRemoveProjectile, bossId);

        this.view.setFill(Color.DARKGREEN);
        this.view.setStroke(Color.BLACK);
        this.view.setStrokeWidth(4.0);
        this.view.setStrokeType(StrokeType.INSIDE);
        this.view.setEffect(new DropShadow(22, Color.LIMEGREEN));

        this.speed = 40.0;
    }

    @Override
    public void update(double dt) {
        if (dead || hp <= 0) return;

        switch (state) {
            case CHASE -> updateChase(dt);
            case WINDUP -> updateWindup(dt);
            case DASH -> updateDash(dt);
            case COOLDOWN -> updateCooldown(dt);
        }

        // Ataque extra (shockwave) solo a veces, aparte del dash
        attackTimer += dt;
        if (attackTimer > 2.4 && state == State.CHASE) {
            performAttack();
            attackTimer = 0.0;
        }
    }

    private void updateChase(double dt) {
        double[] pPos = playerPos.get();
        double dx = pPos[0] - view.getLayoutX();
        double dy = pPos[1] - view.getLayoutY();
        double dist = Math.sqrt(dx*dx + dy*dy);
        if (dist < 0.001) dist = 0.001;

        view.setLayoutX(view.getLayoutX() + (dx / dist) * speed * dt);
        view.setLayoutY(view.getLayoutY() + (dy / dist) * speed * dt);

        // Decide empezar dash
        if (cooldownTimer <= 0.0) {
            // windup si está a rango razonable
            if (dist < 520.0) {
                state = State.WINDUP;
                windupTimer = (phase == 1) ? 0.55 : 0.40;
                // “tell”
                view.setFill(Color.YELLOWGREEN);
            }
        } else {
            cooldownTimer -= dt;
        }
    }

    private void updateWindup(double dt) {
        windupTimer -= dt;
        if (windupTimer <= 0.0) {
            // fijar dirección hacia el player al inicio del dash
            double[] pPos = playerPos.get();
            double dx = pPos[0] - view.getLayoutX();
            double dy = pPos[1] - view.getLayoutY();
            double dist = Math.sqrt(dx*dx + dy*dy);
            if (dist < 0.001) dist = 0.001;

            dashDirX = dx / dist;
            dashDirY = dy / dist;

            state = State.DASH;
            dashTimer = (phase == 1) ? 0.65 : 0.85;

            mineDropTimer = 0.0;
            view.setFill(Color.DARKGREEN);
        }
    }

    private void updateDash(double dt) {
        dashTimer -= dt;

        // Mover dash
        view.setLayoutX(view.getLayoutX() + dashDirX * dashSpeed * dt);
        view.setLayoutY(view.getLayoutY() + dashDirY * dashSpeed * dt);

        // Drop mines durante el dash
        mineDropTimer -= dt;
        if (mineDropTimer <= 0.0) {
            mineDropTimer = (phase == 1) ? 0.18 : 0.12;
            spawnMine(view.getLayoutX(), view.getLayoutY());
        }

        if (dashTimer <= 0.0) {
            // Shockwave al finalizar dash
            doShockwave();
            state = State.COOLDOWN;
            cooldownTimer = (phase == 1) ? 1.2 : 0.9;
        }
    }

    private void updateCooldown(double dt) {
        cooldownTimer -= dt;
        if (cooldownTimer <= 0.0) {
            state = State.CHASE;
        }
    }

    @Override
    protected void performAttack() {
        // ataque “pequeño” mientras chase: 6 balas lentas en abanico hacia el player
        double[] p = playerPos.get();
        double bx = view.getLayoutX();
        double by = view.getLayoutY();
        double dx = p[0] - bx;
        double dy = p[1] - by;
        double dist = Math.sqrt(dx*dx + dy*dy);
        if (dist < 0.001) dist = 0.001;

        double aimX = dx / dist;
        double aimY = dy / dist;

        int shots = (phase == 1) ? 5 : 7;
        double spreadDeg = 35.0;

        for (int i = 0; i < shots; i++) {
            int mid = shots / 2;
            int off = i - mid;

            double a = Math.toRadians(off * (spreadDeg / mid));
            double rx = aimX * Math.cos(a) - aimY * Math.sin(a);
            double ry = aimX * Math.sin(a) + aimY * Math.cos(a);

            Projectile proj = new Projectile(
                rx, ry,
                210.0, 3.2, 1.0,
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

    private void doShockwave() {
        int count = (phase == 1) ? 10 : 14;
        double spd = (phase == 1) ? 180.0 : 210.0;

        for (int i = 0; i < count; i++) {
            double a = (2 * Math.PI / count) * i;
            double vx = Math.cos(a);
            double vy = Math.sin(a);

            Projectile proj = new Projectile(
                vx, vy,
                spd, 3.0, 1.0,
                true,
                parent,
                onRemoveProjectile,
                this,
                bossId
            );
            proj.getView().setLayoutX(view.getLayoutX());
            proj.getView().setLayoutY(view.getLayoutY());
            onSpawnProjectile.accept(proj);
        }
    }

    private void spawnMine(double x, double y) {
        Mine m = new Mine(
            x, y,
            (phase == 1) ? 1.15 : 0.9,
            (phase == 1) ? 8 : 10,
            (phase == 1) ? 170.0 : 200.0,
            parent,
            onSpawnProjectile,
            onRemoveProjectile,
            this,
            bossId
        );
        mines.add(m);
        onSpawnProjectile.accept(m);
    }

    @Override
    protected void die() {
        // limpiar minas visuales
        for (Mine m : new ArrayList<>(mines)) {
            m.forceDestroy();
        }
        mines.clear();
        super.die();
    }

    @Override
    protected void updateColor() {
        view.setFill(Color.DARKGREEN);
    }

    // === Mina: tras delay explota en anillo ===
    private static final class Mine implements GameEntity {
        private final Pane parent;
        private final Circle view;
        private final Consumer<GameEntity> onSpawn;
        private final Consumer<GameEntity> onRemove;
        private final Boss owner;
        private final String bossId;

        private double timer;
        private final int ringCount;
        private final double ringSpeed;

        Mine(double x, double y,
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

            this.timer = delay;
            this.ringCount = ringCount;
            this.ringSpeed = ringSpeed;

            this.view = new Circle(9, Color.ORANGE);
            this.view.setStroke(Color.DARKRED);
            this.view.setStrokeWidth(2);
            this.view.setEffect(new DropShadow(14, Color.ORANGERED));
            this.view.setLayoutX(x);
            this.view.setLayoutY(y);
            parent.getChildren().add(this.view);
        }

        @Override
        public void update(double dt) {
            timer -= dt;
            if (timer <= 0.0) {
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

        void forceDestroy() {
            destroy();
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
