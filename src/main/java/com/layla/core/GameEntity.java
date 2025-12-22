package com.layla.core;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;

/**
 * Base contract for entities updated by the game loop and optionally collidable.
 */
public interface GameEntity {

    /** Fallback bounds for entities without view (keeps collision code null-safe). */
    Bounds EMPTY_BOUNDS = new BoundingBox(-1_000_000_000, -1_000_000_000, 0, 0);

    void update(double dt);

    Node getView();

    /**
     * Returns collision bounds. Defaults to the JavaFX node bounds.
     * If the entity has no view, returns a safe empty bounds.
     */
    default Bounds getBounds() {
        Node v = getView();
        return (v != null) ? v.getBoundsInParent() : EMPTY_BOUNDS;
    }

    /** Optional collision hook. */
    default void onCollision(GameEntity other) {}
}
