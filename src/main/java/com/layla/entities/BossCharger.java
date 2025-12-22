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
 * Boss tipo "Charger":
 * - Persigue (CHASE)
 * - Hace windup (WINDUP)
 * - Dash hacia el jugador (DASH) dejando minas
 * - Cooldown (COOLDOWN)
 *
 * Extra:
 * - En CHASE, a intervalos lanza un abanico de balas lento.
 * - Al terminar el dash, hace shockwave.
 */
public class BossCharger extends Boss {

    /**
     * Estados del boss.
     */
    private enum State {
        CHASE,
        WINDUP,
        DASH,
        COOLDOWN
    }

    private State state = State.CHASE;

    // Timers del estado
    private double windupTimer = 0.0;
    private double dashTimer = 0.0;
    private double cooldownTimer = 0.0;

    // Dirección del dash fijada al terminar el windup
    private double dashDirX = 0.0;
    private double dashDirY = 0.0;

    // Ajustable: velocidad del dash
    private double dashSpeed = 420.0;

    // Drop mines durante dash
    private double mineDropTimer = 0.0;

    /**
     * Lista de minas activas (solo para poder limpiarlas en die()).
     * IMPORTANTE: antes esto se filtraba porque nunca se removían al explotar.
     */
    private final List<Mine> mines = new ArrayList<>();

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

        // Visual propio
        this.view.setFill(Color.DARKGREEN);
        this.view.setStroke(Color.BLACK);
        this.view.setStrokeWidth(4.0);
        this.view.setStrokeType(StrokeType.INSIDE);
        this.view.setEffect(new DropShadow(22, Color.LIMEGREEN));

