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
 * Jefe de tipo "Sniper" (Francotirador).
 * <p>
 * Comportamiento:
 * <ul>
 * <li>Mantiene la distancia con el jugador: huye si está muy cerca y se acerca si está muy lejos.</li>
 * <li>En rango medio, orbita lateralmente (strafe) alrededor del jugador.</li>
 * <li>Ataque principal: Ráfaga de proyectiles rápidos apuntados directamente.</li>
 * <li>Ataque especial (cada 3 ataques): Lanza un orbe lento que explota en un anillo de balas.</li>
 * </ul>
 * </p>
 */
public class BossSniper extends Boss {

    // Distancias deseadas respecto al jugador para la IA de movimiento
    private static final double DESIRED_MIN_DIST = 220.0;
    private static final double DESIRED_MAX_DIST = 360.0;

    // Intervalo de tiempo entre ataques
    private static final double ATTACK_INTERVAL_SEC = 1.7;

    /** Contador de ataques realizados para alternar patrones (ej. cada 3 ataques lanza el orbe). */
    private int attackCount = 0;

    /**
     * Crea un nuevo BossSniper.
     * Configura el aspecto visual (azul) y la velocidad.
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

        // Look distinto: Azul oscuro con borde celeste
        this.view.setFill(Color.DARKSLATEBLUE);
        this.view.setStroke(Color.LIGHTBLUE);
        this.view.setStrokeWidth(4.0);
        this.view.setStrokeType(StrokeType.INSIDE);
        this.view.setEffect(new DropShadow(22, Color.DEEPSKYBLUE));

        this.speed = 35.0;
    }

    /**
     * Actualiza la lógica del Sniper.
     * Gestiona la muerte segura, el movimiento estratégico y el temporizador de ataque.
     *
     * @param dt Delta time en segundos.
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
     * Lógica de movimiento inteligente basada en zonas.
     * <ul>
     * <li><strong>Zona cercana:</strong> Huye del jugador.</li>
     * <li><strong>Zona lejana:</strong> Se acerca al jugador.</li>
     * <li><strong>Zona media:</strong> Se mueve lateralmente (strafe/orbita) para ser un blanco difícil.</li>
     * </ul>
     *
     * @param dt Delta time.
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
            // Huye (vector opuesto al jugador)
            moveX = -ndx;
            moveY = -ndy;
        } else if (dist > DESIRED_MAX_DIST) {
            // Se acerca (vector hacia el jugador)
            moveX = ndx;
            moveY = ndy;
        } else {
            // Strafe/orbita: perpendicular al vector al player (-y, x)
            double px = -ndy;
            double py = ndx;

            // Alterna dirección según el número de ataques para variar el patrón
            double dir = (attackCount % 2 == 0) ? 1.0 : -1.0;

            moveX = px * dir;
            moveY = py * dir;
        }

        view.setLayoutX(bx + moveX * speed * dt);
        view.setLayoutY(by + moveY * speed * dt);
    }

    /**
     * Gestiona el temporizador para disparar.
     *
     * @param dt Delta time.
     */
    private void updateAttackTimer(double dt) {
        attackTimer += dt;
        if (attackTimer > ATTACK_INTERVAL_SEC) {
            performAttack();
            attackTimer = 0.0;
        }
    }

    /**
     * Ejecuta el ataque.
     * Alterna entre una ráfaga apuntada (ataque común) y un orbe explosivo (cada 3 ataques).
     */
    @Override
    protected void performAttack() {
        if (dead) return;

        attackCount++;

        // 1 de cada 3 ataques lanza el orbe especial
        if (attackCount % 3 == 0) {
            spawnExplodingOrb();
            return;
        }

        spawnAimedBurst();
    }

    /**
     * Dispara una ráfaga de proyectiles directos hacia el jugador con una pequeña dispersión.
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

        int mid = burst / 2;

        for (int i = 0; i < burst; i++) {
            int off = i - mid;

            double a = Math.toRadians(off * spreadDeg);

            // Rotar el vector de apuntado (aimX, aimY) por ángulo 'a'
            double rx = aimX * Math.cos(a) - aimY * Math.sin(a);
            double ry = aimX * Math.sin(a) + aimY * Math.cos(a);

            spawnProjectile(rx, ry, 330.0, 3.0, 1.0, bx, by, this);
        }
    }

    /**
     * Crea y lanza un {@link ExplodingOrb} hacia la posición actual del jugador.
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
                110.0,                      // Velocidad lenta del orbe
                (phase == 1) ? 0.9 : 0.7,   // Retardo hasta explotar
                (phase == 1) ? 10 : 14,     // Cantidad de balas en explosión
                (phase == 1) ? 190.0 : 220.0, // Velocidad de balas resultantes
                parent,
                onSpawnProjectile,
                onRemoveProjectile,
                this,
                bossId
        );

        onSpawnProjectile.accept(orb);
    }

    /**
     * Helper para spawnear proyectiles estándar del Sniper.
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
     * Restablece el color azul oscuro tras recibir daño.
     */
    @Override
    protected void updateColor() {
        view.setFill(Color.DARKSLATEBLUE);
    }

    // =========================================================
    // Clase interna: Orbe Explosivo
    // =========================================================

    /**
     * Proyectil especial (Orbe) que no hace daño directo por impacto (opcionalmente),
     * sino que viaja una distancia/tiempo y explota liberando un anillo de balas.
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

        /**
         * Crea un orbe explosivo.
         *
         * @param x, y          Posición inicial.
         * @param dirX, dirY    Dirección de movimiento.
         * @param speed         Velocidad de movimiento.
         * @param delay         Tiempo en segundos hasta la explosión.
         * @param ringCount     Número de proyectiles generados al explotar.
         * @param ringSpeed     Velocidad de los proyectiles generados.
         * @param parent        Panel padre.
         * @param onSpawn       Callback spawn.
         * @param onRemove      Callback remove.
         * @param owner         Boss dueño.
         * @param bossId        ID del boss.
         */
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

            // Movimiento lineal
            view.setLayoutX(view.getLayoutX() + dirX * speed * dt);
            view.setLayoutY(view.getLayoutY() + dirY * speed * dt);

            // Cuenta atrás para detonación
            timeLeft -= dt;
            if (timeLeft <= 0.0) {
                explode();
                destroy();
            }
        }

        /**
         * Genera la explosión radial (anillo de balas).
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
