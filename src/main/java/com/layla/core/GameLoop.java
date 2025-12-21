package com.layla.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.layla.AppContext;
import com.layla.entities.Projectile;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

/**
 * Bucle principal del juego basado en {@link AnimationTimer}.
 *
 * Responsabilidades:
 * - Ejecutar el update(dt) de todas las entidades cada frame.
 * - Gestionar altas/bajas de entidades de forma segura (colas pendientes).
 * - Gestionar colisiones AABB usando {@link Bounds#intersects(Bounds)}.
 * - Adjuntar/desadjuntar los {@link Node} de las entidades al {@link Pane} de render.
 *
 * Nota importante:
 * AnimationTimer se ejecuta en el JavaFX Application Thread, así que cualquier
 * cambio en el Scene Graph es seguro si se hace desde el propio loop.
 * Aun así, este GameLoop se blinda para llamadas a start/stop/add/remove desde otros hilos.
 */
public final class GameLoop {

    // =========================
    // LOGGING / CONSTANTES
    // =========================

    private static final Logger LOG = Logger.getLogger(GameLoop.class.getName());
    private static final double NANOS_TO_SECONDS = 1_000_000_000.0;

    // =========================
    // DEPENDENCIAS
    // =========================

    /** Pane donde se pintan (se añaden) los Node de las entidades. */
    private final Pane renderRoot;

    // =========================
    // TIMER
    // =========================

    /** Timer de JavaFX que ejecuta handle(now) ~60fps (según la plataforma). */
    private final AnimationTimer timer;

    // =========================
    // ENTIDADES + COLAS (seguras)
    // =========================

    /** Lista principal de entidades vivas. */
    private final List<GameEntity> entities = new ArrayList<>();

    /** Entidades pendientes de añadir (se aplican al inicio del frame). */
    private final List<GameEntity> pendingAdds = new ArrayList<>();

    /** Entidades pendientes de quitar (se aplican al inicio del frame). */
    private final List<GameEntity> pendingRemovals = new ArrayList<>();

    /** Lock para proteger las colas pendingAdds/pendingRemovals. */
    private final Object queueLock = new Object();

    // =========================
    // ESTADO DEL LOOP
    // =========================

    private volatile boolean running;
    private boolean debugLoggingEnabled;
    private long frameCount;
    private double lastDeltaTime;
    private long lastFrameTimeNanos = -1L;

    // =========================
    // CONSTRUCTOR
    // =========================

    /**
     * Crea un GameLoop que adjunta las vistas de las entidades al {@code renderRoot}.
     *
     * @param renderRoot Pane objetivo del renderizado (no puede ser null)
     */
    public GameLoop(Pane renderRoot) {
        this.renderRoot = Objects.requireNonNull(renderRoot, "renderRoot");
        this.timer = new AnimationTimer() {
            @Override public void handle(long now) {
                onFrame(now);
            }
        };
    }

    // =========================
    // CONFIG / MÉTRICAS
    // =========================

    /**
     * Activa/desactiva el logging de depuración (frames y dt).
     * Útil para verificar estabilidad del loop y detectar picos de dt.
     */
    public void setDebugLoggingEnabled(boolean enabled) {
        this.debugLoggingEnabled = enabled;
    }

    public boolean isDebugLoggingEnabled() {
        return debugLoggingEnabled;
    }

    /** @return número de frames procesados desde el último {@link #start()}. */
    public long getFrameCount() {
        return frameCount;
    }

    /** @return delta time (segundos) usado en el último frame. */
    public double getLastDeltaTime() {
        return lastDeltaTime;
    }

    /** @return true si el loop está arrancado. */
    public boolean isRunning() {
        return running;
    }

    // =========================
    // CONTROL DEL LOOP
    // =========================

