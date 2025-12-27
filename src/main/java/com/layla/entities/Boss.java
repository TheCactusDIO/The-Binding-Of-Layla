package com.layla.entities;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.core.GameEntity;

import javafx.animation.PauseTransition;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeType;
import javafx.util.Duration;

/**
 * Entidad base para los jefes del juego.
 * <p>
 * Provee la estructura común (vista, vida, callbacks de muerte/proyectiles) y
 * un comportamiento por defecto.
 * </p>
 * <p>
 * <strong>Nota de diseño:</strong>
 * <ul>
 * <li>Si la instancia es exactamente de la clase {@code Boss}, se utiliza el comportamiento "Spreader rework" (patrones de disparo, dash, strafe).</li>
 * <li>Si es una subclase (como {@link BossCharger}), se mantiene el comportamiento "legacy" (persecución simple) para no romper la lógica específica de esos jefes.</li>
 * </ul>
 * </p>
 */
public class Boss implements GameEntity {

    // =========================
    // Dependencias / Callbacks
    // =========================

    /** Panel contenedor donde se añade la vista del jefe. */
    protected final Pane parent;

    /** Representación visual del jefe (círculo). */
    protected final Circle view;

    /** Proveedor para obtener la posición actual del jugador [x, y]. */
    protected final Supplier<double[]> playerPos;

    /** Callback a ejecutar cuando el jefe muere. */
    protected final Consumer<Boss> onDeath;

    /** Callback para registrar nuevos proyectiles en el bucle del juego. */
    protected final Consumer<GameEntity> onSpawnProjectile;

    /**
     * Callback para eliminar correctamente proyectiles del GameLoop.
     * El {@link Projectile} lo usará para pedir su propia eliminación al impactar o salir de rango.
     */
    protected final Consumer<GameEntity> onRemoveProjectile;

    /**
     * Identificador único del jefe.
     * Útil para sistemas de estadísticas o para identificar la fuente de daño ("last hit source").
     */
    protected final String bossId;

    // =========================
    // Estado base del boss
    // =========================

    /** Vida actual del jefe. */
    protected double hp;

    /** Vida máxima del jefe. */
    protected double maxHp;

    /** Velocidad de movimiento base (píxeles por segundo). */
    protected double speed = 45.0;

    /** Indica si el jefe ha muerto para detener actualizaciones. */
    protected boolean dead = false;

    /** Fase actual del jefe (1 o 2). Afecta a la agresividad y patrones. */
    protected int phase = 1;

    /** Temporizador general para controlar la cadencia de ataques. */
    protected double attackTimer = 0.0;

    // =========================
    // Rework state (Exclusivo de Boss.class)
    // =========================

    private static final double DESIRED_RANGE = 210.0;
    private static final double TOO_CLOSE = 150.0;
    private static final double TOO_FAR = 320.0;

    private static final double STRAFE_SPEED_MULT = 0.75;
    private static final double APPROACH_SPEED_MULT = 1.10;
    private static final double RETREAT_SPEED_MULT = 1.25;

    private static final double DASH_DURATION_SEC = 0.55;

    /** Temporizador para cambiar entre patrones de ataque en el rework. */
    private double patternTimer = 0.0;

    /** Índice del patrón de ataque actual (0: Espiral, 1: Abanico, 2: Anillos). */
    private int patternIndex = 0;

    /** Ángulo acumulado para los disparos en espiral. */
    private double spiralAngle = 0.0;

    /** Dirección del movimiento lateral (1.0 o -1.0). */
    private double strafeSign = 1.0;

    /** Tiempo restante para que el dash esté disponible de nuevo. */
    private double dashCooldown = 3.0;

    /** Tiempo restante de duración del dash actual. */
    private double dashTimer = 0.0;

    /** Componente X de la dirección del dash. */
    private double dashDirX = 0.0;

    /** Componente Y de la dirección del dash. */
    private double dashDirY = 0.0;

