package com.layla.model;

import com.layla.core.GameEntity;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Pared/colisionador invisible usado para bloquear el movimiento de entidades.
 *
 * <p>Se representa con un {@link Rectangle} sin relleno ni borde (modo normal).
 * En modo depuración puede mostrarse con un color semitransparente.</p>
 *
 * <p>Esta entidad no tiene lógica de actualización; su propósito es únicamente
 * aportar {@link #getBounds()} para el sistema de colisiones.</p>
 */
public final class InvisibleWall implements GameEntity {

    private static final Color DEBUG_FILL = Color.color(1, 0, 0, 0.08);
    private static final Color DEBUG_STROKE = Color.color(1, 0, 0, 0.35);

    private final Rectangle rect;

    /**
     * Crea una pared invisible.
     *
     * @param x     posición X (layout) en el mundo
     * @param y     posición Y (layout) en el mundo
     * @param w     anchura del colisionador
     * @param h     altura del colisionador
     * @param debug si {@code true}, se renderiza con un overlay visible para depuración
     */
    public InvisibleWall(double x, double y, double w, double h, boolean debug) {
        rect = new Rectangle(w, h);
        rect.setManaged(false);
        rect.setLayoutX(x);
        rect.setLayoutY(y);
        rect.setMouseTransparent(true);

        setDebugVisible(debug);
    }

    /**
     * No hace nada: la pared no tiene comportamiento por frame.
     *
     * @param dt delta time en segundos
     */
    @Override
    public void update(double dt) {
        // Sin lógica: entidad estática de colisión.
    }

    /**
     * @return nodo JavaFX que representa la pared (normalmente transparente).
     */
    @Override
    public Node getView() {
        return rect;
    }

    /**
     * Devuelve los límites de colisión basados en el layout y tamaño del rectángulo.
     *
     * @return bounds de colisión
     */
    @Override
    public Bounds getBounds() {
        return new BoundingBox(
                rect.getLayoutX(),
                rect.getLayoutY(),
                rect.getWidth(),
                rect.getHeight()
        );
    }

    /**
     * Reposiciona la pared.
     *
     * @param x nueva posición X
     * @param y nueva posición Y
     */
    public void setPosition(double x, double y) {
        rect.setLayoutX(x);
        rect.setLayoutY(y);
    }

    /**
     * Activa/desactiva la visualización de depuración.
     *
     * @param debugVisible {@code true} para mostrar overlay, {@code false} para hacerlo invisible
     */
    public void setDebugVisible(boolean debugVisible) {
        if (debugVisible) {
            rect.setFill(DEBUG_FILL);
            rect.setStroke(DEBUG_STROKE);
        } else {
            rect.setFill(Color.TRANSPARENT);
            rect.setStroke(Color.TRANSPARENT);
        }
    }
}
