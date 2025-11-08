// Issue 6 – Bucle del juego (AnimationTimer)
// Archivo: src/main/java/com/layla/core/GameLoop.java
// Propósito: Bucle principal del juego construido sobre AnimationTimer.
// - Gestiona entidades (alta/baja segura), llama update(dt) cada frame,
//   chequea colisiones AABB y adjunta/desadjunta los Node de JavaFX en el Pane objetivo.
// - Seguro para JavaFX Application Thread (usa Platform.runLater cuando toca).
// - Start/stop idempotentes. Métricas básicas (frameCount, lastDeltaTime).
// - Java 21 (pero compatible con 17+).

package com.layla.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

import com.layla.AppContext;
import com.layla.entities.Projectile;

/**
 * Bucle de juego basado en {@link AnimationTimer}.
 *
 * Flujo por frame: calcular dt (segundos) -> updateAll(dt) -> checkCollisions().
 * El "render" en JavaFX se consigue modificando propiedades del Node en update().
 *
 * Seguridad:
 * - Altas/bajas de entidades se encolan (pendingAdds/pendingRemovals) y se aplican
 *   de forma segura (sin ConcurrentModification) al inicio del frame o al parar.
 * - Cualquier mutación de la escena (add/remove de Node) se ejecuta en el JavaFX
 *   Application Thread (Platform.runLater si no estamos ya en él).
 */
public final class GameLoop {

    // ---------- LOGGING ----------
    private static final Logger LOG = Logger.getLogger(GameLoop.class.getName());

    // ---------- CONSTANTES ----------
    private static final double NANOS_TO_SECONDS = 1_000_000_000.0;

    // ---------- DEPENDENCIAS ----------
    /** Pane donde se añaden los Node de las entidades. */
    private final Pane renderRoot;

    // ---------- TIMER ----------
    /** Timer de JavaFX que llama a handle(now) ~60fps. */
    private final AnimationTimer timer;

    // ---------- ESTADO DE ENTIDADES ----------
    /** Entidades vivas (snapshot se usa para iterar sin ConcurrentModification). */
    private final List<GameEntity> entities = new ArrayList<>();
    /** Altas pendientes aplicadas al inicio de frame o al detener. */
    private final List<GameEntity> pendingAdds = new ArrayList<>();
    /** Bajas pendientes aplicadas al inicio de frame o al detener. */
    private final List<GameEntity> pendingRemovals = new ArrayList<>();
    /** Lock para proteger pendingAdds/pendingRemovals. */
    private final Object queueLock = new Object();

    // ---------- ESTADO DEL LOOP ----------
    private volatile boolean running;
    private boolean debugLoggingEnabled;
    private long frameCount;
    private double lastDeltaTime;
    private long lastFrameTimeNanos = -1L;

