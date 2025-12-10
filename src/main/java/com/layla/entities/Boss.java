package com.layla.entities;

import java.util.Objects;
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
    protected final String bossId;

    protected double hp;
    protected double maxHp;
    protected double speed = 45.0;
    protected boolean dead = false;

    protected double attackTimer = 0.0;
    protected int phase = 1;

    public Boss(double x, double y, double maxHp, Pane parent,
                Supplier<double[]> playerPos,
                Consumer<Boss> onDeath,
                Consumer<GameEntity> onSpawnProjectile,
                String bossId) {

        this.maxHp = maxHp;
        this.hp = maxHp;
        this.parent = Objects.requireNonNull(parent);
        this.playerPos = Objects.requireNonNull(playerPos);
        this.onDeath = Objects.requireNonNull(onDeath);
        this.onSpawnProjectile = Objects.requireNonNull(onSpawnProjectile);
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

        // IA BÁSICA (Pisos 1-4): Perseguir y disparar
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
            performAttack();
            attackTimer = 0.0;
        }
    }

    protected void performAttack() {
        if (dead) return;
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
                ent -> parent.getChildren().remove(ent.getView()),
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
            if (!dead) updateColor(); // Delegado a método para que FinalBoss pueda cambiar color
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
            speed *= 1.5;
            view.setStroke(Color.YELLOW);
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
