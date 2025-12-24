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
 * Jefe de tipo "Charger" (Embestidor).
 * <p>
 * Este jefe sigue un patrón de máquina de estados:
 * <ol>
 * <li><strong>CHASE:</strong> Persigue al jugador lentamente y dispara abanicos de balas.</li>
 * <li><strong>WINDUP:</strong> Se detiene y se prepara para embestir (telegrafía).</li>
 * <li><strong>DASH:</strong> Embiste a alta velocidad en línea recta, soltando minas.</li>
 * <li><strong>COOLDOWN:</strong> Descansa brevemente antes de volver a perseguir.</li>
 * </ol>
 * </p>
 */
public class BossCharger extends Boss {

    /** Enumeración de los estados posibles del jefe. */
    private enum State {
        CHASE,
        WINDUP,
        DASH,
        COOLDOWN
    }

    /** Estado actual del jefe. */
    private State state = State.CHASE;

    // Timers para controlar la duración de cada estado
    private double windupTimer = 0.0;
    private double dashTimer = 0.0;
    private double cooldownTimer = 0.0;

    /** Dirección X fijada para el dash. */
    private double dashDirX = 0.0;
    /** Dirección Y fijada para el dash. */
    private double dashDirY = 0.0;

    /** Velocidad de desplazamiento durante el dash. */
    private double dashSpeed = 420.0;

    /** Temporizador para controlar el soltado de minas durante el dash. */
    private double mineDropTimer = 0.0;

    /**
     * Lista de minas activas.
     * Se usa para forzar su eliminación si el jefe muere antes de que exploten.
     */
    private final List<Mine> mines = new ArrayList<>();

    /**
     * Constructor del BossCharger.
     * Configura el aspecto visual (verde) y la velocidad base.
     *
     * @param x                 Posición X inicial.
     * @param y                 Posición Y inicial.
     * @param maxHp             Vida máxima.
     * @param parent            Panel contenedor.
     * @param playerPos         Supplier posición jugador.
     * @param onDeath           Callback muerte.
     * @param onSpawnProjectile Callback spawn proyectil.
     * @param onRemoveProjectile Callback remove proyectil.
     * @param bossId            ID del jefe.
     */
    public BossCharger(
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

        // Visual propio: Verde
        this.view.setFill(Color.DARKGREEN);
        this.view.setStroke(Color.BLACK);
        this.view.setStrokeWidth(4.0);
        this.view.setStrokeType(StrokeType.INSIDE);
        this.view.setEffect(new DropShadow(22, Color.LIMEGREEN));

        // Velocidad base de persecución (más lento que el Boss normal)
        this.speed = 40.0;
    }

    /**
     * Actualización principal. Ejecuta la lógica correspondiente al estado actual.
     *
     * @param dt Delta time en segundos.
     */
    @Override
    public void update(double dt) {
        if (dead) return;

        // Robustez: asegurar muerte si hp <= 0
        if (hp <= 0.0) {
            die();
            return;
        }

        // Máquina de estados
        switch (state) {
            case CHASE -> updateChase(dt);
            case WINDUP -> updateWindup(dt);
            case DASH -> updateDash(dt);
            case COOLDOWN -> updateCooldown(dt);
        }

        // Ataque secundario (disparo) solo durante la fase de persecución
        attackTimer += dt;
        if (state == State.CHASE && attackTimer > 2.4) {
            performAttack();
            attackTimer = 0.0;
        }
    }

    /**
     * Lógica del estado CHASE.
     * Persigue al jugador y decide si transicionar a WINDUP para iniciar una embestida.
     *
     * @param dt Delta time.
     */
    private void updateChase(double dt) {
        double[] pPos = playerPos.get();

        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        double dx = pPos[0] - bx;
        double dy = pPos[1] - by;

        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.001) dist = 0.001;

        // Movimiento hacia el jugador
        view.setLayoutX(bx + (dx / dist) * speed * dt);
        view.setLayoutY(by + (dy / dist) * speed * dt);

        // Si hay cooldown pendiente, no puede iniciar dash
        if (cooldownTimer > 0.0) {
            cooldownTimer -= dt;
            return;
        }

