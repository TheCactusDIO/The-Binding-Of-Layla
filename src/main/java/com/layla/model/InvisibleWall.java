package com.layla.model;

import com.layla.core.GameEntity;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public final class InvisibleWall implements GameEntity {

    private final Rectangle rect;

    public InvisibleWall(double x, double y, double w, double h, boolean debug) {
        rect = new Rectangle(w, h);
        rect.setManaged(false);
        rect.setLayoutX(x);
        rect.setLayoutY(y);

        if (debug) {
            rect.setFill(Color.color(1, 0, 0, 0.08));
            rect.setStroke(Color.color(1, 0, 0, 0.35));
        } else {
            rect.setFill(Color.TRANSPARENT);
            rect.setStroke(Color.TRANSPARENT);
        }
        rect.setMouseTransparent(true);
    }

    @Override public void update(double dt) {}

    @Override public Node getView() { return rect; }

    @Override
    public Bounds getBounds() {
        return new BoundingBox(
                rect.getLayoutX(),
                rect.getLayoutY(),
                rect.getWidth(),
                rect.getHeight()
        );
    }

    public void setPosition(double x, double y) {
        rect.setLayoutX(x);
        rect.setLayoutY(y);
    }
}
