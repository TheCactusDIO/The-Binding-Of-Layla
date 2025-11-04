package com.layla.core;

import java.util.EnumSet;
import java.util.Objects;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * Tracks the current keyboard state for movement-related keys.
 */
public final class InputService {

  private final EnumSet<KeyCode> pressed = EnumSet.noneOf(KeyCode.class);
  private final javafx.event.EventHandler<KeyEvent> pressedHandler = event -> pressed.add(event.getCode());
  private final javafx.event.EventHandler<KeyEvent> releasedHandler = event -> pressed.remove(event.getCode());
  private Scene attachedScene;

  public void attach(Scene scene) {
    Objects.requireNonNull(scene, "scene");
    if (attachedScene != null) {
      attachedScene.removeEventHandler(KeyEvent.KEY_PRESSED, pressedHandler);
      attachedScene.removeEventHandler(KeyEvent.KEY_RELEASED, releasedHandler);
    }
    scene.addEventHandler(KeyEvent.KEY_PRESSED, pressedHandler);
    scene.addEventHandler(KeyEvent.KEY_RELEASED, releasedHandler);
    attachedScene = scene;
  }

  public double[] getMoveVector() {
    double dx = 0.0;
    double dy = 0.0;

    if (isPressed(KeyCode.A) || isPressed(KeyCode.LEFT)) {
      dx -= 1.0;
    }
    if (isPressed(KeyCode.D) || isPressed(KeyCode.RIGHT)) {
      dx += 1.0;
    }
    if (isPressed(KeyCode.W) || isPressed(KeyCode.UP)) {
      dy -= 1.0;
    }
    if (isPressed(KeyCode.S) || isPressed(KeyCode.DOWN)) {
      dy += 1.0;
    }

    double magnitude = Math.hypot(dx, dy);
    if (magnitude > 1.0) {
      dx /= magnitude;
      dy /= magnitude;
    }

    return new double[] {dx, dy};
  }

  private boolean isPressed(KeyCode code) {
    return pressed.contains(code);
  }
}
