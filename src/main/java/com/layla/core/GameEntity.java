package com.layla.core;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;

/**
 * Contrato base para entidades actualizadas por el game loop y, opcionalmente, colisionables.
 */
public interface GameEntity {

    /**
     * Bounds de respaldo para entidades sin vista (mantiene el código de colisiones seguro ante null).
     */
    Bounds EMPTY_BOUNDS = new BoundingBox(-1_000_000_000, -1_000_000_000, 0, 0);

    /**
     * Actualiza la entidad.
     *
     * @param dt delta time en segundos
     */
    void update(double dt);

    /**
     * Devuelve el nodo JavaFX que representa visualmente a la entidad.
     *
     * @return nodo de la entidad (puede ser null)
     */
    Node getView();

    /**
     * Devuelve los límites de colisión.
     *
     * <p>Por defecto, se usan los bounds del nodo JavaFX en el padre.</p>
     * <p>Si la entidad no tiene vista, devuelve unos bounds vacíos seguros.</p>
     *
     * @return bounds de colisión
     */
    default Bounds getBounds() {
        Node v = getView();
        return (v != null) ? v.getBoundsInParent() : EMPTY_BOUNDS;
    }

    /**
     * Hook opcional de colisión.
     *
     * @param other otra entidad con la que colisiona
     */
    default void onCollision(GameEntity other) {}
}
