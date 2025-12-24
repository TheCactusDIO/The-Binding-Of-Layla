package com.layla.entities;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.core.GameEntity;

import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

/**
 * Jefe Final del juego.
 * <p>
 * Implementa una máquina de estados con patrones más agresivos:
 * <ul>
 * <li><strong>IDLE:</strong> Pausa breve para reevaluar.</li>
 * <li><strong>CHASE:</strong> Persigue al jugador directamente.</li>
 * <li><strong>ATTACK_SPIRAL:</strong> Dispara un patrón continuo en espiral (más complejo en fase 2).</li>
 * <li><strong>ATTACK_SLAM:</strong> Carga y embiste rápidamente hacia la última posición conocida del jugador.</li>
 * </ul>
 * </p>
 * <p>
 * Hereda de {@link Boss} la gestión de vida, muerte y cambio de fase (al 50% de HP).
 * </p>
 */
public class FinalBoss extends Boss {

    /** Estados posibles de la IA del jefe final. */
    private enum State {
        IDLE,
        CHASE,
        ATTACK_SPIRAL,
        ATTACK_SLAM
    }

    // -------------------------
    // Configuración y Tuning
    // -------------------------

    private static final ThreadLocalRandom RNG = ThreadLocalRandom.current();

    private static final double BASE_SPEED = 60.0;
    private static final double PHASE2_SPEED_MULT = 1.3;

    private static final double SLAM_SPEED = 900.0;
    private static final double SLAM_REACH_DISTANCE = 10.0;

    private static final double SLAM_CHARGE_TIME = 0.5;
    private static final double SLAM_MOVE_TIMEOUT = 0.3;

    private static final double SPIRAL_ANGULAR_SPEED = 5.0;
    private static final double PHASE2_SPIRAL_ANGULAR_MULT = 2.0;

    private static final double SPIRAL_SHOT_INTERVAL = 0.10;

    private static final double BULLET_SPEED = 250.0;
    private static final double BULLET_LIFETIME = 4.0;
    private static final double BULLET_DAMAGE = 1.0;

    // -------------------------
    // Estado interno
    // -------------------------

    private State state = State.IDLE;
    private double stateTimer = 0.0;

    private double spiralAngle = 0.0;
    private double spiralShotTimer = 0.0;

    // Variables para el ataque Slam
    private double slamTargetX;
    private double slamTargetY;
    private boolean slamCharging = false;

    /**
     * Constructor del Jefe Final.
     *
     * @param x                 Posición X inicial.
     * @param y                 Posición Y inicial.
     * @param maxHp             Vida máxima.
     * @param parent            Panel contenedor.
     * @param playerPos         Supplier posición jugador.
     * @param onDeath           Callback muerte.
     * @param onSpawnProjectile Callback spawn proyectil.
     * @param onRemoveProjectile Callback remove proyectil.
     * @param bossId            ID del boss.
     */
    public FinalBoss(
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

        // Aspecto visual más grande e intimidante
        this.view.setRadius(55.0);
        this.view.setFill(Color.BLACK);
        this.view.setStroke(Color.DARKRED);
        this.view.setStrokeWidth(6.0);

        this.speed = BASE_SPEED;
    }

    /**
     * Actualiza el estado del jefe final.
     *
     * @param dt Delta time.
     */
    @Override
    public void update(double dt) {
        if (dead || hp <= 0 || dt <= 0) {
            return;
        }

        stateTimer -= dt;

        switch (state) {
            case IDLE -> {
                if (stateTimer <= 0.0) pickNextState();
            }
            case CHASE -> {
                moveTowardsPlayer(dt);
                if (stateTimer <= 0.0) pickNextState();
            }
            case ATTACK_SPIRAL -> {
                fireSpiral(dt);
                if (stateTimer <= 0.0) pickNextState();
            }
            case ATTACK_SLAM -> handleSlam(dt);
        }
    }

    /**
     * Decide el siguiente estado basándose en probabilidades.
     * Las probabilidades cambian según la fase del jefe (más agresivo en fase 2).
     */
    private void pickNextState() {
        double rnd = RNG.nextDouble();

        if (phase == 2) {
            // Fase 2: Menos persecución, más ataques especiales
            if (rnd < 0.40) enterState(State.CHASE, 2.0);
            else if (rnd < 0.70) enterState(State.ATTACK_SPIRAL, 3.0);
            else enterState(State.ATTACK_SLAM, 0.0); // Duración controlada por la lógica del slam
        } else {
            // Fase 1: Más equilibrado
            if (rnd < 0.50) enterState(State.CHASE, 3.0);
            else if (rnd < 0.80) enterState(State.ATTACK_SPIRAL, 2.5);
            else enterState(State.IDLE, 1.0);
        }
    }

