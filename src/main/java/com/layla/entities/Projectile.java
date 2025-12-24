package com.layla.entities;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.core.GameEntity;
import com.layla.model.Enemy;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Proyectil genérico del juego.
 * <p>
 * Esta clase maneja toda la lógica de disparo, tanto de aliados como de enemigos.
 * <p>
 * Capacidades soportadas:
 * <ul>
 * <li><strong>Pierce:</strong> Atraviesa un número determinado de objetivos.</li>
 * <li><strong>Bounce:</strong> Rebota contra los bordes de la pantalla.</li>
 * <li><strong>Homing:</strong> Persigue automáticamente a los objetivos (solo proyectiles del jugador).</li>
 * </ul>
 */
public final class Projectile implements GameEntity {

    private static final double RADIUS = 5.0;

    // Configuración de Homing (Persecución)
    private static final double HOMING_RANGE = 450.0;
    private static final double HOMING_TURN_SPEED = 5.0;

    private final Circle view = new Circle(RADIUS, Color.YELLOW);

    private final Pane pane;
    private final Consumer<GameEntity> onRemove;

    private final double speed;
    private final double lifetime;
    private final double damage;

    private final boolean fromEnemy;
    private final GameEntity owner;
    private final String sourceName;

    private double dirX;
    private double dirY;

    // Contadores de efectos restantes
    private int pierceRemaining;
    private int bounceRemaining;

    private final boolean homing;
    private final Supplier<List<GameEntity>> targetSupplier;

    /** Registro de entidades ya golpeadas para evitar daño múltiple por frame o en pierce. */
    private final Set<GameEntity> alreadyHit = new HashSet<>();

    private double timeAlive = 0.0;

    /** Bandera para asegurar idempotencia en la eliminación. */
    private boolean removed = false;

    /**
     * Constructor completo con todas las opciones.
     *
     * @param dirX           Dirección X normalizada.
     * @param dirY           Dirección Y normalizada.
     * @param speed          Velocidad en px/s.
     * @param lifetime       Tiempo de vida en segundos.
     * @param damage         Daño al impactar.
     * @param fromEnemy      True si es hostil al jugador.
     * @param pane           Panel donde se mueve.
     * @param onRemove       Callback para solicitar eliminación.
     * @param owner          Entidad que disparó (inmune al propio disparo).
     * @param sourceName     Nombre de la fuente (para logs/stats).
     * @param pierce         Cantidad de enemigos a atravesar.
     * @param bounce         Cantidad de rebotes en paredes.
     * @param homing         True si persigue enemigos.
     * @param targetSupplier Proveedor de lista de objetivos (para homing).
     */
    public Projectile(
            double dirX,
            double dirY,
            double speed,
            double lifetime,
            double damage,
            boolean fromEnemy,
            Pane pane,
            Consumer<GameEntity> onRemove,
            GameEntity owner,
            String sourceName,
            int pierce,
            int bounce,
            boolean homing,
            Supplier<List<GameEntity>> targetSupplier
    ) {
        this.pane = Objects.requireNonNull(pane, "pane");
        this.onRemove = Objects.requireNonNull(onRemove, "onRemove");

        this.speed = speed;
        this.lifetime = lifetime;
        this.damage = damage;

        this.fromEnemy = fromEnemy;
        this.owner = owner;
        this.sourceName = sourceName;

        this.pierceRemaining = pierce;
        this.bounceRemaining = bounce;

        this.homing = homing;
        this.targetSupplier = targetSupplier;

        setDirectionNormalized(dirX, dirY);

        view.setManaged(false);
        view.setStroke(Color.BLACK);
        view.setStrokeWidth(1.5);

        updateColor();
    }

    // Constructores de conveniencia (Overloads)

    public Projectile(
            double dirX, double dirY,
            double speed, double lifetime, double damage,
            boolean fromEnemy,
            Pane pane, Consumer<GameEntity> onRemove,
            GameEntity owner, String sourceName
    ) {
        this(dirX, dirY, speed, lifetime, damage, fromEnemy, pane, onRemove, owner, sourceName, 0, 0, false, null);
    }

