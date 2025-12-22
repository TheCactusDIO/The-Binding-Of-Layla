package com.layla.entities;

import java.util.Objects;
import java.util.function.Consumer;

import com.layla.core.GameEntity;
import com.layla.services.StatsService;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Entidad de moneda recogible.
 *
 * <p>Comportamiento:</p>
 * <ul>
 *   <li>Flota suavemente alrededor de su {@code baseY} (feedback visual).</li>
 *   <li>Si el jugador intersecta sus bounds, dispara {@code onPickup} exactamente una vez.</li>
 *   <li>Tras recogerse, elimina su nodo del {@code parent} (la eliminación del GameLoop suele gestionarse fuera).</li>
 * </ul>
 *
 * <p>Nota de diseño: la moneda no modifica stats ni balance por sí misma; delega en {@code onPickup}
 * para mantener esta entidad simple (render/colisión) y que la lógica viva en servicios/controladores.</p>
 */
public class Coin implements GameEntity {

    // =========================
    // Constantes visuales / animación
    // =========================
    private static final double RADIUS = 6.0;
    private static final double STROKE_WIDTH = 1.5;

    private static final double FLOAT_SPEED = 5.0;
    private static final double FLOAT_AMPLITUDE = 3.0;

    // =========================
    // Dependencias / callbacks
    // =========================
    private final Pane parent;
    private final Player player;            // puede ser null
    private final Consumer<Coin> onPickup;

    // =========================
    // Estado / view
    // =========================
    private final Circle view;
    private final int value;
    private final double baseY;

    private double floatTimer;
    private boolean pickedUp;

    /**
     * Constructor “limpio” (sin dependencias legacy).
     *
     * @param x posición X inicial (layout)
     * @param y posición Y inicial (layout). También se usa como base de flotación.
     * @param value valor de la moneda
     * @param parent contenedor JavaFX donde se renderiza
     * @param player jugador para detectar recogida (puede ser {@code null})
     * @param onPickup callback al recoger (no puede ser {@code null})
     */
    public Coin(double x, double y, int value, Pane parent, Player player, Consumer<Coin> onPickup) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.player = player;
        this.onPickup = Objects.requireNonNull(onPickup, "onPickup");

        this.value = value;
        this.baseY = y;

        this.view = new Circle(RADIUS, Color.GOLD);
        this.view.setManaged(false);
        this.view.setStroke(Color.ORANGE);
        this.view.setStrokeWidth(STROKE_WIDTH);
        this.view.setLayoutX(x);
        this.view.setLayoutY(y);

        this.parent.getChildren().add(this.view);
    }

    /**
     * Constructor legacy: mantiene compatibilidad si aún se pasa {@link StatsService}.
     *
     * <p>IMPORTANTE: {@code StatsService} no se usa dentro de {@code Coin}.
     * Se mantiene solo para no romper call sites antiguos. Migrar al constructor nuevo cuando puedas.</p>
     *
     * @deprecated usar {@link #Coin(double, double, int, Pane, Player, Consumer)}
     */
    @Deprecated
    public Coin(double x, double y, int value, Pane parent, StatsService statsService, Player player, Consumer<Coin> onPickup) {
        this(x, y, value, parent, player, onPickup);
    }

    /**
     * Tick de la moneda:
     * <ul>
     *   <li>Actualiza flotación.</li>
     *   <li>Chequea recogida (si hay jugador y no está muerto).</li>
     * </ul>
     *
     * @param dt delta time en segundos
     */
    @Override
    public void update(double dt) {
        if (pickedUp || dt <= 0) return;

        floatTimer += dt * FLOAT_SPEED;
        view.setLayoutY(baseY + Math.sin(floatTimer) * FLOAT_AMPLITUDE);

        if (player == null || player.isDead()) return;

        if (view.getBoundsInParent().intersects(player.getBounds())) {
            pickedUp = true;
            onPickup.accept(this);
            parent.getChildren().remove(view);
        }
    }

    /**
     * Devuelve el nodo visual de la moneda.
     */
    @Override
    public Node getView() {
        return view;
    }

    /**
     * @return valor de la moneda para que el callback aplique la recompensa.
     */
    public int getValue() {
        return value;
    }
}
