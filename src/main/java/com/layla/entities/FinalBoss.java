package com.layla.entities;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.core.GameEntity;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

public class FinalBoss extends Boss {

    private enum State {
        IDLE,           // Esperando / Recuperando
        CHASE,          // Persiguiendo al jugador
        ATTACK_SPIRAL,  // Quieto, disparando en espiral
        ATTACK_SLAM     // Embestida rápida (Fase 2)
    }

    private State state = State.IDLE;
    private double stateTimer = 0.0;
    private double spiralAngle = 0.0;

    // Variables para SLAM
    private double slamTargetX, slamTargetY;
    private boolean slamCharging = false;

    public FinalBoss(double x, double y, double maxHp, Pane parent,
                     Supplier<double[]> playerPos,
                     Consumer<Boss> onDeath,
                     Consumer<GameEntity> onSpawnProjectile,
                     String bossId) {
        super(x, y, maxHp, parent, playerPos, onDeath, onSpawnProjectile, bossId);

        // El jefe final es más grande y oscuro
        this.view.setRadius(55.0);
        this.view.setFill(Color.BLACK);
        this.view.setStroke(Color.DARKRED);
        this.view.setStrokeWidth(6.0);
        this.speed = 60.0;
    }

    @Override
    public void update(double dt) {
        if (dead || hp <= 0) return;

        stateTimer -= dt;

        switch (state) {
            case IDLE:
                if (stateTimer <= 0) {
                    // Elegir siguiente estado aleatorio
                    pickNextState();
                }
                break;

            case CHASE:
                moveTowardsPlayer(dt);
                if (stateTimer <= 0) pickNextState();
                break;

            case ATTACK_SPIRAL:
                fireSpiral(dt);
                if (stateTimer <= 0) pickNextState();
                break;

            case ATTACK_SLAM:
                handleSlam(dt);
                break;
        }
    }

    private void pickNextState() {
        double rnd = Math.random();

        // Fase 2 es más agresiva
        if (phase == 2) {
            if (rnd < 0.4) enterState(State.CHASE, 2.0);
            else if (rnd < 0.7) enterState(State.ATTACK_SPIRAL, 3.0);
            else enterState(State.ATTACK_SLAM, 1.5);
        } else {
            // Fase 1: Más tranquilo
            if (rnd < 0.5) enterState(State.CHASE, 3.0);
            else if (rnd < 0.8) enterState(State.ATTACK_SPIRAL, 2.5);
            else enterState(State.IDLE, 1.0);
        }
    }

    private void enterState(State newState, double duration) {
        this.state = newState;
        this.stateTimer = duration;

        if (newState == State.ATTACK_SLAM) {
            prepareSlam();
        } else if (newState == State.ATTACK_SPIRAL) {
            // Efecto visual: Brillo
            view.setStroke(Color.MAGENTA);
        } else {
            updateColor(); // Reset color
        }
    }

    private void moveTowardsPlayer(double dt) {
        double[] pPos = playerPos.get();
        double dx = pPos[0] - view.getLayoutX();
        double dy = pPos[1] - view.getLayoutY();
        double dist = Math.sqrt(dx*dx + dy*dy);

        double currentSpeed = (phase == 2) ? speed * 1.3 : speed;

        if (dist > 1.0) {
            view.setLayoutX(view.getLayoutX() + (dx / dist) * currentSpeed * dt);
            view.setLayoutY(view.getLayoutY() + (dy / dist) * currentSpeed * dt);
        }
    }

    private void fireSpiral(double dt) {
        // Disparar muy rápido (cada frame o casi)
        spiralAngle += 5.0 * dt * (phase == 2 ? 2.0 : 1.0); // Gira más rápido en fase 2

        // Limitar cadencia de fuego real (cada 0.1s aprox)
        if (Math.abs(stateTimer % 0.1) < dt) {
            double dirX = Math.cos(spiralAngle);
            double dirY = Math.sin(spiralAngle);

            // Disparar 2 balas opuestas
            spawnBullet(dirX, dirY);
            spawnBullet(-dirX, -dirY);

            if (phase == 2) {
                // En fase 2, dispara 4 balas en cruz
                spawnBullet(dirY, -dirX);
                spawnBullet(-dirY, dirX);
            }
        }
    }

    private void spawnBullet(double dx, double dy) {
        Projectile p = new Projectile(
            dx, dy,
            250.0, 4.0, 1.0,
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

    private void prepareSlam() {
        double[] pPos = playerPos.get();
        slamTargetX = pPos[0];
        slamTargetY = pPos[1];
        slamCharging = true;
        view.setFill(Color.WHITE); // Flash de carga
        stateTimer = 0.5; // Tiempo de carga estática
    }

    private void handleSlam(double dt) {
        if (slamCharging) {
            // Quieto, cargando...
            if (stateTimer <= 0) {
                slamCharging = false;
                stateTimer = 0.3; // Tiempo de vuelo
                updateColor();
            }
        } else {
            // Vuelo hacia el objetivo
            double dx = slamTargetX - view.getLayoutX();
            double dy = slamTargetY - view.getLayoutY();
            double dist = Math.sqrt(dx*dx + dy*dy);

            // Velocidad muy alta
            double slamSpeed = 900.0;

            if (dist < 10.0 || stateTimer <= 0) {
                // Impacto!
                view.setLayoutX(slamTargetX);
                view.setLayoutY(slamTargetY);
                // Onda expansiva de balas al impactar
                performAttack();
                pickNextState();
            } else {
                view.setLayoutX(view.getLayoutX() + (dx/dist) * slamSpeed * dt);
                view.setLayoutY(view.getLayoutY() + (dy/dist) * slamSpeed * dt);
            }
        }
    }

    @Override
    protected void updateColor() {
        if (phase == 2) {
            view.setFill(Color.ORANGERED);
            view.setStroke(Color.YELLOW);
        } else {
            view.setFill(Color.BLACK);
            view.setStroke(Color.DARKRED);
        }
    }

    @Override
    protected void performAttack() {
        // Reutilizamos el ataque circular base para el impacto del Slam
        super.performAttack();
    }
}