    /**
     * Arranca el loop (idempotente).
     * - Si se llama desde un hilo que no es FX, se reenvía con {@link Platform#runLater(Runnable)}.
     * - Resetea métricas y reinicia el contador de tiempo interno.
     */
    public void start() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::start);
            return;
        }
        if (running) return;

        running = true;
        frameCount = 0L;
        lastDeltaTime = 0.0;
        lastFrameTimeNanos = -1L;

        timer.start();

        if (LOG.isLoggable(Level.FINE)) LOG.fine("[GameLoop] started");
    }

    /**
     * Detiene el loop (idempotente).
     * - Si se llama fuera del hilo FX, se reenvía con {@link Platform#runLater(Runnable)}.
     * - Drena colas pendientes para dejar el sistema consistente (sin entidades “a medio quitar/poner”).
     */
    public void stop() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::stop);
            return;
        }
        if (!running) return;

        running = false;
        timer.stop();

        // Dejamos el estado consistente al parar.
        applyPendingQueuesFxOnly();

        lastFrameTimeNanos = -1L;

        if (LOG.isLoggable(Level.FINE)) LOG.fine("[GameLoop] stopped");
    }

    // =========================
    // GESTIÓN DE ENTIDADES
    // =========================

    /**
     * Encola una entidad para participar en el loop.
     * - La entidad se añade a la lista viva al inicio del siguiente frame.
     * - Su Node se añadirá al Pane en el hilo FX.
     * - Si el loop NO está corriendo, se aplica inmediatamente para que sea visible al instante.
     */
    public void addEntity(GameEntity entity) {
        Objects.requireNonNull(entity, "entity");

        boolean applyNow;
        synchronized (queueLock) {
            if (!pendingAdds.contains(entity)) pendingAdds.add(entity);
            pendingRemovals.remove(entity);
            applyNow = !running;
        }

        if (applyNow) processQueuesOnFxThread();
    }

    /**
     * Encola la retirada de una entidad del loop.
     * - La entidad se elimina de la lista viva al inicio del siguiente frame.
     * - Su Node se quitará del Pane en el hilo FX.
     * - Si el loop NO está corriendo, se aplica inmediatamente.
     */
    public void removeEntity(GameEntity entity) {
        if (entity == null) return;

        boolean applyNow;
        synchronized (queueLock) {
            pendingAdds.remove(entity);
            if (!pendingRemovals.contains(entity)) pendingRemovals.add(entity);
            applyNow = !running;
        }

        if (applyNow) processQueuesOnFxThread();
    }

    /**
     * Elimina todas las entidades actuales (y sus vistas del Pane).
     * Útil para resets entre pisos/escenas.
     */
    public void clearEntities() {
        synchronized (queueLock) {
            pendingAdds.clear();
            pendingRemovals.clear();
            pendingRemovals.addAll(entities);
        }
        processQueuesOnFxThread();
    }

    // =========================
    // FRAME
    // =========================

    /**
     * Tick interno ejecutado por {@link AnimationTimer}.
     * Calcula dt, aplica colas, actualiza entidades y resuelve colisiones.
     */
    private void onFrame(long now) {
        // Primer tick tras start(): inicializa reloj y aplica colas.
        if (lastFrameTimeNanos < 0L) {
            lastFrameTimeNanos = now;
            applyPendingQueuesFxOnly();
            return;
        }

        double dt = (now - lastFrameTimeNanos) / NANOS_TO_SECONDS;
        lastFrameTimeNanos = now;

        // Clamp de dt para estabilidad (evita saltos enormes o dt=0).
        if (dt < 1e-5) dt = 1e-5;
        if (dt > 1.0 / 60.0) dt = 1.0 / 60.0;

        lastDeltaTime = dt;
        frameCount++;

        // Aplicar altas/bajas pendientes antes de actualizar.
        applyPendingQueuesFxOnly();

        // Snapshot para iterar sin ConcurrentModification.
        final List<GameEntity> snapshot = entities.isEmpty() ? List.of() : List.copyOf(entities);

        // Proyectiles rápidos: micro-steps opcionales para no “atravesar” objetivos.
        final Set<GameEntity> microHandledProjectiles = new HashSet<>();

        updateAll(snapshot, dt, microHandledProjectiles);
        checkCollisions(snapshot, microHandledProjectiles);

        if (debugLoggingEnabled && LOG.isLoggable(Level.FINE)) {
            LOG.fine(String.format("[GameLoop] frame=%d dt=%.6f", frameCount, dt));
        }
    }

    // =========================
    // ETAPAS DEL FRAME
    // =========================

    /**
     * Ejecuta update(dt) a todas las entidades.
     * Si está activado el micro-step para proyectiles, los proyectiles rápidos se actualizan en sub-pasos
     * y se checan colisiones en cada sub-paso.
     */
    private void updateAll(List<GameEntity> snapshot, double dt, Set<GameEntity> microHandledProjectiles) {
        final double maxLinearStep = AppContext.balance().projectileMicroStepPx;
        final int maxSubSteps = AppContext.balance().projectileMaxSubSteps;
        final boolean microEnabled = maxLinearStep > 0.0 && maxSubSteps > 1;

        for (GameEntity entity : snapshot) {
            if (microEnabled && entity instanceof Projectile projectile) {
                boolean handled = runProjectileWithMicroSteps(
                        projectile, snapshot, dt, microHandledProjectiles, maxLinearStep, maxSubSteps);
                if (handled) continue;
            }
            safeUpdate(entity, dt);
        }
    }

    /**
     * Recorre pares de entidades y llama a onCollision si sus bounds se solapan.
     * Se salta los proyectiles que ya fueron procesados en micro-steps para no duplicar colisiones.
     */
    private void checkCollisions(List<GameEntity> snapshot, Set<GameEntity> alreadyHandled) {
        final int size = snapshot.size();

        for (int i = 0; i < size; i++) {
            final GameEntity a = snapshot.get(i);
            if (alreadyHandled.contains(a)) continue;

            final Bounds aBounds = safeBounds(a);
            if (aBounds == null) continue;

            for (int j = i + 1; j < size; j++) {
                final GameEntity b = snapshot.get(j);
                if (alreadyHandled.contains(b)) continue;

                final Bounds bBounds = safeBounds(b);
                if (bBounds == null) continue;

                if (aBounds.intersects(bBounds)) {
                    handleCollisionPair(a, b);
                }
            }
        }
    }

    /**
     * Ejecuta entity.update(dt) y captura cualquier excepción para que una entidad rota
     * no tumbe el juego completo.
     */
    private void safeUpdate(GameEntity entity, double dt) {
        try {
            entity.update(dt);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "[GameLoop] Excepción en entity.update()", t);
        }
    }

    /**
     * Ejecuta un proyectil con sub-pasos si su desplazamiento este frame es demasiado grande.
     * Esto reduce el “tunneling” (atravesar enemigos/obstáculos sin colisionar).
     *
     * @return true si se aplicó micro-step (y por tanto el proyectil ya quedó resuelto este frame).
     */
    private boolean runProjectileWithMicroSteps(
            Projectile projectile,
            List<GameEntity> snapshot,
            double dt,
            Set<GameEntity> microHandledProjectiles,
            double maxLinearStep,
            int maxSubSteps) {

        final double displacement = projectile.getSpeed() * dt;
        if (!(displacement > maxLinearStep)) {
            // Movimiento pequeño: se procesa como entidad normal.
            return false;
        }

        int steps = (int) Math.ceil(displacement / maxLinearStep);
        steps = Math.min(maxSubSteps, Math.max(1, steps));
        final double subDt = dt / steps;

        for (int i = 0; i < steps; i++) {
            if (isMarkedForRemoval(projectile)) break;

            safeUpdate(projectile, subDt);

            if (isMarkedForRemoval(projectile)) break;

            runCollisionsForEntity(projectile, snapshot);

            if (isMarkedForRemoval(projectile)) break;
        }

        microHandledProjectiles.add(projectile);
        return true;
    }

    /**
     * Chequea colisiones de una entidad contra el resto y dispara onCollision.
     * Se usa principalmente para proyectiles dentro de micro-steps.
     */
    private void runCollisionsForEntity(GameEntity entity, List<GameEntity> snapshot) {
        for (GameEntity other : snapshot) {
            if (other == entity) continue;
            if (isMarkedForRemoval(entity)) return;

            final Bounds aBounds = safeBounds(entity);
            if (aBounds == null) return;

            final Bounds bBounds = safeBounds(other);
            if (bBounds == null) continue;

            if (aBounds.intersects(bBounds)) {
                handleCollisionPair(entity, other);
                if (isMarkedForRemoval(entity)) return;
            }
        }
    }

    /**
     * Llama a onCollision en ambos sentidos (a->b y b->a).
     * Se protege con try/catch para evitar que una entidad rompa el loop.
     */
    private void handleCollisionPair(GameEntity a, GameEntity b) {
        try {
            a.onCollision(b);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "[GameLoop] Excepción en a.onCollision()", t);
        }
        try {
            b.onCollision(a);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "[GameLoop] Excepción en b.onCollision()", t);
        }
    }

    // =========================
    // UTILIDADES
    // =========================

    /**
     * Devuelve bounds de forma segura.
     * Si la entidad no tiene view o su getBounds falla, devuelve null.
     */
    private Bounds safeBounds(GameEntity entity) {
        try {
            final Node view = entity.getView();
            if (view == null) return null;
            return entity.getBounds();
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "[GameLoop] Bounds inválidos en entidad: " + entity.getClass().getSimpleName(), t);
            return null;
        }
    }

    /**
     * Indica si una entidad está marcada para eliminación (encolada en pendingRemovals).
     * Se usa sobre todo para cortar micro-steps cuando un proyectil ya “murió” en una colisión.
     */
    private boolean isMarkedForRemoval(GameEntity entity) {
        synchronized (queueLock) {
            return pendingRemovals.contains(entity);
        }
    }

    /**
     * Asegura que la aplicación de colas se ejecuta en el hilo FX.
     * Si se llama desde otro hilo, se reenvía con Platform.runLater.
     */
    private void processQueuesOnFxThread() {
        if (Platform.isFxApplicationThread()) {
            applyPendingQueuesFxOnly();
        } else {
            Platform.runLater(this::applyPendingQueuesFxOnly);
        }
    }

    /**
     * Aplica altas/bajas pendientes (SOLO debe ejecutarse en el hilo FX).
     *
     * Orden:
     * 1) Bajas: quitar de entities y remover Node del Pane.
     * 2) Altas: añadir a entities y añadir Node al Pane.
     */
    private void applyPendingQueuesFxOnly() {
        if (!Platform.isFxApplicationThread()) {
            // Blindaje: por si alguien llama mal a este método.
            Platform.runLater(this::applyPendingQueuesFxOnly);
            return;
        }

        final List<GameEntity> adds;
        final List<GameEntity> removals;

        synchronized (queueLock) {
            if (pendingAdds.isEmpty() && pendingRemovals.isEmpty()) return;

            adds = new ArrayList<>(pendingAdds);
            removals = new ArrayList<>(pendingRemovals);
            pendingAdds.clear();
            pendingRemovals.clear();
        }

        // 1) Bajas
        if (!removals.isEmpty()) {
            entities.removeIf(removals::contains);
            for (GameEntity entity : removals) {
                final Node view = entity.getView();
                if (view != null) {
                    renderRoot.getChildren().remove(view);
                }
            }
        }

        // 2) Altas
        for (GameEntity entity : adds) {
            if (entity == null || entities.contains(entity)) continue;

            entities.add(entity);

            final Node view = entity.getView();
            if (view != null && !renderRoot.getChildren().contains(view)) {
                renderRoot.getChildren().add(view);
            }
        }
    }
}
