package com.layla.entities;

import java.util.function.Consumer;

import com.layla.core.GameEntity;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

public final class Projectile implements GameEntity {
  private final double dx, dy;       // dirección normalizada
  private final double speed;        // px/s
  private final double lifetime;     // s
  private double elapsed = 0.0;
  private final Pane boundsPane;
  private final Consumer<GameEntity> onRemove;
  private final Circle view;         // círculo como bala

  public Projectile(double dx, double dy, double speed, double lifetimeSeconds,
                    Pane boundsPane, Consumer<GameEntity> onRemove) {
    double len = Math.hypot(dx, dy);
    if (len == 0) { dx = 0; dy = -1; len = 1; }
    this.dx = dx / len;
    this.dy = dy / len;
    this.speed = speed;
    this.lifetime = lifetimeSeconds;
    this.boundsPane = boundsPane;
    this.onRemove = onRemove;

    // Círculo 4px radio (≈ 8x8)
    this.view = new Circle(4);
    this.view.setFill(Color.WHITE);
    this.view.setStroke(Color.BLACK);
  }

  @Override public void update(double dt) {
    elapsed += dt;
    view.setLayoutX(view.getLayoutX() + dx * speed * dt);
    view.setLayoutY(view.getLayoutY() + dy * speed * dt);

    if (elapsed >= lifetime || isOutOfPaneBounds()) {
      onRemove.accept(this);
    }
  }

  @Override public Node getView() { return view; }

  @Override public void onCollision(GameEntity other) {
    if (!(other instanceof Player)) {
      onRemove.accept(this);
    }
  }

  private boolean isOutOfPaneBounds() {
    double x = view.getTranslateX();
    double y = view.getTranslateY();
    double r = view.getRadius();
    double w = boundsPane.getWidth();
    double h = boundsPane.getHeight();
    return (x + r < 0) || (y + r < 0) || (x - r > w) || (y - r > h);
  }
}