    public Projectile(
            double dirX, double dirY,
            double speed, double lifetime, double damage,
            boolean fromEnemy,
            Pane pane, Consumer<GameEntity> onRemove,
            GameEntity owner, String sourceName,
            int pierce, int bounce
    ) {
        this(dirX, dirY, speed, lifetime, damage, fromEnemy, pane, onRemove, owner, sourceName, pierce, bounce, false, null);
    }

    /**
     * Actualiza la posición y lógica del proyectil.
     * <ol>
     * <li>Aplica lógica Homing si corresponde.</li>
     * <li>Mueve el proyectil.</li>
     * <li>Gestiona rebotes en los bordes del Pane.</li>
     * <li>Comprueba tiempo de vida (lifetime).</li>
     * <li>Elimina si sale de los límites sin rebotar.</li>
     * </ol>
     *
     * @param dt Delta time.
     */
    @Override
    public void update(double dt) {
        if (removed || dt <= 0.0) return;

        if (homing && !fromEnemy && targetSupplier != null) {
            updateHoming(dt);
        }

        final double prevX = view.getLayoutX();
        final double prevY = view.getLayoutY();

        double nextX = prevX + dirX * speed * dt;
        double nextY = prevY + dirY * speed * dt;

        boolean bounced = false;

        final double width = pane.getWidth();
        final double height = pane.getHeight();

        // Lógica de rebote (solo si el pane tiene dimensiones válidas)
        if (bounceRemaining > 0 && width > RADIUS * 2 && height > RADIUS * 2) {
            double minX = RADIUS;
            double minY = RADIUS;
            double maxX = width - RADIUS;
            double maxY = height - RADIUS;

            if (nextX <= minX) { nextX = minX; dirX = -dirX; bounced = true; }
            else if (nextX >= maxX) { nextX = maxX; dirX = -dirX; bounced = true; }

            if (nextY <= minY) { nextY = minY; dirY = -dirY; bounced = true; }
            else if (nextY >= maxY) { nextY = maxY; dirY = -dirY; bounced = true; }

            if (bounced) {
                bounceRemaining--;
                // Opcional: updateColor() si cambia al rebotar
            }
        }

        view.setLayoutX(nextX);
        view.setLayoutY(nextY);

        timeAlive += dt;
        if (timeAlive >= lifetime) {
            requestRemove();
            return;
        }

        // Si se sale de los límites y no rebotó, se elimina
        if (!bounced && width > 0 && height > 0 && isOutOfPaneBounds(width, height)) {
            requestRemove();
        }
    }

    /**
     * Normaliza el vector de dirección. Si es cero, asigna una dirección por defecto (Arriba).
     */
    private void setDirectionNormalized(double x, double y) {
        double len = Math.hypot(x, y);
        if (len < 1e-6) {
            x = 0;
            y = -1;
            len = 1;
        }
        this.dirX = x / len;
        this.dirY = y / len;
    }

    /**
     * Actualiza la dirección del proyectil para perseguir al objetivo más cercano.
     * Gira gradualmente (no instantáneo) hacia el objetivo.
     *
     * @param dt Delta time.
     */
    private void updateHoming(double dt) {
        List<GameEntity> targets;
        try {
            targets = targetSupplier.get();
        } catch (Exception ignore) {
            return;
        }
        if (targets == null || targets.isEmpty()) return;

        final double px = view.getLayoutX();
        final double py = view.getLayoutY();

        GameEntity closest = null;
        double closestDistSq = HOMING_RANGE * HOMING_RANGE;

        // Buscar target más cercano
        for (GameEntity e : targets) {
            if (e == null) continue;

            boolean isDead = false;
            if (e instanceof Enemy en) isDead = en.isDead();
            else if (e instanceof Boss b) isDead = b.isDead();
            if (isDead) continue;

            Bounds b = e.getBounds();
            double dx = b.getCenterX() - px;
            double dy = b.getCenterY() - py;

            double distSq = dx * dx + dy * dy;
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = e;
            }
        }

