package com.layla.ui;

import java.util.function.Consumer;

import com.layla.core.GameEntity;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * Texto flotante temporal (por ejemplo: +monedas, daño, notificaciones cortas).
 *
 * Es una entidad "pasiva" en el GameLoop:
 * - No tiene lógica en update(dt), porque la animación se hace con JavaFX Transitions.
 * - Al terminar la animación, llama a onRemove.accept(this) para que quien la creó la elimine del GameLoop.
 */
public class FloatingTextEntity implements GameEntity {

    /** Acción vacía para evitar null checks y lambdas repetidas. */
    private static final Consumer<GameEntity> NO_OP_REMOVE = e -> {};

    /** Duración total de la animación (ms). */
    private static final int ANIM_MS = 800;

    /** Desplazamiento vertical total (px). Valores negativos = sube. */
    private static final double MOVE_Y = -24.0;

    /** Estilo visual del texto. */
    private static final String LABEL_STYLE =
            "-fx-text-fill: #ffd54f; " +
            "-fx-font-size: 14; " +
            "-fx-font-weight: bold; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.85), 6, 0.4, 0, 0);";

    private final Label label = new Label();

    /**
     * Callback que debe eliminar esta entidad del juego (por ejemplo: gameLoop.removeEntity(entity)).
     * Se inyecta desde fuera para que esta clase no dependa de GameLoop directamente.
     */
    private final Consumer<GameEntity> onRemove;

    /**
     * Crea un texto flotante en una posición inicial.
     *
     * @param text     texto a mostrar
     * @param startX   posición X inicial
     * @param startY   posición Y inicial
     * @param onRemove callback llamado al finalizar la animación (puede ser null)
     */
    public FloatingTextEntity(String text, double startX, double startY, Consumer<GameEntity> onRemove) {
        this.onRemove = (onRemove != null) ? onRemove : NO_OP_REMOVE;

        // Configuración del nodo
        label.setManaged(false);          // no participa en layouts automáticos
        label.setMouseTransparent(true);  // no bloquea clicks/hover
        label.setText(text);
        label.setStyle(LABEL_STYLE);
        label.setLayoutX(startX);
        label.setLayoutY(startY);

        // Animación: subir + fade out
        TranslateTransition move = new TranslateTransition(Duration.millis(ANIM_MS), label);
        move.setFromY(0);
        move.setToY(MOVE_Y);

        FadeTransition fade = new FadeTransition(Duration.millis(ANIM_MS), label);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);

        ParallelTransition pt = new ParallelTransition(move, fade);
        pt.setOnFinished(e -> this.onRemove.accept(this));
        pt.play();
    }

    /**
     * No se usa: esta entidad se anima con Transitions, no con lógica por frame.
     */
    @Override
    public void update(double dt) {
        // Intencionalmente vacío.
    }

    @Override
    public Node getView() {
        return label;
    }

    /**
     * No participa en colisiones.
     */
    @Override
    public void onCollision(GameEntity other) {
        // Intencionalmente vacío.
    }
}