    /**
     * Transiciona a un nuevo estado y configura sus temporizadores iniciales.
     *
     * @param newState El nuevo estado a adoptar.
     * @param duration Duración del estado en segundos (ignorado para SLAM).
     */
    private void enterState(State newState, double duration) {
        this.state = newState;

        if (newState == State.ATTACK_SLAM) {
            prepareSlam();
            return;
        }

        this.stateTimer = duration;

        if (newState == State.ATTACK_SPIRAL) {
            view.setStroke(Color.MAGENTA); // Feedback visual de ataque
            spiralShotTimer = 0.0;
        } else {
            updateColor(); // Restaura color normal
        }
    }

    /**
     * Mueve al jefe hacia la posición actual del jugador.
     *
     * @param dt Delta time.
     */
    private void moveTowardsPlayer(double dt) {
        double[] pPos = playerPos.get();
        double dx = pPos[0] - view.getLayoutX();
        double dy = pPos[1] - view.getLayoutY();
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist <= 1.0) {
            return;
        }

        double currentSpeed = (phase == 2) ? speed * PHASE2_SPEED_MULT : speed;

        view.setLayoutX(view.getLayoutX() + (dx / dist) * currentSpeed * dt);
        view.setLayoutY(view.getLayoutY() + (dy / dist) * currentSpeed * dt);
    }

    /**
     * Ejecuta el ataque de espiral.
     * Dispara proyectiles rotando el ángulo de emisión constantemente.
     * En Fase 2 dispara 4 balas simultáneas en cruz, girando.
     *
     * @param dt Delta time.
     */
    private void fireSpiral(double dt) {
        double mult = (phase == 2) ? PHASE2_SPIRAL_ANGULAR_MULT : 1.0;
        spiralAngle += SPIRAL_ANGULAR_SPEED * dt * mult;

        spiralShotTimer += dt;
        // Bucle while para mantener cadencia exacta incluso si el frame es largo
        while (spiralShotTimer >= SPIRAL_SHOT_INTERVAL) {
            spiralShotTimer -= SPIRAL_SHOT_INTERVAL;

            double dirX = Math.cos(spiralAngle);
            double dirY = Math.sin(spiralAngle);

            spawnBullet(dirX, dirY);
            spawnBullet(-dirX, -dirY);

            if (phase == 2) {
                // Fuego cruzado adicional
                spawnBullet(dirY, -dirX);
                spawnBullet(-dirY, dirX);
            }
        }
    }

    /**
     * Crea y dispara un proyectil del jefe.
     */
    private void spawnBullet(double dx, double dy) {
        Projectile p = new Projectile(
                dx, dy,
                BULLET_SPEED, BULLET_LIFETIME, BULLET_DAMAGE,
                true, // hostil
                parent,
                onRemoveProjectile,
                this,
                bossId
        );

        p.getView().setLayoutX(view.getLayoutX());
        p.getView().setLayoutY(view.getLayoutY());
        onSpawnProjectile.accept(p);
    }

    /**
     * Inicia la secuencia de ataque SLAM.
     * Fija el objetivo (posición del jugador) y comienza la carga (telegraph).
     */
    private void prepareSlam() {
        double[] pPos = playerPos.get();
        slamTargetX = pPos[0];
        slamTargetY = pPos[1];

        slamCharging = true;
        view.setFill(Color.WHITE); // Aviso visual (flash)

        stateTimer = SLAM_CHARGE_TIME;
    }

    /**
     * Maneja la ejecución del SLAM.
     * 1. Espera el tiempo de carga.
     * 2. Se mueve muy rápido hacia el objetivo fijado.
     * 3. Al llegar, realiza un ataque de área y vuelve a elegir estado.
     *
     * @param dt Delta time.
     */
    private void handleSlam(double dt) {
        if (slamCharging) {
            // Esperando que termine la carga (telegraph)
            if (stateTimer <= 0.0) {
                slamCharging = false;
                stateTimer = SLAM_MOVE_TIMEOUT; // Tiempo máximo para llegar
                updateColor();
            }
            return;
        }

        // Movimiento rápido hacia el punto objetivo
        double dx = slamTargetX - view.getLayoutX();
        double dy = slamTargetY - view.getLayoutY();
        double dist = Math.sqrt(dx * dx + dy * dy);

        // Si llegó o se acabó el tiempo
        if (dist < SLAM_REACH_DISTANCE || stateTimer <= 0.0) {
            view.setLayoutX(slamTargetX);
            view.setLayoutY(slamTargetY);

            // Impacto: ejecuta el ataque estándar del Boss base (explosión de proyectiles)
            super.performAttack();
            pickNextState();
            return;
        }

        view.setLayoutX(view.getLayoutX() + (dx / dist) * SLAM_SPEED * dt);
        view.setLayoutY(view.getLayoutY() + (dy / dist) * SLAM_SPEED * dt);
    }

    /**
     * Actualiza el color del jefe según su fase.
     */
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
}