    /** Indica si el jefe está ejecutando un dash actualmente. */
    private boolean dashing = false;

    /** Generador de números aleatorios para variaciones de comportamiento. */
    private final ThreadLocalRandom rng = ThreadLocalRandom.current();

    // =========================
    // Constructor
    // =========================

    /**
     * Crea una nueva instancia de un Boss.
     *
     * @param x                 Posición X inicial.
     * @param y                 Posición Y inicial.
     * @param maxHp             Vida máxima.
     * @param parent            Panel donde se renderizará.
     * @param playerPos         Supplier para conocer la posición del jugador.
     * @param onDeath           Callback al morir.
     * @param onSpawnProjectile Callback para crear proyectiles.
     * @param onRemoveProjectile Callback para eliminar proyectiles.
     * @param bossId            Identificador único (texto).
     */
    public Boss(
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
        this.parent = Objects.requireNonNull(parent, "parent");
        this.playerPos = Objects.requireNonNull(playerPos, "playerPos");
        this.onDeath = Objects.requireNonNull(onDeath, "onDeath");
        this.onSpawnProjectile = Objects.requireNonNull(onSpawnProjectile, "onSpawnProjectile");
        this.onRemoveProjectile = Objects.requireNonNull(onRemoveProjectile, "onRemoveProjectile");

        this.maxHp = maxHp;
        this.hp = maxHp;

        this.bossId = bossId;

        this.view = new Circle(40.0, Color.DARKRED);
        this.view.setStroke(Color.BLACK);
        this.view.setStrokeWidth(4.0);
        this.view.setStrokeType(StrokeType.INSIDE);
        this.view.setEffect(new DropShadow(20, Color.RED));

        this.view.setLayoutX(x);
        this.view.setLayoutY(y);

        parent.getChildren().add(this.view);
    }

    // =========================
    // Bucle de juego (Game Loop)
    // =========================

    /**
     * Actualiza la lógica del jefe en cada frame.
     * <p>
     * Verifica la vida (para evitar estados inconsistentes o "zombis"), y delega
     * la lógica de movimiento/ataque dependiendo de si es la clase base o una subclase.
     * </p>
     *
     * @param dt Delta time en segundos desde el último frame.
     */
    @Override
    public void update(double dt) {
        if (dead) return;

        if (hp <= 0.0) {
            die();
            return;
        }

        // Mantener legacy en subclases para no romper su IA específica
        if (getClass() != Boss.class) {
            updateLegacy(dt);
            return;
        }

        updateSpreaderRework(dt);
    }

    /**
     * Lógica de actualización antigua (Legacy).
     * <p>
     * Comportamiento simple: persigue al jugador en línea recta y cada cierto tiempo
     * lanza un ataque radial. Se conserva para las subclases que extienden Boss pero
     * no sobrescriben {@code update()}.
     * </p>
     *
     * @param dt Delta time en segundos.
     */
    private void updateLegacy(double dt) {
        double[] pPos = playerPos.get();

        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        double dx = pPos[0] - bx;
        double dy = pPos[1] - by;

        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > 1.0) {
            view.setLayoutX(bx + (dx / dist) * speed * dt);
            view.setLayoutY(by + (dy / dist) * speed * dt);
        }

