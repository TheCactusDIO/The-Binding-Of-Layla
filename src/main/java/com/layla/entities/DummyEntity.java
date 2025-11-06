package com.layla.entities;

import com.layla.core.GameEntity;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public final class DummyEntity implements GameEntity {
  private final Rectangle view;
  private final Pane boundsPane;
  private double vx; // px/s (positivo a la derecha)

  public DummyEntity(double startX, double startY, double initialSpeed, Pane boundsPane) {
    this.view = new Rectangle(20, 20);
    this.view.setFill(Color.DARKRED);
    this.view.setStroke(Color.BLACK);
    this.view.setTranslateX(startX);
    this.view.setTranslateY(startY);
    this.vx = initialSpeed;
    this.boundsPane = boundsPane;
  }

  @Override
  public void update(double dt) {
    // Mover
    view.setTranslateX(view.getTranslateX() + vx * dt);

    // Calcular límites en caliente (pane puede cambiar de tamaño)
    double paneW = Math.max(0, boundsPane.getWidth());
    double nodeW = view.getWidth(); // porque usamos translate como posición “top-left”
    double minX = 0;
    double maxX = Math.max(0, paneW - nodeW);

    // Si el pane se redujo y el dummy quedó fuera, recolocarlo
    if (view.getTranslateX() > maxX) {
      view.setTranslateX(maxX);
    } else if (view.getTranslateX() < minX) {
      view.setTranslateX(minX);
    }

    // Rebotar en los bordes visibles
    if (view.getTranslateX() <= minX && vx < 0) {
      vx = Math.abs(vx);
    } else if (view.getTranslateX() >= maxX && vx > 0) {
      vx = -Math.abs(vx);
    }
  }

  @Override public Node getView() { return view; }
  @Override public void onCollision(com.layla.core.GameEntity other) { /* no-op por ahora */ }
}
