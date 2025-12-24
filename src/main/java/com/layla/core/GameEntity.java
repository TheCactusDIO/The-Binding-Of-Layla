package com.layla.core;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;

/**
 * Interfaz base para todas las entidades del juego.
 * <p>Define el contrato básico para ser gestionado por el {@link GameLoop}: actualización lógica y representación visual/física.</p>
 */
public interface GameEntity {

    /**
     * Límites vacíos y seguros para entidades que no tienen representación visual o física.
     * Evita devolver null en {@link #getBounds()}.
     */
    Bounds EMPTY_BOUNDS = new BoundingBox(-1_000_000_000, -1_000_000_000, 0, 0);

    /**
     * Método de actualización lógica llamado en cada frame.
     *
     * @param dt Delta time en segundos (tiempo transcurrido desde el último frame).
     */
    void update(double dt);

    /**
     * Obtiene el nodo JavaFX que representa visualmente a la entidad.
     *
     * @return Nodo gráfico o null si la entidad es invisible/lógica.
     */
    Node getView();

    /**
     * Obtiene los límites de colisión de la entidad.
     * <p>Por defecto delega en los bounds del nodo visual ({@link #getView()}).</p>
     *
     * @return Bounds para cálculos de colisión.
     */
    default Bounds getBounds() {
        Node v = getView();
        return (v != null) ? v.getBoundsInParent() : EMPTY_BOUNDS;
    }

    /**
     * Callback invocado cuando esta entidad colisiona con otra.
     * <p>La implementación por defecto no hace nada.</p>
     *
     * @param other La otra entidad involucrada en la colisión.
     */
    default void onCollision(GameEntity other) {}
}