        attackTimer += dt;
        if (attackTimer > 3.6) {
            performAttack();
            attackTimer = 0.0;
        }
    }

    /**
     * Lógica del "Rework" para el Boss base (tipo Spreader).
     * <p>
     * Implementa una IA más compleja que incluye:
     * <ul>
     * <li>Mantenimiento de distancia (ni muy cerca, ni muy lejos).</li>
     * <li>Movimiento lateral (strafe).</li>
     * <li>Habilidad de Dash (embestida) con telegrafiado previo.</li>
     * <li>Rotación de patrones de ataque (espiral, abanico, anillos).</li>
     * </ul>
     * </p>
     *
     * @param dt Delta time en segundos.
     */
    private void updateSpreaderRework(double dt) {
        double[] pPos = playerPos.get();
        double px = pPos[0];
        double py = pPos[1];

        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        double dx = px - bx;
        double dy = py - by;

        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.0001) dist = 0.0001;

        double ndx = dx / dist;
        double ndy = dy / dist;

        // -------------------------
        // Lógica de Dash
        // -------------------------
        if (dashing) {
            dashTimer -= dt;

            double dashSpeed = (phase == 1) ? 290.0 : 360.0;
            view.setLayoutX(bx + dashDirX * dashSpeed * dt);
            view.setLayoutY(by + dashDirY * dashSpeed * dt);

            // Disparos residuales durante el dash (más agresivo en fase 2)
            if (phase >= 2 && rng.nextDouble() < 0.12) {
                spawnMiniSpiralBurst(1, 180.0);
            }

            if (dashTimer <= 0.0) {
                dashing = false;
                dashCooldown = (phase == 1) ? 3.2 : 2.2;

                // Shockwave al terminar el dash (impacto)
                spawnRing(6 + phase, 220.0 + phase * 30.0, 2.2, 1.0);
            }

        } else {
            // -------------------------
            // Movimiento normal con Strafe
            // -------------------------

            // Vector perpendicular para el strafe
            double sx = -ndy * strafeSign;
            double sy =  ndx * strafeSign;

            double mult;
            if (dist < TOO_CLOSE) mult = RETREAT_SPEED_MULT;
            else if (dist > TOO_FAR) mult = APPROACH_SPEED_MULT;
            else mult = 1.0;

            double moveX = ndx * mult;
            double moveY = ndy * mult;

            // Si está en rango ideal, prioriza strafe sobre acercarse
            double strafeWeight = (dist > DESIRED_RANGE) ? 0.30 : 0.60;
            double followWeight = 1.0 - strafeWeight;

            double finalX = moveX * followWeight + sx * strafeWeight * STRAFE_SPEED_MULT;
            double finalY = moveY * followWeight + sy * strafeWeight * STRAFE_SPEED_MULT;

            // Si está demasiado cerca, fuerza la retirada
            if (dist < TOO_CLOSE) {
                finalX = -ndx * RETREAT_SPEED_MULT + sx * 0.45;
                finalY = -ndy * RETREAT_SPEED_MULT + sy * 0.45;
            }

            view.setLayoutX(bx + finalX * speed * dt);
            view.setLayoutY(by + finalY * speed * dt);

            // Cambia la dirección del strafe aleatoriamente
            if (rng.nextDouble() < 0.01) strafeSign *= -1.0;

            // Iniciar Dash si el cooldown terminó y hay distancia suficiente
            dashCooldown -= dt;
            if (dashCooldown <= 0.0 && dist > 120.0) {
                startDashTelegraphed(ndx, ndy);
            }
        }

        // -------------------------
        // Temporizadores de Ataque
        // -------------------------
        patternTimer += dt;

        double baseAttackInterval = (phase == 1) ? 3.6 : 2.6;
        attackTimer += dt;
        if (attackTimer >= baseAttackInterval) {
            performAttack();
            attackTimer = 0.0;
        }

        // Cambio de patrón cíclico
        double patternDuration = (phase == 1) ? 4.2 : 3.3;
        if (patternTimer >= patternDuration) {
            patternTimer = 0.0;
            patternIndex = (patternIndex + 1) % 3;
        }

        // Ejecución de patrones de "presión" continua (disparos entre ataques principales)
        switch (patternIndex) {
            case 0 -> { // Presión en espiral
                spiralAngle += dt * ((phase == 1) ? 3.2 : 4.6);
                if (rng.nextDouble() < ((phase == 1) ? 0.08 : 0.14)) {
                    spawnSpiralShot(spiralAngle, 240.0 + phase * 40.0);
                }
            }
            case 1 -> { // Ráfagas apuntadas aleatorias
                if (rng.nextDouble() < ((phase == 1) ? 0.04 : 0.12)) {
                    spawnAimedFan(2 + phase, 0.35, 260.0 + phase * 40.0);
                }
            }
            default -> { // Anillos de negación de área
                if (rng.nextDouble() < ((phase == 1) ? 0.03 : 0.08)) {
                    spawnRing(8 + phase, 170.0, 2.2, 1.0);
                }
            }
        }
    }

    /**
     * Inicia la secuencia de Dash mostrando un aviso visual (telegraph).
     * El jefe cambia de color brevemente antes de lanzarse.
     *
     * @param ndx Componente X de la dirección del dash normalizada.
     * @param ndy Componente Y de la dirección del dash normalizada.
     */
    private void startDashTelegraphed(double ndx, double ndy) {
        dashing = true;
        dashTimer = DASH_DURATION_SEC;
        dashDirX = ndx;
        dashDirY = ndy;

        view.setStroke(Color.ORANGE);

        PauseTransition pt = new PauseTransition(Duration.millis(140));
        pt.setOnFinished(e -> {
            if (!dead) {
                view.setStroke(phase >= 2 ? Color.YELLOW : Color.BLACK);
            }
        });
        pt.play();
    }

    // ======================
    // Ataques
    // ======================

    /**
     * Ejecuta el ataque principal del jefe.
     * <p>
     * Si es la clase base, ejecuta el ataque "Spreader" (abanico + anillo en fase 2).
     * Si es una subclase, ejecuta el ataque legacy (radial simple).
     * </p>
     */
    protected void performAttack() {
        if (dead) return;

        if (getClass() != Boss.class) {
            performAttackLegacy();
            return;
        }

        // Ataque principal del Spreader: Abanico dirigido
        spawnAimedFan(4 + phase, 0.50, 280.0 + phase * 30.0);

        // En fase 2 añade un anillo extra
        if (phase >= 2) {
            spawnRing(4, 210.0, 2.2, 1.0);
        }
    }

    /**
     * Ataque legacy: disparo radial en 360 grados.
     * Usado por defecto en subclases que no sobrescriben performAttack.
     */
    private void performAttackLegacy() {
        int projectiles = 5 + phase;
        for (int i = 0; i < projectiles; i++) {
            double angle = (2.0 * Math.PI / projectiles) * i;
            spawnProjectile(Math.cos(angle), Math.sin(angle), 200.0, 1.6, 1.0);
        }
    }

    // ======================
    // Helpers de Proyectiles
    // ======================

    /**
     * Crea y genera un proyectil básico desde la posición actual del jefe.
     *
     * @param dirX   Componente X de la dirección.
     * @param dirY   Componente Y de la dirección.
     * @param speed  Velocidad del proyectil.
     * @param radius Radio del proyectil (tamaño visual/hitbox).
     * @param damage Daño que inflige.
     */
    private void spawnProjectile(double dirX, double dirY, double speed, double radius, double damage) {
        Projectile p = new Projectile(
                dirX, dirY,
                speed,
                radius, damage,
                true, // es enemigo
                parent,
                onRemoveProjectile,
                this,
                bossId
        );

        double bx = view.getLayoutX();
        double by = view.getLayoutY();
        p.getView().setLayoutX(bx);
        p.getView().setLayoutY(by);

        onSpawnProjectile.accept(p);
    }

    /**
     * Dispara un único proyectil en un ángulo determinado (patrón espiral).
     *
     * @param angle Ángulo en radianes.
     * @param speed Velocidad del proyectil.
     */
    private void spawnSpiralShot(double angle, double speed) {
        spawnProjectile(Math.cos(angle), Math.sin(angle), speed, 2.2, 1.0);
    }

    /**
     * Dispara una pequeña ráfaga de proyectiles en espiral rápida.
     *
     * @param count Número de disparos en la ráfaga.
     * @param speed Velocidad de los proyectiles.
     */
    private void spawnMiniSpiralBurst(int count, double speed) {
        for (int i = 0; i < count; i++) {
            spiralAngle += 0.55;
            spawnSpiralShot(spiralAngle, speed);
        }
    }

    /**
     * Dispara un abanico de proyectiles centrado en la dirección del jugador.
     *
     * @param shots         Número total de proyectiles en el abanico.
     * @param spreadRadians Apertura total del abanico en radianes.
     * @param speed         Velocidad de los proyectiles.
     */
    private void spawnAimedFan(int shots, double spreadRadians, double speed) {
        double[] pPos = playerPos.get();

        double bx = view.getLayoutX();
        double by = view.getLayoutY();

        double dx = pPos[0] - bx;
        double dy = pPos[1] - by;
        double base = Math.atan2(dy, dx);

        int half = shots / 2;
        for (int i = -half; i <= half; i++) {
            // Evita división por cero si shots es 1 (aunque el bucle lo maneja)
            double t = (half == 0) ? 0.0 : (i / (double) half);
            double ang = base + t * spreadRadians;
            spawnProjectile(Math.cos(ang), Math.sin(ang), speed, 2.2, 1.0);
        }
    }

    /**
     * Dispara un anillo de proyectiles en todas direcciones (360 grados).
     *
     * @param projectiles Número de proyectiles.
     * @param speed       Velocidad de los proyectiles.
     * @param radius      Radio del proyectil.
     * @param damage      Daño del proyectil.
     */
    private void spawnRing(int projectiles, double speed, double radius, double damage) {
        for (int i = 0; i < projectiles; i++) {
            double angle = (2.0 * Math.PI / projectiles) * i;
            spawnProjectile(Math.cos(angle), Math.sin(angle), speed, radius, damage);
        }
    }

    // ======================
    // Daño, Fases y Muerte
    // ======================

    /**
     * Aplica daño al jefe, gestiona el parpadeo visual y comprueba cambios de fase o muerte.
     *
     * @param amount Cantidad de daño a aplicar.
     */
    public void takeDamage(double amount) {
        if (dead) return;

        hp -= amount;

        // Feedback visual: parpadeo blanco
        view.setFill(Color.WHITE);
        PauseTransition flash = new PauseTransition(Duration.millis(100));
        flash.setOnFinished(e -> {
            if (!dead) updateColor();
        });
        flash.play();

        if (hp <= 0.0) {
            die();
        } else {
            checkPhaseChange();
        }
    }

    /**
     * Restaura el color base del jefe tras el parpadeo de daño.
     * Puede ser sobrescrito por subclases para restaurar su color específico.
     */
    protected void updateColor() {
        view.setFill(Color.DARKRED);
    }

    /**
     * Comprueba si se debe transicionar a la Fase 2 (bajo el 50% de vida).
     * En Fase 2, aumenta velocidad y cambia el color del borde.
     */
    protected void checkPhaseChange() {
        if (phase == 1 && hp < maxHp * 0.5) {
            phase = 2;
            speed *= 1.35;
            view.setStroke(Color.YELLOW);

            // En fase 2, permite que el próximo dash llegue antes
            dashCooldown = Math.min(dashCooldown, 1.8);
        }
    }

    /**
     * Gestiona la muerte del jefe.
     * Elimina su vista y notifica al sistema a través del callback {@code onDeath}.
     */
    protected void die() {
        if (dead) return;
        dead = true;
        parent.getChildren().remove(view);
        onDeath.accept(this);
    }

    // ======================
    // Getters y Colisión
    // ======================

    public double getHp() { return hp; }
    public double getMaxHp() { return maxHp; }
    public boolean isDead() { return dead; }

    @Override public Node getView() { return view; }
    @Override public Bounds getBounds() { return view.getBoundsInParent(); }

    /**
     * Maneja la colisión con otras entidades.
     * Si colisiona con el jugador, registra este jefe como la fuente del último golpe.
     */
    @Override
    public void onCollision(GameEntity other) {
        if (other instanceof Player p) {
            p.setLastHitSource(bossId);
        }
    }
}