        // Si está lo suficientemente cerca, inicia la preparación (Windup)
        if (dist < 520.0) {
            state = State.WINDUP;
            windupTimer = (phase == 1) ? 0.55 : 0.40;

            // Feedback visual: color más claro
            view.setFill(Color.YELLOWGREEN);
        }
    }

    /**
     * Lógica del estado WINDUP.
     * Espera quieto un momento y calcula la dirección hacia el jugador.
     *
     * @param dt Delta time.
     */
    private void updateWindup(double dt) {
        windupTimer -= dt;
        if (windupTimer > 0.0) return;

        // Al terminar el windup, fija la dirección hacia donde estaba el jugador
        double[] pPos = playerPos.get();

        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        double dx = pPos[0] - bx;
        double dy = pPos[1] - by;

        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.001) dist = 0.001;

        dashDirX = dx / dist;
        dashDirY = dy / dist;

        // Transición a DASH
        state = State.DASH;
        dashTimer = (phase == 1) ? 0.65 : 0.85;
        mineDropTimer = 0.0;

        // Restaura color
        view.setFill(Color.DARKGREEN);
    }

    /**
     * Lógica del estado DASH.
     * Mueve al jefe rápidamente en la dirección fijada y suelta minas periódicamente.
     *
     * @param dt Delta time.
     */
    private void updateDash(double dt) {
        dashTimer -= dt;

        // Movimiento rectilíneo rápido
        double bx = view.getLayoutX();
        double by = view.getLayoutY();
        view.setLayoutX(bx + dashDirX * dashSpeed * dt);
        view.setLayoutY(by + dashDirY * dashSpeed * dt);

        // Soltar minas
        mineDropTimer -= dt;
        if (mineDropTimer <= 0.0) {
            mineDropTimer = (phase == 1) ? 0.18 : 0.12;
            spawnMine(view.getLayoutX(), view.getLayoutY());
        }

        // Al terminar el dash
        if (dashTimer <= 0.0) {
            doShockwave(); // Onda de choque final
            state = State.COOLDOWN;
            cooldownTimer = (phase == 1) ? 1.2 : 0.9;
        }
    }

    /**
     * Lógica del estado COOLDOWN.
     * Espera un tiempo antes de volver a perseguir.
     *
     * @param dt Delta time.
     */
    private void updateCooldown(double dt) {
        cooldownTimer -= dt;
        if (cooldownTimer <= 0.0) {
            state = State.CHASE;
        }
    }

    /**
     * Realiza el ataque secundario (durante CHASE).
     * Dispara un abanico de proyectiles lentos hacia el jugador.
     */
    @Override
    protected void performAttack() {
        double[] p = playerPos.get();

        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        double dx = p[0] - bx;
        double dy = p[1] - by;

        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.001) dist = 0.001;

        double aimX = dx / dist;
        double aimY = dy / dist;

        int shots = (phase == 1) ? 5 : 7;
        double spreadDeg = 35.0;

        // Caso borde: un solo disparo
        if (shots <= 1) {
            spawnProjectile(aimX, aimY, 210.0, 3.2, 1.0, bx, by);
            return;
        }

        int mid = shots / 2;
        for (int i = 0; i < shots; i++) {
            int off = i - mid;

            double stepDeg = spreadDeg / Math.max(1, mid);
            double a = Math.toRadians(off * stepDeg);

            // Rotación vectorial para el spread
            double rx = aimX * Math.cos(a) - aimY * Math.sin(a);
            double ry = aimX * Math.sin(a) + aimY * Math.cos(a);

            spawnProjectile(rx, ry, 210.0, 3.2, 1.0, bx, by);
        }
    }

    /**
     * Genera una onda de choque radial (shockwave) al finalizar el dash.
     */
    private void doShockwave() {
        int count = (phase == 1) ? 10 : 14;
        double spd = (phase == 1) ? 180.0 : 210.0;

        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        for (int i = 0; i < count; i++) {
            double a = (2.0 * Math.PI / count) * i;
            spawnProjectile(Math.cos(a), Math.sin(a), spd, 3.0, 1.0, bx, by);
        }
    }

    /**
     * Método auxiliar para instanciar proyectiles del Charger.
     */
    private void spawnProjectile(double dirX, double dirY, double speed, double radius, double damage, double x, double y) {
        Projectile proj = new Projectile(
                dirX, dirY,
                speed,
                radius, damage,
                true,
                parent,
                onRemoveProjectile,
                this,
                bossId
        );

        proj.getView().setLayoutX(x);
        proj.getView().setLayoutY(y);

        onSpawnProjectile.accept(proj);
    }

    /**
     * Genera una mina en la posición dada y la registra en la lista de minas activas.
     *
     * @param x Posición X.
     * @param y Posición Y.
     */
    private void spawnMine(double x, double y) {
        Mine m = new Mine(
                x, y,
                (phase == 1) ? 1.15 : 0.9, // Tiempo hasta explosión
                (phase == 1) ? 8 : 10,     // Proyectiles al explotar
                (phase == 1) ? 170.0 : 200.0, // Velocidad proyectiles
                parent,
                onSpawnProjectile,
                onRemoveProjectile,
                this,
                bossId,
                destroyedMine -> mines.remove(destroyedMine) // Callback para auto-eliminarse de la lista
        );

        mines.add(m);
        onSpawnProjectile.accept(m);
    }

    /**
     * Al morir, destruye todas las minas activas para limpiar el escenario y delega a la clase padre.
     */
    @Override
    protected void die() {
        // Copia de la lista para evitar ConcurrentModificationException al iterar y borrar
        for (Mine m : new ArrayList<>(mines)) {
            m.forceDestroy();
        }
        mines.clear();
        super.die();
    }

    /**
     * Restablece el color a verde oscuro tras recibir daño.
     */
    @Override
    protected void updateColor() {
        view.setFill(Color.DARKGREEN);
    }

    // =========================================================
    // Clase interna: Mina
    // =========================================================

    /**
     * Entidad "Mina" que explota tras un retardo, generando un anillo de proyectiles.
     */
    private static final class Mine implements GameEntity {

        private final Pane parent;
        private final Circle view;

        private final Consumer<GameEntity> onSpawn;
        private final Consumer<GameEntity> onRemove;

        private final Boss owner;
        private final String bossId;

        /** Callback para notificarse a sí misma fuera de la lista del BossCharger. */
        private final Consumer<Mine> onDestroyed;

        private double timer;
        private final int ringCount;
        private final double ringSpeed;

        private boolean destroyed = false;

        /**
         * Crea una mina.
         *
         * @param x Posición X.
         * @param y Posición Y.
         * @param delay Tiempo en segundos antes de explotar.
         * @param ringCount Número de proyectiles.
         * @param ringSpeed Velocidad de los proyectiles.
         * @param parent Panel padre.
         * @param onSpawn Callback spawn.
         * @param onRemove Callback remove.
         * @param owner Boss dueño.
         * @param bossId ID del boss.
         * @param onDestroyed Callback de limpieza.
         */
        Mine(
                double x,
                double y,
                double delay,
                int ringCount,
                double ringSpeed,
                Pane parent,
                Consumer<GameEntity> onSpawn,
                Consumer<GameEntity> onRemove,
                Boss owner,
                String bossId,
                Consumer<Mine> onDestroyed
        ) {
            this.parent = Objects.requireNonNull(parent, "parent");
            this.onSpawn = Objects.requireNonNull(onSpawn, "onSpawn");
            this.onRemove = Objects.requireNonNull(onRemove, "onRemove");
            this.owner = Objects.requireNonNull(owner, "owner");
            this.bossId = Objects.requireNonNull(bossId, "bossId");
            this.onDestroyed = (onDestroyed != null) ? onDestroyed : m -> {};

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
            if (destroyed) return;

            timer -= dt;
            if (timer <= 0.0) {
                explode();
                destroy();
            }
        }

        /**
         * Genera la explosión radial.
         */
        private void explode() {
            double x = view.getLayoutX();
            double y = view.getLayoutY();

            for (int i = 0; i < ringCount; i++) {
                double a = (2.0 * Math.PI / ringCount) * i;

                Projectile p = new Projectile(
                        Math.cos(a), Math.sin(a),
                        ringSpeed,
                        3.0, 1.0,
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
         * Fuerza la destrucción inmediata (usado al limpiar el nivel o morir el boss).
         */
        void forceDestroy() {
            destroy();
        }

        /**
         * Destruye la entidad visualmente y la remueve del juego.
         * Es idempotente.
         */
        private void destroy() {
            if (destroyed) return;
            destroyed = true;

            parent.getChildren().remove(view);
            onRemove.accept(this);

            // Notifica al BossCharger para que la saque de su lista interna
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
