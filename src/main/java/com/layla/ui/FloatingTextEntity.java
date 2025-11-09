package com.layla.ui;

import com.layla.core.GameEntity;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.util.function.Consumer;

public class FloatingTextEntity implements GameEntity {
    private final Label label = new Label();
    private final Consumer<GameEntity> onRemove;

    public FloatingTextEntity(String text, double startX, double startY, Consumer<GameEntity> onRemove) {
        this.onRemove = (onRemove != null) ? onRemove : e -> {};
        label.setManaged(false);
        label.setMouseTransparent(true);
        label.setText(text);
        label.setStyle(
            "-fx-text-fill: #ffd54f; " +
            "-fx-font-size: 14; " +
            "-fx-font-weight: bold; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.85), 6, 0.4, 0, 0);"
        );
        label.setLayoutX(startX);
        label.setLayoutY(startY);

        TranslateTransition move = new TranslateTransition(Duration.millis(800), label);
        move.setFromY(0);
        move.setToY(-24);

        FadeTransition fade = new FadeTransition(Duration.millis(800), label);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);

        ParallelTransition pt = new ParallelTransition(move, fade);
        pt.setOnFinished(e -> this.onRemove.accept(this));
        pt.play();
    }

    @Override
    public void update(double dt) {}

    @Override
    public Node getView() {
        return label;
    }

    @Override
    public void onCollision(GameEntity other) {}
}
