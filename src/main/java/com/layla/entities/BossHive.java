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

/**
 * Jefe de tipo "Hive" (Colmena).
 * <p>
 * Características:
 * <ul>
 * <li>Se mantiene a una distancia media del jugador (ni muy cerca ni muy lejos).</li>
 * <li>Dispara ráfagas de proyectiles en espiral.</li>
 * <li>Invoca y mantiene "Drones" que orbitan a su alrededor y disparan independientemente.</li>
 * </ul>
 * </p>
 */
public class BossHive extends Boss {

    // Configuración de los drones
    private static final int DRONES_COUNT = 4;
    private static final double DRONES_ORBIT_RADIUS = 95.0;

    /** Lista de drones activos para limpieza al morir. */
    private final List<Drone> drones = new ArrayList<>();

    /** Bandera para asegurar que los drones se spawnean solo una vez. */
    private boolean dronesSpawned = false;

    /** Ángulo acumulado para generar el efecto de espiral en los disparos. */
    private double spiralAngle = 0.0;

    /**
     * Constructor del BossHive.
     * Configura el aspecto visual (púrpura) y una velocidad menor.
     *
     * @param x                 Posición X.
     * @param y                 Posición Y.
     * @param maxHp             Vida máxima.
     * @param parent            Panel padre.
     * @param playerPos         Supplier posición jugador.
     * @param onDeath           Callback muerte.
     * @param onSpawnProjectile Callback spawn.
     * @param onRemoveProjectile Callback remove.
     * @param bossId            ID del boss.
     */
    public BossHive(
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

        this.view.setFill(Color.PURPLE);
        this.view.setStroke(Color.BLACK);
        this.view.setStrokeWidth(4.0);
        this.view.setStrokeType(StrokeType.INSIDE);
        this.view.setEffect(new DropShadow(24, Color.MEDIUMPURPLE));

        this.speed = 28.0; // Más lento que el promedio
    }

    /**
     * Update principal.
     * Gestiona el spawn único de drones, el movimiento y los ataques.
     *
     * @param dt Delta time.
     */
    @Override
    public void update(double dt) {
        if (dead) return;

        // Robustez: verificar muerte por hp <= 0
        if (hp <= 0.0) {
            die();
            return;
        }

        // Spawn inicial de los drones
        if (!dronesSpawned) {
            dronesSpawned = true;
            spawnDrones();
        }

        updateMovement(dt);
        updateAttacks(dt);
    }

    /**
     * Lógica de movimiento inteligente.
     * Intenta mantener una distancia "targetDist" del jugador.
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

        double targetDist = (phase == 1) ? 280.0 : 320.0;

        // move = 1 (acercarse), -1 (alejarse), 0 (quieto)
        double move;
        if (dist > targetDist + 40.0) move = 1.0;
        else if (dist < targetDist - 40.0) move = -1.0;
        else move = 0.0;

        if (move != 0.0) {
            view.setLayoutX(bx + (dx / dist) * speed * dt * move);
            view.setLayoutY(by + (dy / dist) * speed * dt * move);
        }
    }

    /**
     * Gestiona el temporizador de ataque y dispara la espiral.
     *
     * @param dt Delta time.
     */
    private void updateAttacks(double dt) {
        attackTimer += dt;
        if (attackTimer > 1.9) {
            performAttack();
            attackTimer = 0.0;
        }
    }

    /**
     * Ejecuta el ataque de espiral.
     * Genera múltiples proyectiles en anillo que rotan ligeramente en cada disparo.
     */
    @Override
    protected void performAttack() {
        if (dead) return;

        int count = (phase == 1) ? 10 : 14;
        double projSpeed = (phase == 1) ? 190.0 : 220.0;

        // Incrementa el ángulo para que el siguiente disparo esté rotado (efecto espiral)
        spiralAngle += (phase == 1) ? 0.35 : 0.55;

        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        for (int i = 0; i < count; i++) {
            double a = spiralAngle + (2.0 * Math.PI / count) * i;
            spawnProjectile(Math.cos(a), Math.sin(a), projSpeed, 3.0, 1.0, bx, by);
        }
    }