        if (closest == null) return;

        // Calcular ángulo hacia el target y girar
        Bounds cb = closest.getBounds();
        double tx = cb.getCenterX() - px;
        double ty = cb.getCenterY() - py;

        double currentAngle = Math.atan2(dirY, dirX);
        double targetAngle = Math.atan2(ty, tx);

        double diff = targetAngle - currentAngle;
        while (diff <= -Math.PI) diff += 2 * Math.PI;
        while (diff > Math.PI) diff -= 2 * Math.PI;

        double turn = HOMING_TURN_SPEED * dt;
        if (Math.abs(diff) <= turn) {
            currentAngle = targetAngle;
        } else {
            currentAngle += (diff > 0) ? turn : -turn;
        }

        dirX = Math.cos(currentAngle);
        dirY = Math.sin(currentAngle);
    }

    /**
     * Maneja la colisión con otras entidades.
     * <ul>
     * <li>Ignora colisión con el dueño (quien disparó).</li>
     * <li>Ignora colisiones repetidas con el mismo objetivo.</li>
     * <li>Aplica daño a enemigos o jugador según corresponda.</li>
     * <li>Reduce contadores de Pierce o destruye el proyectil.</li>
     * </ul>
     *
     * @param other Entidad con la que colisionó.
     */
    @Override
    public void onCollision(GameEntity other) {
        if (removed || other == null) return;
        if (owner != null && other == owner) return;
        if (alreadyHit.contains(other)) return;

        boolean hit = false;
        boolean forceDestroy = false;

        // Lógica de impacto según facción
        if (!fromEnemy && other instanceof Enemy e) {
            if (!e.isDead()) {
                e.applyDamage(damage);
                hit = true;
            }
        } else if (!fromEnemy && other instanceof Boss b) {
            if (!b.isDead()) {
                b.takeDamage(damage);
                hit = true;
            }
        } else if (fromEnemy && other instanceof Player p) {
            if (sourceName != null) p.setLastHitSource(sourceName);
            p.takeDamage(damage);
            hit = true;
        } else if (other instanceof Rock) {
            // Las rocas bloquean disparos
            hit = true;
            forceDestroy = true;
        }

        if (!hit) return;

        alreadyHit.add(other);

        if (forceDestroy) {
            requestRemove();
            return;
        }

        // Gestión de Pierce (perforación)
        if (pierceRemaining > 0) {
            pierceRemaining--;
        } else {
            requestRemove();
        }
    }

    /**
     * Pide al sistema que elimine este proyectil.
     * Idempotente: evita llamar al callback múltiples veces.
     */
    private void requestRemove() {
        if (removed) return;
        removed = true;
        onRemove.accept(this);
    }

    @Override public Node getView() { return view; }
    @Override public Bounds getBounds() { return view.getBoundsInParent(); }

    public double getDamage() { return damage; }
    public boolean isFromEnemy() { return fromEnemy; }
    public double getSpeed() { return speed; }

    /**
     * Verifica si el proyectil ha salido completamente del área visible.
     */
    private boolean isOutOfPaneBounds(double width, double height) {
        double x = view.getLayoutX();
        double y = view.getLayoutY();

        return (x < -RADIUS * 2 || y < -RADIUS * 2 ||
                x > width + RADIUS * 2 ||
                y > height + RADIUS * 2);
    }

    /**
     * Actualiza el color del proyectil según sus propiedades para feedback visual.
     */
    private void updateColor() {
        if (fromEnemy) {
            view.setFill(Color.ORANGERED);
            return;
        }

        if (homing) view.setFill(Color.PURPLE);
        else if (bounceRemaining > 0 && pierceRemaining > 0) view.setFill(Color.MAGENTA);
        else if (bounceRemaining > 0) view.setFill(Color.CYAN);
        else if (pierceRemaining > 0) view.setFill(Color.LIGHTGREEN);
        else view.setFill(Color.GOLD);
    }
}
