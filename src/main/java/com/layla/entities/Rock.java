package com.layla.entities;

import com.layla.core.GameEntity;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class Rock implements GameEntity {

    private final Rectangle view;
    private final Pane parent;

    // Tamaño estándar de la roca
    public static final double SIZE = 40.0;

    public Rock(double x, double y, Pane parent) {
        this.parent = parent;

        this.view = new Rectangle(SIZE, SIZE);
        this.view.setFill(Color.DARKGREY);
        this.view.setStroke(Color.BLACK);
        this.view.setStrokeWidth(2.0);

        // Sombra/Efecto visual simple
        this.view.setEffect(new javafx.scene.effect.DropShadow(5, Color.BLACK));

        this.view.setLayoutX(x);
        this.view.setLayoutY(y);

        parent.getChildren().add(view);
    }

    @Override
    public void update(double dt) {
        // Las rocas no hacen nada, son estáticas
    }

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public Bounds getBounds() {
        return view.getBoundsInParent();
    }

    // Método para eliminar la roca (por si añadimos bombas luego)
    public void destroy() {
        parent.getChildren().remove(view);
    }
}