    /**
     * Crea e invoca los drones que orbitarán al jefe.
     */
    private void spawnDrones() {
        for (int i = 0; i < DRONES_COUNT; i++) {
            double a = (2.0 * Math.PI / DRONES_COUNT) * i;

            Drone d = new Drone(
                    this,
                    a,
                    DRONES_ORBIT_RADIUS,
                    parent,
                    onSpawnProjectile,
                    onRemoveProjectile,
                    bossId,
                    destroyed -> drones.remove(destroyed) // Callback para auto-eliminarse de la lista
            );

            drones.add(d);
            onSpawnProjectile.accept(d);
        }
    }

    /**
     * Helper para disparar proyectiles del BossHive.
     */
    private void spawnProjectile(double dirX, double dirY, double speed, double radius, double damage, double x, double y) {
        Projectile p = new Projectile(
                dirX, dirY,
                speed,
                radius, damage,
                true,
                parent,
                onRemoveProjectile,
                this,
                bossId
        );
        p.getView().setLayoutX(x);
        p.getView().setLayoutY(y);
        onSpawnProjectile.accept(p);
    }

    /**
     * Al morir, destruye todos los drones asociados.
     */
    @Override
    protected void die() {
        for (Drone d : new ArrayList<>(drones)) {
            d.forceDestroy();
        }
        drones.clear();
        super.die();
    }

    /**
     * Restablece el color a púrpura tras daño.
     */
    @Override
    protected void updateColor() {
        view.setFill(Color.PURPLE);
    }

    // =========================================================
    // Clase interna: Drone
    // =========================================================

    /**
     * Entidad Drone que orbita alrededor de su jefe dueño y dispara hacia el exterior.
     */
    private static final class Drone implements GameEntity {

        private final BossHive boss;
        private final Pane parent;
        private final Circle view;

        private final Consumer<GameEntity> onSpawn;
        private final Consumer<GameEntity> onRemove;

        private final String bossId;
        private final Consumer<Drone> onDestroyed;

        private double angle;
        private final double radius;

        private double fireTimer = 0.0;
        private boolean destroyed = false;

        /**
         * Crea un Drone.
         *
         * @param boss       Instancia del jefe al que orbita.
         * @param startAngle Ángulo inicial en la órbita.
         * @param radius     Radio de la órbita.
         * @param parent     Panel padre.
         * @param onSpawn    Callback spawn.
         * @param onRemove   Callback remove.
         * @param bossId     ID del boss.
         * @param onDestroyed Callback limpieza.
         */
        Drone(
                BossHive boss,
                double startAngle,
                double radius,
                Pane parent,
                Consumer<GameEntity> onSpawn,
                Consumer<GameEntity> onRemove,
                String bossId,
                Consumer<Drone> onDestroyed
        ) {
            this.boss = Objects.requireNonNull(boss, "boss");
            this.parent = Objects.requireNonNull(parent, "parent");
            this.onSpawn = Objects.requireNonNull(onSpawn, "onSpawn");
            this.onRemove = Objects.requireNonNull(onRemove, "onRemove");
            this.bossId = Objects.requireNonNull(bossId, "bossId");
            this.onDestroyed = (onDestroyed != null) ? onDestroyed : d -> {};

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
            if (destroyed) return;

            // Auto-destrucción si el jefe muere
            if (boss.dead || boss.hp <= 0.0) {
                forceDestroy();
                return;
            }

            // Actualizar posición orbital
            double rotSpeed = (boss.phase == 1) ? 1.4 : 2.1;
            angle += rotSpeed * dt;
            syncPos();

            // Disparar
            fireTimer += dt;
            double interval = (boss.phase == 1) ? 1.25 : 0.85;
            if (fireTimer >= interval) {
                fireTimer = 0.0;
                shoot();
            }
        }

        /**
         * Sincroniza la posición del drone basándose en la posición del jefe y el ángulo orbital.
         */
        private void syncPos() {
            double bx = boss.view.getLayoutX();
            double by = boss.view.getLayoutY();
            view.setLayoutX(bx + Math.cos(angle) * radius);
            view.setLayoutY(by + Math.sin(angle) * radius);
        }

        /**
         * Dispara un proyectil en la dirección vectorial hacia afuera de la órbita.
         */
        private void shoot() {
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

        /**
         * Destruye el drone inmediatamente.
         */
        void forceDestroy() {
            if (destroyed) return;
            destroyed = true;

            parent.getChildren().remove(view);
            onRemove.accept(this);
            onDestroyed.accept(this);
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
