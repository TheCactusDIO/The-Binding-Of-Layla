package com.layla.core;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Objects;

import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/**
 * Servicio de entrada de teclado para el juego.
 *
 * Qué hace:
 * - Mantiene un set de teclas presionadas (WASD + flechas).
 * - Devuelve vector de movimiento normalizado con WASD.
 * - Devuelve dirección cardinal de disparo con flechas, priorizando la ÚLTIMA flecha pulsada.
 * - Limpia estado al perder el foco de la ventana o al ocultarse (evita "teclas pegadas").
 *
 * Nota:
 * Este servicio no mueve nada directamente; solo expone estado consultable desde el GameLoop.
 */
public final class InputService {

    // =========================
    // ESTADO DE TECLAS
    // =========================

    /** Conjunto de teclas actualmente presionadas. */
    private final EnumSet<KeyCode> pressed = EnumSet.noneOf(KeyCode.class);

    /**
     * Secuencia incremental para registrar "la última flecha pulsada".
     * Cuanto más alto, más reciente.
     */
    private long arrowSeq = 0L;

    /**
     * Orden de pulsación por flecha.
     * Ej: UP -> 15, RIGHT -> 16  (RIGHT sería la más reciente).
     */
    private final EnumMap<KeyCode, Long> arrowOrder = new EnumMap<>(KeyCode.class);

    // =========================
    // HANDLERS DE TECLADO
    // =========================

    /** Handler de KeyPressed: marca tecla como presionada y registra orden si es flecha. */
    private final EventHandler<KeyEvent> pressedHandler = event -> {
        KeyCode code = event.getCode();
        pressed.add(code);
        if (isArrow(code)) {
            arrowOrder.put(code, ++arrowSeq);
        }
    };

    /** Handler de KeyReleased: desmarca tecla y borra su orden si es flecha. */
    private final EventHandler<KeyEvent> releasedHandler = event -> {
        KeyCode code = event.getCode();
        pressed.remove(code);
        if (isArrow(code)) {
            arrowOrder.remove(code);
        }
    };

    // =========================
    // ENGANCHE A ESCENA/VENTANA
    // =========================

    /** Escena actualmente asociada (si null, el servicio está desconectado). */
    private Scene attachedScene;

    /** Ventana actualmente asociada (para limpiar listeners correctamente). */
    private Window attachedWindow;

    /** Listener de foco de ventana (limpia estado al perder foco). */
    private javafx.beans.value.ChangeListener<Boolean> focusListener;

    /** Handler de ventana oculta (limpia estado al ocultar/cerrar). */
    private EventHandler<WindowEvent> windowHiddenHandler;

    /** Listener de windowProperty del Scene (para enganchar cuando la Window existe). */
    private javafx.beans.value.ChangeListener<Window> windowListener;

    // =========================
    // API PÚBLICA
    // =========================

    /**
     * Adjunta el servicio a una escena.
     *
     * Qué hace:
     * - Si ya estaba adjunto a otra escena, la desconecta primero.
     * - Registra handlers de teclado (KEY_PRESSED/KEY_RELEASED).
     * - Registra listeners de ventana para limpiar estado cuando:
     *   - la ventana pierde foco
     *   - la ventana se oculta / cierra
     *
     * @param scene escena a la que se engancha el input
     */
    public void attach(Scene scene) {
        Objects.requireNonNull(scene, "scene");

        // Si ya estaba enganchado, limpiamos todo para no duplicar listeners.
        detach();

        resetState();

        scene.addEventHandler(KeyEvent.KEY_PRESSED, pressedHandler);
        scene.addEventHandler(KeyEvent.KEY_RELEASED, releasedHandler);

        // Guardamos el listener para poder quitarlo en detach()
        windowListener = (obs, oldWindow, newWindow) -> {
            // Si cambiamos de window, desenganchamos de la anterior.
            detachFromWindow(oldWindow);
            attachToWindow(newWindow);
        };

        scene.windowProperty().addListener(windowListener);

        // Si la escena ya tiene window, enganchamos ya.
        attachToWindow(scene.getWindow());

        attachedScene = scene;
    }

