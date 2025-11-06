package com.layla.core;

import javafx.geometry.Bounds;
import javafx.scene.Node;

/** Entidad del juego actualizada por el GameLoop. */
public interface GameEntity {
    /** dt en segundos. */
    void update(double dt);

    /** Nodo JavaFX que representa la entidad. */
    Node getView();

    /** Bounds en coordenadas del padre (por defecto, bounds del view). */
    default Bounds getBounds() {
        return getView().getBoundsInParent();
    }

    /** Hook de colisión (no-op por defecto). */
    default void onCollision(GameEntity other) {
        // no-op
    }
}
