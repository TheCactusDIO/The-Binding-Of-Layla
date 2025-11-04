// Issue 6 – Bucle del juego (AnimationTimer)
// Archivo: src/main/java/com/layla/entities/DummyEntity.java
// Propósito: Entidad de prueba para validar GameLoop. Se mueve horizontalmente y rebota en los bordes.
// Comentarios extensos para que cualquier chat/miembro entienda el objetivo sin contexto adicional.

package com.layla.entities;

import com.layla.core.GameEntity;

import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Entidad dummy que:
 * - Se representa como un Rectangle.
 * - Se desplaza en el eje X con velocidad constante (px/segundo).
 * - Rebota cuando llega a límites dados (minX, maxX).
 * - Cambia de color cuando colisiona con otra entidad (onCollision).
 *
 * Usada para pruebas visuales del GameLoop.
 */
public class DummyEntity implements GameEntity {

    private final Rectangle view = new Rectangle(30, 30);
    private double vx = 120.0; // velocidad en px/seg
    private final double minX;
    private final double maxX;

    /**
     * @param startX posición X inicial (en TranslateX del Node)
     * @param startY posición Y inicial (en TranslateY del Node)
     * @param minX límite izquierdo (rebote)
     * @param maxX límite derecho (rebote)
     */
    public DummyEntity(double startX, double startY, double minX, double maxX) {
        this.minX = minX;
        this.maxX = maxX;
        view.setTranslateX(startX);
        view.setTranslateY(startY);
        view.setFill(Color.CORNFLOWERBLUE);
        // Borde visible para distinguir la entidad
        view.setStroke(Color.BLACK);
    }

    @Override
    public void update(double dt) {
        // Movimiento simple: x = x + vx * dt
        double nextX = view.getTranslateX() + vx * dt;
        view.setTranslateX(nextX);

        // Rebote: si nos salimos por la izquierda o derecha, invertimos vx
        if (view.getTranslateX() < minX) {
            view.setTranslateX(minX);
            vx = Math.abs(vx);
        } else if (view.getTranslateX() > maxX - view.getWidth()) {
            view.setTranslateX(maxX - view.getWidth());
            vx = -Math.abs(vx);
        }
    }

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public void onCollision(GameEntity other) {
        // Cambiamos color brevemente para visualizar impacto
        view.setFill(Color.ORANGE);
        // Nota: en un juego real, aquí aplicaríamos daño, rebotes más complejos, etc.
    }
}