    /**
     * Desconecta el servicio de la escena actual.
     *
     * Qué hace:
     * - Quita handlers de teclado.
     * - Quita listeners asociados a la ventana (focus/hidden).
     * - Limpia el estado interno de teclas.
     *
     * Es seguro llamarlo aunque no esté adjunto (idempotente).
     */
    public void detach() {
        if (attachedScene != null) {
            attachedScene.removeEventHandler(KeyEvent.KEY_PRESSED, pressedHandler);
            attachedScene.removeEventHandler(KeyEvent.KEY_RELEASED, releasedHandler);

            if (windowListener != null) {
                attachedScene.windowProperty().removeListener(windowListener);
                windowListener = null;
            }

            detachFromWindow(attachedScene.getWindow());

            attachedScene = null;
        }

        resetState();
    }

    /**
     * Devuelve el vector de movimiento basado en WASD.
     *
     * Qué hace:
     * - Construye un vector (dx, dy) en base a teclas presionadas.
     * - Normaliza si la magnitud es > 1 (para que diagonal no sea más rápido).
     *
     * @return array {x, y} en rango [-1..1]
     */
    public double[] getMoveVector() {
        double dx = 0.0, dy = 0.0;

        if (isPressed(KeyCode.A)) dx -= 1.0;
        if (isPressed(KeyCode.D)) dx += 1.0;
        if (isPressed(KeyCode.W)) dy -= 1.0;
        if (isPressed(KeyCode.S)) dy += 1.0;

        double mag = Math.hypot(dx, dy);
        if (mag > 1.0) {
            dx /= mag;
            dy /= mag;
        }

        return new double[]{ dx, dy };
    }

    /**
     * Devuelve la dirección de apuntado/disparo en cardinal (arriba/abajo/izq/der).
     *
     * Qué hace:
     * - Si no hay flechas presionadas, devuelve {0,0}.
     * - Si hay varias flechas presionadas, elige la MÁS RECIENTE (por orden de pulsación).
     *
     * @return array {x, y} con valores en {-1,0,1}
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

    /**
     * Comprueba si una tecla está actualmente presionada.
     *
     * @param code tecla a consultar
     * @return true si está presionada
     */
    public boolean isPressed(KeyCode code) {
        return pressed.contains(code);
    }

    // =========================
    // HELPERS INTERNOS
    // =========================

    /**
     * Resetea el estado interno de teclas y orden de flechas.
     * Esto evita estados "pegados" al cambiar de escena/ventana.
     */
    private void resetState() {
        pressed.clear();
        arrowOrder.clear();
        arrowSeq = 0L;
    }

    /**
     * Engancha listeners a una ventana concreta.
     * Se usa para limpiar input automáticamente cuando el usuario cambia de ventana o la app pierde foco.
     */
    private void attachToWindow(Window win) {
        if (win == null) return;

        attachedWindow = win;

        // Limpieza al perder foco
        focusListener = (o, oldFocus, nowFocus) -> {
            if (!Boolean.TRUE.equals(nowFocus)) {
                resetState();
            }
        };
        win.focusedProperty().addListener(focusListener);

        // Limpieza al ocultarse/cerrarse
        windowHiddenHandler = e -> resetState();
        win.addEventHandler(WindowEvent.WINDOW_HIDDEN, windowHiddenHandler);
    }

    /**
     * Desengancha listeners de una ventana (si estaban registrados).
     * Importante para no “filtrar” listeners entre escenas o ventanas.
     */
    private void detachFromWindow(Window win) {
        if (win == null) return;

        if (focusListener != null) {
            win.focusedProperty().removeListener(focusListener);
            focusListener = null;
        }
        if (windowHiddenHandler != null) {
            win.removeEventHandler(WindowEvent.WINDOW_HIDDEN, windowHiddenHandler);
            windowHiddenHandler = null;
        }

        if (attachedWindow == win) {
            attachedWindow = null;
        }
    }

    /**
     * Comprueba si una tecla es una flecha.
     * Se usa para aplicar la prioridad por "última pulsada".
     */
    private static boolean isArrow(KeyCode code) {
        return code == KeyCode.UP || code == KeyCode.DOWN
            || code == KeyCode.LEFT || code == KeyCode.RIGHT;
    }
}
