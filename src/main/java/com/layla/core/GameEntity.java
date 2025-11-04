package com.layla.core;

import javafx.geometry.Bounds;
import javafx.scene.Node;

/**
 * Contract for objects that participate in the game loop and collision system.
 */
public interface GameEntity {

    /**
     * Updates this entity state based on the elapsed time.
     *
     * @param dt time since the previous frame in seconds
     */
    void update(double dt);

    /**
     * @return JavaFX node used to render this entity within the scene graph
     */
    Node getView();

    /**
     * Returns the current bounds used for collision detection.
     *
     * @return bounds in parent coordinates
     */
    default Bounds getBounds() {
        return getView().getBoundsInParent();
    }

    /**
     * Hook invoked when this entity collides with another entity.
     *
     * @param other entity that intersected with this one
     */
    default void onCollision(GameEntity other) {
        // default no-op
    }
}
