package com.layla.core;

import java.util.EnumMap;
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
  private long arrowSeq = 0L;
  private final EnumMap<KeyCode, Long> arrowOrder = new EnumMap<>(KeyCode.class);
  private final javafx.event.EventHandler<KeyEvent> pressedHandler = event -> {
    KeyCode code = event.getCode();
    pressed.add(code);
    if (isArrow(code)) {
      arrowOrder.put(code, ++arrowSeq);
    }
  };
  private final javafx.event.EventHandler<KeyEvent> releasedHandler = event -> {
    KeyCode code = event.getCode();
    pressed.remove(code);
    if (isArrow(code)) {
      arrowOrder.remove(code);
    }
  };
  private Scene attachedScene;

  public void attach(Scene scene) {
    Objects.requireNonNull(scene, "scene");
    if (attachedScene != null) {
      attachedScene.removeEventHandler(KeyEvent.KEY_PRESSED, pressedHandler);
      attachedScene.removeEventHandler(KeyEvent.KEY_RELEASED, releasedHandler);
    }
    pressed.clear();
    arrowOrder.clear();
    arrowSeq = 0L;
    scene.addEventHandler(KeyEvent.KEY_PRESSED, pressedHandler);
    scene.addEventHandler(KeyEvent.KEY_RELEASED, releasedHandler);
    attachedScene = scene;
  }

  public double[] getMoveVector() {
    double dx = 0.0, dy = 0.0;

    if (isPressed(KeyCode.A)) dx -= 1.0;
    if (isPressed(KeyCode.D)) dx += 1.0;
    if (isPressed(KeyCode.W)) dy -= 1.0;
    if (isPressed(KeyCode.S)) dy += 1.0;

    double mag = Math.hypot(dx, dy);
    if (mag > 1.0) { dx /= mag; dy /= mag; }

    return new double[] { dx, dy };
  }


  public double[] getAimArrowCardinal() {
    boolean up = isPressed(KeyCode.UP);
    boolean down = isPressed(KeyCode.DOWN);
    boolean left = isPressed(KeyCode.LEFT);
    boolean right = isPressed(KeyCode.RIGHT);

    if (!(up || down || left || right)) {
      return new double[] {0.0, 0.0};
    }

    KeyCode chosen = null;
    long best = Long.MIN_VALUE;

    if (up) {
      long order = arrowOrder.getOrDefault(KeyCode.UP, Long.MIN_VALUE);
      if (order > best) {
        best = order;
        chosen = KeyCode.UP;
      }
    }
    if (down) {
      long order = arrowOrder.getOrDefault(KeyCode.DOWN, Long.MIN_VALUE);
      if (order > best) {
        best = order;
        chosen = KeyCode.DOWN;
      }
    }
    if (left) {
      long order = arrowOrder.getOrDefault(KeyCode.LEFT, Long.MIN_VALUE);
      if (order > best) {
        best = order;
        chosen = KeyCode.LEFT;
      }
    }
    if (right) {
      long order = arrowOrder.getOrDefault(KeyCode.RIGHT, Long.MIN_VALUE);
      if (order > best) {
        best = order;
        chosen = KeyCode.RIGHT;
      }
    }

    double ax = 0.0;
    double ay = 0.0;

    if (chosen == KeyCode.UP) {
      ay = -1.0;
    } else if (chosen == KeyCode.DOWN) {
      ay = 1.0;
    } else if (chosen == KeyCode.LEFT) {
      ax = -1.0;
    } else if (chosen == KeyCode.RIGHT) {
      ax = 1.0;
    }

    return new double[] {ax, ay};
  }

  public boolean isPressed(KeyCode code) {
    return pressed.contains(code);
  }

  private static boolean isArrow(KeyCode code) {
    return code == KeyCode.UP || code == KeyCode.DOWN || code == KeyCode.LEFT || code == KeyCode.RIGHT;
  }
}
