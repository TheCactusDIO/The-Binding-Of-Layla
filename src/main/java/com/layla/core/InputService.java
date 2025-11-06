package com.layla.core;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Objects;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/**
 * Servicio de entrada de teclado (WASD + flechas).
 * <p>
 * - WASD controlan movimiento, retornado en {@link #getMoveVector()}.
 * - Flechas controlan dirección de disparo, retornada en {@link #getAimArrowCardinal()}.
 * - Mantiene orden de pulsación para las flechas (última pulsada tiene prioridad).
 * - Limpia su estado al perder foco o cerrar ventana.
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

    /**
     * Adjunta el servicio a una escena (registra listeners de teclado).
     * Si ya estaba adjunto a otra escena, la desconecta primero.
     */
    public void attach(Scene scene) {
        Objects.requireNonNull(scene, "scene");

        if (attachedScene != null) {
            detach();
        }

        pressed.clear();
        arrowOrder.clear();
        arrowSeq = 0L;

        scene.addEventHandler(KeyEvent.KEY_PRESSED, pressedHandler);
        scene.addEventHandler(KeyEvent.KEY_RELEASED, releasedHandler);

        // Limpia al perder foco o al ocultar ventana
        scene.windowProperty().addListener((obs, oldWindow, newWindow) -> {
            if (newWindow != null) {
                Window win = (Window) newWindow;
                win.focusedProperty().addListener((o, oldFocus, nowFocus) -> {
                    if (!nowFocus) {
                        pressed.clear();
                        arrowOrder.clear();
                    }
                });
                win.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> {
                    pressed.clear();
                    arrowOrder.clear();
                });
            }
        });

        attachedScene = scene;
    }

    /**
     * Desconecta el servicio de la escena actual (elimina handlers y limpia estado).
     */
    public void detach() {
        if (attachedScene != null) {
            attachedScene.removeEventHandler(KeyEvent.KEY_PRESSED, pressedHandler);
            attachedScene.removeEventHandler(KeyEvent.KEY_RELEASED, releasedHandler);
            attachedScene = null;
        }
        pressed.clear();
        arrowOrder.clear();
        arrowSeq = 0L;
    }

    /**
     * Devuelve el vector de movimiento (WASD), normalizado a longitud 1.
     * @return double[2] con componentes X,Y en el rango [-1,1].
     */
    public double[] getMoveVector() {
        double dx = 0.0, dy = 0.0;

        if (isPressed(KeyCode.A)) dx -= 1.0;
        if (isPressed(KeyCode.D)) dx += 1.0;
        if (isPressed(KeyCode.W)) dy -= 1.0;
        if (isPressed(KeyCode.S)) dy += 1.0;

        double mag = Math.hypot(dx, dy);
        if (mag > 1.0) { dx /= mag; dy /= mag; }

        return new double[]{ dx, dy };
    }

    /**
     * Devuelve la dirección cardinal del disparo (última flecha pulsada).
     * @return double[2] con {-1,0,1} por eje.
     */
    public double[] getAimArrowCardinal() {
        boolean up = isPressed(KeyCode.UP);
        boolean down = isPressed(KeyCode.DOWN);
        boolean left = isPressed(KeyCode.LEFT);
        boolean right = isPressed(KeyCode.RIGHT);

        if (!(up || down || left || right)) {
            return new double[]{0.0, 0.0};
        }

        KeyCode chosen = null;
        long best = Long.MIN_VALUE;

        if (up) {
            long order = arrowOrder.getOrDefault(KeyCode.UP, Long.MIN_VALUE);
            if (order > best) { best = order; chosen = KeyCode.UP; }
        }
        if (down) {
            long order = arrowOrder.getOrDefault(KeyCode.DOWN, Long.MIN_VALUE);
            if (order > best) { best = order; chosen = KeyCode.DOWN; }
        }
        if (left) {
            long order = arrowOrder.getOrDefault(KeyCode.LEFT, Long.MIN_VALUE);
            if (order > best) { best = order; chosen = KeyCode.LEFT; }
        }
        if (right) {
            long order = arrowOrder.getOrDefault(KeyCode.RIGHT, Long.MIN_VALUE);
            if (order > best) { best = order; chosen = KeyCode.RIGHT; }
        }

        double ax = 0.0, ay = 0.0;
        if (chosen == KeyCode.UP) ay = -1.0;
        else if (chosen == KeyCode.DOWN) ay = 1.0;
        else if (chosen == KeyCode.LEFT) ax = -1.0;
        else if (chosen == KeyCode.RIGHT) ax = 1.0;

        return new double[]{ ax, ay };
    }

    /** Comprueba si una tecla está actualmente presionada. */
    public boolean isPressed(KeyCode code) {
        return pressed.contains(code);
    }

    private static boolean isArrow(KeyCode code) {
        return code == KeyCode.UP || code == KeyCode.DOWN
            || code == KeyCode.LEFT || code == KeyCode.RIGHT;
    }
}
