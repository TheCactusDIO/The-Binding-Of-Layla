package com.layla.core;

import javafx.geometry.Bounds;
import javafx.scene.Node;

/**
 * Contrato base para cualquier entidad del juego gestionada por el GameLoop.
 *
 * Una entidad suele tener:
 * - Lógica por frame (update)
 * - Un nodo JavaFX para renderizarse (getView)
 * - Un área de colisión / interacción (getBounds)
 * - Un hook opcional de colisión (onCollision)
 */
public interface GameEntity {

    /**
     * Actualiza la lógica de la entidad en cada tick del GameLoop.
     *
     * @param dt tiempo transcurrido desde el último frame, en segundos.
     *           Se usa para hacer movimiento y timers independientes del FPS.
     */
    void update(double dt);

    /**
     * Devuelve el nodo JavaFX que representa visualmente a la entidad.
     *
     * Nota: si devuelves null, la entidad puede existir a nivel lógico,
     * pero no podrá participar en cálculos que dependan de la vista
     * (como el getBounds() por defecto).
     */
    Node getView();

    /**
     * Devuelve los límites (Bounds) usados para colisiones, spawns y distancias.
     *
     * Por defecto se usan los bounds del nodo en su padre (BoundsInParent).
     * Si una entidad necesita una hitbox distinta al tamaño visual (ej: Player),
     * debe sobrescribir este método.
     */
    default Bounds getBounds() {
        Node v = getView();
        return (v != null) ? v.getBoundsInParent() : null;
    }

    /**
     * Hook opcional llamado cuando el sistema detecta una colisión con otra entidad.
     *
     * Se deja como "no-op" por defecto para que no sea obligatorio implementarlo.
     * Si tu juego necesita colisiones con lógica (daño por contacto, empujes, etc.),
     * las entidades interesadas pueden sobrescribirlo.
     */
    default void onCollision(GameEntity other) {
        // Intencionalmente vacío.
    }
}
