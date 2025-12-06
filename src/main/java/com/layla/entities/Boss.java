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

    private final Pane parent;
    private final Circle view;
    private final Supplier<double[]> playerPos;
    private final Consumer<Boss> onDeath;
    private final Consumer<GameEntity> onSpawnProjectile;

    private double hp;
    private double maxHp;
    private double speed = 45.0;

    // Lógica de ataque
    private double attackTimer = 0.0;
    private int phase = 1;

    public Boss(double x, double y, double maxHp, Pane parent,
                Supplier<double[]> playerPos,
                Consumer<Boss> onDeath,
                Consumer<GameEntity> onSpawnProjectile) {

        this.maxHp = maxHp;
        this.hp = maxHp;
        this.parent = Objects.requireNonNull(parent);
        this.playerPos = Objects.requireNonNull(playerPos);
        this.onDeath = Objects.requireNonNull(onDeath);
        this.onSpawnProjectile = Objects.requireNonNull(onSpawnProjectile);

        // Visual: Un círculo grande y amenazante
        this.view = new Circle(40.0, Color.DARKRED);
        this.view.setStroke(Color.BLACK);
        this.view.setStrokeWidth(4.0);
        this.view.setStrokeType(StrokeType.INSIDE);
        // Efecto de "Boss": sombra roja
        this.view.setEffect(new javafx.scene.effect.DropShadow(20, Color.RED));

        this.view.setLayoutX(x);
        this.view.setLayoutY(y);

        parent.getChildren().add(this.view);
    }

    @Override
    public void update(double dt) {
        if (hp <= 0) return;

        // 1. Movimiento: Persecución lenta pero implacable
        double[] pPos = playerPos.get();
        double dx = pPos[0] - view.getLayoutX();
        double dy = pPos[1] - view.getLayoutY();
        double dist = Math.sqrt(dx*dx + dy*dy);

        if (dist > 1.0) {
            view.setLayoutX(view.getLayoutX() + (dx / dist) * speed * dt);
            view.setLayoutY(view.getLayoutY() + (dy / dist) * speed * dt);
        }

        // 2. Ataques (Patrón simple: disparo radial cada 2s)
        attackTimer += dt;
        if (attackTimer > 2.0) {
            performAttack();
            attackTimer = 0.0;
        }
    }

    private void performAttack() {
        // Dispara 8 proyectiles en círculo
        int projectiles = 8 + (phase * 2); // Más proyectiles en fases avanzadas
        for (int i = 0; i < projectiles; i++) {
            double angle = (2 * Math.PI / projectiles) * i;
            double dirX = Math.cos(angle);
            double dirY = Math.sin(angle);

            // Creamos proyectil enemigo (usamos la clase Projectile existente)
            Projectile p = new Projectile(
                dirX, dirY,
                200.0, 3.0, 1.0, // velocidad, vida, daño
                true, // isFromEnemy
                parent,
                ent -> parent.getChildren().remove(ent.getView()), // Simple remove callback
                this
            );

            // Salen del centro del boss
            p.getView().setLayoutX(view.getLayoutX());
            p.getView().setLayoutY(view.getLayoutY());

            onSpawnProjectile.accept(p);
        }
    }

    public void takeDamage(double amount) {
        hp -= amount;

        // Feedback visual de daño (brillo blanco breve)
        view.setFill(Color.WHITE);

        // 🛠️ CORRECCIÓN: Separamos la creación de la animación de su ejecución
        PauseTransition flash = new PauseTransition(Duration.millis(100));
        flash.setOnFinished(e -> {
            // Si estamos en fase 2, vuelve a rojo, si no a rojo oscuro
            view.setFill(Color.DARKRED);
        });
        flash.play();

        if (hp <= 0) {
            die();
        } else if (hp < maxHp * 0.5 && phase == 1) {
            // Fase 2: Se vuelve más rápido y borde amarillo al 50% de vida
            phase = 2;
            speed *= 1.5;
            view.setStroke(Color.YELLOW);
        }
    }

    private void die() {
        parent.getChildren().remove(view);
        onDeath.accept(this);
    }

    public double getHp() { return hp; }
    public double getMaxHp() { return maxHp; }

    @Override public Node getView() { return view; }
    @Override public Bounds getBounds() { return view.getBoundsInParent(); }
}
