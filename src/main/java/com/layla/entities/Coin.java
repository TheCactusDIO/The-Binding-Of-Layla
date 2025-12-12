package com.layla.entities;

import java.util.Objects;
import java.util.function.Consumer;

import com.layla.core.GameEntity;
import com.layla.services.StatsService;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

public class Coin implements GameEntity {

    private final Pane parent;
    private final StatsService statsService;
    private final Player player;
    private final Consumer<Coin> onPickup;

    private final Circle view;
    private final int value;

    // Animación simple de rebote o flotación
    private double floatTimer = 0.0;
    private double baseY;

    public Coin(double x, double y, int value, Pane parent,
                StatsService statsService, Player player, Consumer<Coin> onPickup) {
        this.parent = Objects.requireNonNull(parent);
        this.statsService = statsService;
        this.player = player;
        this.onPickup = Objects.requireNonNull(onPickup);
        this.value = value;
        this.baseY = y;

        view = new Circle(6, Color.GOLD);
        view.setStroke(Color.ORANGE);
        view.setStrokeWidth(1.5);
        view.setLayoutX(x);
        view.setLayoutY(y);

        // Efecto visual simple: Texto "$" o similar si quieres
        // Por ahora solo círculo dorado

        parent.getChildren().add(view);
    }

    @Override
    public void update(double dt) {
        // Animación de flotar
        floatTimer += dt * 5.0;
        view.setLayoutY(baseY + Math.sin(floatTimer) * 3.0);

        // Detección de recogida
        if (player != null && !player.isDead()) {
            if (view.getBoundsInParent().intersects(player.getBounds())) {
                onPickup.accept(this);
                // Efecto visual de recogida podría ir aquí (destruir entidad lo hace el callback)
                parent.getChildren().remove(view);
            }
        }
    }

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public Bounds getBounds() {
        return view.getBoundsInParent();
    }

    public int getValue() {
        return value;
    }
}