        // Velocidad base de chase
        this.speed = 40.0;
    }

    /**
     * Update principal del boss.
     * Se asegura de llamar a die() si hp cae a 0 por cualquier motivo.
     */
    @Override
    public void update(double dt) {
        if (dead) return;

        // Robustez: si hp <= 0, no te quedes en "zombie state"
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

        // Ataque secundario (solo en CHASE)
        attackTimer += dt;
        if (state == State.CHASE && attackTimer > 2.4) {
            performAttack();
            attackTimer = 0.0;
        }
    }

    /**
     * Estado CHASE:
     * - Sigue al jugador.
     * - Cuando el cooldown permite, si está a rango, inicia windup.
     */
    private void updateChase(double dt) {
        double[] pPos = playerPos.get();

        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        double dx = pPos[0] - bx;
        double dy = pPos[1] - by;

        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.001) dist = 0.001;

        // Movimiento hacia jugador
        view.setLayoutX(bx + (dx / dist) * speed * dt);
        view.setLayoutY(by + (dy / dist) * speed * dt);

        // Si aún hay cooldown, lo descontamos aquí también por seguridad
        if (cooldownTimer > 0.0) {
            cooldownTimer -= dt;
            return;
        }

        // Decide empezar dash si está a distancia razonable
        if (dist < 520.0) {
            state = State.WINDUP;
            windupTimer = (phase == 1) ? 0.55 : 0.40;

            // Tell visual: cambia color durante windup
            view.setFill(Color.YELLOWGREEN);
        }
    }

    /**
     * Estado WINDUP:
     * - Espera un pequeño tiempo.
     * - Fija dirección hacia el jugador al final del windup.
     */
    private void updateWindup(double dt) {
        windupTimer -= dt;
        if (windupTimer > 0.0) return;

        // Fijar dirección al jugador en el instante de iniciar el dash
        double[] pPos = playerPos.get();

        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        double dx = pPos[0] - bx;
        double dy = pPos[1] - by;

        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.001) dist = 0.001;

        dashDirX = dx / dist;
        dashDirY = dy / dist;

        state = State.DASH;
        dashTimer = (phase == 1) ? 0.65 : 0.85;

        mineDropTimer = 0.0;

        // Vuelve a color normal
        view.setFill(Color.DARKGREEN);
    }

    /**
     * Estado DASH:
     * - Avanza en línea recta.
     * - Deja minas cada X segundos.
     * - Al terminar, hace shockwave y entra en cooldown.
     */
    private void updateDash(double dt) {
        dashTimer -= dt;

        // Mover dash
        double bx = view.getLayoutX();
        double by = view.getLayoutY();
        view.setLayoutX(bx + dashDirX * dashSpeed * dt);
        view.setLayoutY(by + dashDirY * dashSpeed * dt);

        // Drop mines durante dash
        mineDropTimer -= dt;
        if (mineDropTimer <= 0.0) {
            mineDropTimer = (phase == 1) ? 0.18 : 0.12;
            spawnMine(view.getLayoutX(), view.getLayoutY());
        }

        // Fin del dash
        if (dashTimer <= 0.0) {
            doShockwave();

            state = State.COOLDOWN;
            cooldownTimer = (phase == 1) ? 1.2 : 0.9;
        }
    }

    /**
     * Estado COOLDOWN:
     * - Espera un tiempo antes de volver a CHASE.
     */
    private void updateCooldown(double dt) {
        cooldownTimer -= dt;
        if (cooldownTimer <= 0.0) {
            state = State.CHASE;
        }
    }

    /**
     * Ataque secundario mientras CHASE:
     * - Abanico de balas lentas hacia el jugador.
     *
     * Nota: lo he blindado para evitar divisiones raras si cambias shots a 1.
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

        // Si por lo que sea shots se convierte en 1, disparo único (sin división por 0)
        if (shots <= 1) {
            spawnProjectile(aimX, aimY, 210.0, 3.2, 1.0, bx, by);
            return;
        }

        int mid = shots / 2; // para 5 => 2, para 7 => 3
        for (int i = 0; i < shots; i++) {
            int off = i - mid;

            double stepDeg = spreadDeg / Math.max(1, mid);
            double a = Math.toRadians(off * stepDeg);

            // Rotación del vector (aimX, aimY)
            double rx = aimX * Math.cos(a) - aimY * Math.sin(a);
            double ry = aimX * Math.sin(a) + aimY * Math.cos(a);

            spawnProjectile(rx, ry, 210.0, 3.2, 1.0, bx, by);
        }
    }

    /**
     * Shockwave radial al terminar el dash.
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
     * Helper centralizado para crear y spawnear proyectiles sin duplicar código.
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
     * Spawnea una mina y la registra como "activa" para poder limpiarla en die().
     *
     * Importante:
     * - La mina llamará a un callback cuando se destruya para quitarse de la lista.
     */
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
                bossId,
                destroyedMine -> mines.remove(destroyedMine) // ✅ evita leak
        );

        mines.add(m);
        onSpawnProjectile.accept(m);
    }

    /**
     * Al morir:
     * - Destruye minas que queden vivas (idempotente).
     * - Limpia lista.
     * - Llama a super.die().
     */
    @Override
    protected void die() {
        for (Mine m : new ArrayList<>(mines)) {
            m.forceDestroy();
        }
        mines.clear();
        super.die();
    }

    /**
     * Restablece el color tras recibir daño.
     */
    @Override
    protected void updateColor() {
        view.setFill(Color.DARKGREEN);
    }

    // =========================================================
    // Mina: tras delay explota en anillo
    // =========================================================

    /**
     * Mina que explota tras un delay y genera un anillo de proyectiles.
     *
     * Nota:
     * - Es idempotente: destroy() puede llamarse varias veces sin romper nada.
     * - Notifica a BossCharger para quitarse de la lista "mines".
     */
    private static final class Mine implements GameEntity {

        private final Pane parent;
        private final Circle view;

        private final Consumer<GameEntity> onSpawn;
        private final Consumer<GameEntity> onRemove;

        private final Boss owner;
        private final String bossId;

        private final Consumer<Mine> onDestroyed;

        private double timer;
        private final int ringCount;
        private final double ringSpeed;

        private boolean destroyed = false;

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
         * Explosión radial de la mina.
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
         * Fuerza la destrucción (usado por BossCharger.die()).
         */
        void forceDestroy() {
            destroy();
        }

        /**
         * Destruye visualmente y pide eliminación del GameLoop.
         * Idempotente: solo se ejecuta una vez.
         */
        private void destroy() {
            if (destroyed) return;
            destroyed = true;

            parent.getChildren().remove(view);
            onRemove.accept(this);

            // ✅ importantísimo: quitarse de la lista del boss
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
