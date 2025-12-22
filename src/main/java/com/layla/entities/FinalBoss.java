package com.layla.entities;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.core.GameEntity;

import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

/**
 * Jefe final con una máquina de estados simple:
 * <ul>
 *   <li>IDLE: pausa breve.</li>
 *   <li>CHASE: persigue al jugador.</li>
 *   <li>ATTACK_SPIRAL: dispara un patrón en espiral (más agresivo en fase 2).</li>
 *   <li>ATTACK_SLAM: carga y hace un desplazamiento rápido al último punto conocido del jugador.</li>
 * </ul>
 *
 * <p>Notas:</p>
 * <ul>
 *   <li>La {@code phase} se hereda de {@link Boss} (cambia al bajar de 50% de vida).</li>
 *   <li>Los proyectiles se spawnean vía {@code onSpawnProjectile} y se eliminan vía {@code onRemoveProjectile}.</li>
 * </ul>
 */
public class FinalBoss extends Boss {

    private enum State {
        IDLE,
        CHASE,
        ATTACK_SPIRAL,
        ATTACK_SLAM
    }

    // -------------------------
    // Tuning
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
    // State
    // -------------------------

    private State state = State.IDLE;
    private double stateTimer = 0.0;

    private double spiralAngle = 0.0;
    private double spiralShotTimer = 0.0;

    private double slamTargetX;
    private double slamTargetY;
    private boolean slamCharging = false;

    /**
     * Crea el jefe final.
     *
     * @param x posición inicial X
     * @param y posición inicial Y
     * @param maxHp vida máxima
     * @param parent contenedor JavaFX donde se renderiza la entidad
     * @param playerPos proveedor de posición del jugador (x,y)
     * @param onDeath callback al morir
     * @param onSpawnProjectile callback para añadir proyectiles/entidades al GameLoop
     * @param onRemoveProjectile callback para eliminar proyectiles/entidades del GameLoop
     * @param bossId identificador del boss (para lastHitSource, stats, etc.)
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

        this.view.setRadius(55.0);
        this.view.setFill(Color.BLACK);
        this.view.setStroke(Color.DARKRED);
        this.view.setStrokeWidth(6.0);

        this.speed = BASE_SPEED;
    }

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
     * Selecciona el siguiente estado con probabilidades distintas por fase.
     */
    private void pickNextState() {
        double rnd = RNG.nextDouble();

        if (phase == 2) {
            if (rnd < 0.40) enterState(State.CHASE, 2.0);
            else if (rnd < 0.70) enterState(State.ATTACK_SPIRAL, 3.0);
            else enterState(State.ATTACK_SLAM, 0.0); // duración la decide el propio slam
        } else {
            if (rnd < 0.50) enterState(State.CHASE, 3.0);
            else if (rnd < 0.80) enterState(State.ATTACK_SPIRAL, 2.5);
            else enterState(State.IDLE, 1.0);
        }
    }

    /**
     * Entra en un estado y configura timers/telegraph.
     *
     * @param newState nuevo estado
     * @param duration duración del estado (en segundos). En SLAM se ignora (usa tiempos internos).
     */
    private void enterState(State newState, double duration) {
        this.state = newState;

        if (newState == State.ATTACK_SLAM) {
            prepareSlam();
            return;
        }

        this.stateTimer = duration;

        if (newState == State.ATTACK_SPIRAL) {
            view.setStroke(Color.MAGENTA);
            spiralShotTimer = 0.0;
        } else {
            updateColor();
        }
    }

    /**
     * Persigue al jugador a velocidad base (más rápida en fase 2).
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
     * Patrón de disparo en espiral. En fase 2 dispara además en cruz.
     *
     * <p>Robusto: usa un timer acumulado para disparar cada {@value #SPIRAL_SHOT_INTERVAL}s.</p>
     */
    private void fireSpiral(double dt) {
        double mult = (phase == 2) ? PHASE2_SPIRAL_ANGULAR_MULT : 1.0;
        spiralAngle += SPIRAL_ANGULAR_SPEED * dt * mult;

        spiralShotTimer += dt;
        while (spiralShotTimer >= SPIRAL_SHOT_INTERVAL) {
            spiralShotTimer -= SPIRAL_SHOT_INTERVAL;

            double dirX = Math.cos(spiralAngle);
            double dirY = Math.sin(spiralAngle);

            spawnBullet(dirX, dirY);
            spawnBullet(-dirX, -dirY);

            if (phase == 2) {
                spawnBullet(dirY, -dirX);
                spawnBullet(-dirY, dirX);
            }
        }
    }

    /**
     * Spawnea una bala enemiga desde la posición actual del boss.
     */
    private void spawnBullet(double dx, double dy) {
        Projectile p = new Projectile(
                dx, dy,
                BULLET_SPEED, BULLET_LIFETIME, BULLET_DAMAGE,
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

    /**
     * Preparación del slam:
     * <ul>
     *   <li>Guarda la posición del jugador en ese instante.</li>
     *   <li>Telegraph: se pone blanco.</li>
     *   <li>Timer de carga: {@value #SLAM_CHARGE_TIME}s.</li>
     * </ul>
     */
    private void prepareSlam() {
        double[] pPos = playerPos.get();
        slamTargetX = pPos[0];
        slamTargetY = pPos[1];

        slamCharging = true;
        view.setFill(Color.WHITE);

        stateTimer = SLAM_CHARGE_TIME;
    }

    /**
     * Ejecuta el slam: carga y luego se desplaza rápidamente al punto objetivo.
     * Al llegar o expirar el timeout, aterriza y lanza el ataque base del boss.
     */
    private void handleSlam(double dt) {
        if (slamCharging) {
            if (stateTimer <= 0.0) {
                slamCharging = false;
                stateTimer = SLAM_MOVE_TIMEOUT;
                updateColor();
            }
            return;
        }

        double dx = slamTargetX - view.getLayoutX();
        double dy = slamTargetY - view.getLayoutY();
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < SLAM_REACH_DISTANCE || stateTimer <= 0.0) {
            view.setLayoutX(slamTargetX);
            view.setLayoutY(slamTargetY);

            // Explosión/ataque tras el aterrizaje (usa comportamiento del Boss base)
            super.performAttack();
            pickNextState();
            return;
        }

        view.setLayoutX(view.getLayoutX() + (dx / dist) * SLAM_SPEED * dt);
        view.setLayoutY(view.getLayoutY() + (dy / dist) * SLAM_SPEED * dt);
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
}