    // ---------- CONSTRUCTOR ----------
    /**
     * Crea un GameLoop que adjunta las vistas de las entidades en {@code renderRoot}.
     * @param renderRoot Pane objetivo del renderizado (no puede ser null)
     */
    public GameLoop(Pane renderRoot) {
        this.renderRoot = Objects.requireNonNull(renderRoot, "renderRoot");
        this.timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                onFrame(now);
            }
        };
    }

    // ---------- CONFIG / MÉTRICAS ----------
    /**
     * Activa/desactiva logging mínimo de frames y dt (nivel FINE).
     */
    public void setDebugLoggingEnabled(boolean enabled) {
        this.debugLoggingEnabled = enabled;
    }

    public boolean isDebugLoggingEnabled() {
        return debugLoggingEnabled;
    }

    /** @return número de frames procesados desde el último start(). */
    public long getFrameCount() {
        return frameCount;
    }

    /** @return delta time en segundos del último frame. */
    public double getLastDeltaTime() {
        return lastDeltaTime;
    }

    /** @return si el loop está arrancado. */
    public boolean isRunning() {
        return running;
    }

    // ---------- CONTROL DEL LOOP ----------
    /**
     * Arranca el loop (idempotente). Si no estamos en el FX thread, relanza vía runLater.
     */
    public void start() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::start);
            return;
        }
        if (running) {
            // Idempotente: no vuelve a arrancar si ya está en marcha.
            return;
        }
        running = true;
        frameCount = 0L;
        lastDeltaTime = 0.0;
        lastFrameTimeNanos = -1L;
        timer.start();
        if (LOG.isLoggable(Level.FINE)) LOG.fine("[GameLoop] started");
    }

    /**
     * Detiene el loop (idempotente). Drena colas pendientes y asegura que no quedan nodos colgando.
     */
    public void stop() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::stop);
            return;
        }
        if (!running) {
            return;
        }
        running = false;
        timer.stop();
        // Aplicar de inmediato altas/bajas pendientes para dejar el sistema consistente.
        processQueuesOnFxThread();
        lastFrameTimeNanos = -1L;
        if (LOG.isLoggable(Level.FINE)) LOG.fine("[GameLoop] stopped");
    }

    // ---------- GESTIÓN DE ENTIDADES ----------
    /**
     * Encola una entidad para participar en el loop. Su Node se añadirá al Pane en el FX thread.
     */
    public void addEntity(GameEntity entity) {
        Objects.requireNonNull(entity, "entity");
        boolean processImmediately;
        synchronized (queueLock) {
            if (!pendingAdds.contains(entity)) {
                pendingAdds.add(entity);
            }
            pendingRemovals.remove(entity);
            // Si no está corriendo, aplicamos ya para que quede visible al instante.
            processImmediately = !running;
        }
        if (processImmediately) {
            processQueuesOnFxThread();
        }
    }

    /**
     * Encola la retirada de una entidad del loop. Su Node se quitará del Pane en el FX thread.
     */
    public void removeEntity(GameEntity entity) {
        if (entity == null) return;
        boolean processImmediately;
        synchronized (queueLock) {
            pendingAdds.remove(entity);
            if (!pendingRemovals.contains(entity)) {
                pendingRemovals.add(entity);
            }
            processImmediately = !running;
        }
        if (processImmediately) {
            processQueuesOnFxThread();
        }
    }

    /**
     * Elimina TODAS las entidades actuales (y sus vistas del Pane).
     * Útil para resets entre niveles/escenas.
     */
    public void clearEntities() {
        synchronized (queueLock) {
            pendingAdds.clear();
            pendingRemovals.clear();
            pendingRemovals.addAll(entities);
        }
        processQueuesOnFxThread();
    }

    // ---------- BUCLE DE FRAME ----------
    /** Lógica por frame (invocada por AnimationTimer). */
   private void onFrame(long now) {
        // Primer tick tras start(): inicializa marco temporal y aplica colas.
        if (lastFrameTimeNanos < 0L) {
            lastFrameTimeNanos = now;
            applyPendingQueues();
            return;
        }

        // Calcular dt en segundos (mutable para poder clamp)
        double dt = (now - lastFrameTimeNanos) / NANOS_TO_SECONDS;
        lastFrameTimeNanos = now;

        // 🔧 Clamp del delta time para estabilidad (≈60 FPS lógico)
        if (dt < 1e-5) dt = 1e-5;            // evita dt=0
        if (dt > 1.0 / 60.0) dt = 1.0 / 60.0; // máx ≈ 16.67 ms

        lastDeltaTime = dt;  // guardar el dt realmente usado
        frameCount++;

        // Aplicar altas/bajas pendientes antes de iterar
        applyPendingQueues();

        // Crear snapshot inmutable para iterar sin ConcurrentModification
        final List<GameEntity> snapshot = entities.isEmpty() ? List.of() : List.copyOf(entities);

        // 1) Update (con micro-step opcional para proyectiles rápidos)
        final Set<GameEntity> microHandledProjectiles = new HashSet<>();
        updateAll(snapshot, dt, microHandledProjectiles);

        // 2) Colisiones AABB
        checkCollisions(snapshot, microHandledProjectiles);

        // 3) (Render implícito en update: mover Nodes)

        if (debugLoggingEnabled && LOG.isLoggable(Level.FINE)) {
            LOG.fine(String.format("[GameLoop] frame=%d dt=%.6f", frameCount, dt));
        }
    }


    // ---------- ETAPAS DEL FRAME ----------
    private void updateAll(List<GameEntity> snapshot, double dt, Set<GameEntity> microHandledProjectiles) {
        final double maxLinearStep = AppContext.balance().projectileMicroStepPx;
        final int maxSubSteps = AppContext.balance().projectileMaxSubSteps;
        final boolean microEnabled = maxLinearStep > 0.0 && maxSubSteps > 1;

        for (GameEntity entity : snapshot) {
            if (microEnabled && entity instanceof Projectile projectile) {
                boolean handled = runProjectileWithMicroSteps(
                        projectile, snapshot, dt, microHandledProjectiles, maxLinearStep, maxSubSteps);
                if (handled) {
                    continue;
                }
            }
            safeUpdate(entity, dt);
        }
    }

    private void checkCollisions(List<GameEntity> snapshot, Set<GameEntity> projectilesHandledAlready) {
        final int size = snapshot.size();
        for (int i = 0; i < size; i++) {
            final GameEntity a = snapshot.get(i);
            if (projectilesHandledAlready.contains(a)) continue;
            final Bounds aBounds = safeBounds(a);
            if (aBounds == null) continue;

            for (int j = i + 1; j < size; j++) {
                final GameEntity b = snapshot.get(j);
                if (projectilesHandledAlready.contains(b)) continue;
                final Bounds bBounds = safeBounds(b);
                if (bBounds == null) continue;

                // AABB simple usando Bounds#intersects
                if (aBounds.intersects(bBounds)) {
                    handleCollisionPair(a, b);
                }
            }
        }
    }

    private void safeUpdate(GameEntity entity, double dt) {
        try {
            entity.update(dt);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "[GameLoop] Exception in entity.update()", t);
        }
    }

    /**
     * Returns true if the projectile was sub-stepped this frame.
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
            // Movement small enough: fallback to regular path (handled later).
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

    private void handleCollisionPair(GameEntity a, GameEntity b) {
        try {
            a.onCollision(b);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "[GameLoop] Exception in a.onCollision()", t);
        }
        try {
            b.onCollision(a);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "[GameLoop] Exception in b.onCollision()", t);
        }
    }

    // ---------- UTILIDADES ----------
    /** Devuelve bounds seguros (null si la vista es null). */
    private Bounds safeBounds(GameEntity entity) {
        final Node view = entity.getView();
        if (view == null) return null;
        return entity.getBounds();
    }

    private boolean isMarkedForRemoval(GameEntity entity) {
        synchronized (queueLock) {
            return pendingRemovals.contains(entity);
        }
    }

    /** Aplica colas pendientes en el FX thread si no estamos ya en él. */
    private void processQueuesOnFxThread() {
        if (Platform.isFxApplicationThread()) {
            applyPendingQueues();
        } else {
            Platform.runLater(this::applyPendingQueues);
        }
    }

    /**
     * Aplica altas/bajas pendientes:
     * - Elimina entidades de la lista viva y quita sus Node del Pane.
     * - Añade entidades a la lista viva y añade sus Node al Pane.
     * Se ejecuta siempre en el FX thread (si no, llamar a processQueuesOnFxThread()).
     */
    private void applyPendingQueues() {
        final List<GameEntity> adds;
        final List<GameEntity> removals;

        synchronized (queueLock) {
            if (pendingAdds.isEmpty() && pendingRemovals.isEmpty()) {
                return;
            }
            adds = new ArrayList<>(pendingAdds);
            removals = new ArrayList<>(pendingRemovals);
            pendingAdds.clear();
            pendingRemovals.clear();
        }

        // Bajas primero (quitar vistas y eliminar de 'entities')
        if (!removals.isEmpty()) {
            entities.removeIf(removals::contains);
            for (GameEntity entity : removals) {
                final Node view = entity.getView();
                if (view != null) {
                    // Como ya estamos (o deberíamos estar) en el FX thread, podemos mutar directamente;
                    // si quisieras blindarte 100%, podrías envolver en Platform.runLater().
                    renderRoot.getChildren().remove(view);
                }
            }
        }

        // Altas después
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
