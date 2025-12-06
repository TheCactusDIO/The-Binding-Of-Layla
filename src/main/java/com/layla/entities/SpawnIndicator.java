package com.layla.entities;

import java.util.Objects;
import java.util.function.Consumer;

import com.layla.core.GameEntity;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeType;

public class SpawnIndicator implements GameEntity {

    private final Pane parent;
    private final Circle view;
    private final double duration;
    private final Consumer<SpawnIndicator> onFinish;

    private double timer = 0.0;
    private boolean finished = false;

    public SpawnIndicator(double x, double y, double duration, Pane parent, Consumer<SpawnIndicator> onFinish) {
        this.parent = Objects.requireNonNull(parent);
        this.duration = duration;
        this.onFinish = Objects.requireNonNull(onFinish);

        this.view = new Circle(12.0, Color.rgb(255, 0, 0, 0.2));
        this.view.setStroke(Color.rgb(255, 0, 0, 0.6));
        this.view.setStrokeWidth(2.0);
        this.view.setStrokeType(StrokeType.INSIDE);
        this.view.getStrokeDashArray().addAll(5d, 5d);

        this.view.setLayoutX(x);
        this.view.setLayoutY(y);

        parent.getChildren().add(this.view);
        this.view.toBack();
    }

    @Override
    public void update(double dt) {
        if (finished) return;

        timer += dt;

        double progress = timer / duration;
        double frequency = 5.0 + (15.0 * progress);
        double alpha = 0.2 + 0.4 * Math.abs(Math.sin(timer * frequency));

        view.setFill(Color.rgb(255, 0, 0, alpha));
        view.setStroke(Color.rgb(255, 0, 0, alpha + 0.3));

        if (timer >= duration) {
            finish();
        }
    }

    private void finish() {
        finished = true;
        parent.getChildren().remove(view);
        onFinish.accept(this);
    }

    public double getX() { return view.getLayoutX(); }
    public double getY() { return view.getLayoutY(); }

    @Override public Node getView() { return view; }
    @Override public void onCollision(GameEntity other) {}
}
