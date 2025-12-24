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
 * <p>
 * Características:
 * <ul>
 * <li>Flotación visual suave (efecto sine wave en Y).</li>
 * <li>Detección de colisión con el jugador para ser recogida.</li>
 * <li>La lógica de negocio (sumar dinero) se delega al callback {@code onPickup}.</li>
 * </ul>
 * </p>
 */
public class Coin implements GameEntity {

    // =========================
    // Constantes visuales
    // =========================
    private static final double RADIUS = 6.0;
    private static final double STROKE_WIDTH = 1.5;

    private static final double FLOAT_SPEED = 5.0;
    private static final double FLOAT_AMPLITUDE = 3.0;

    // =========================
    // Dependencias
    // =========================
    private final Pane parent;
    private final Player player;            // Referencia para colisiones
    private final Consumer<Coin> onPickup;

    // =========================
    // Estado
    // =========================
    private final Circle view;
    private final int value;
    private final double baseY;

    private double floatTimer;
    private boolean pickedUp;

    /**
     * Constructor principal.
     *
     * @param x         Posición X inicial.
     * @param y         Posición Y inicial (base de flotación).
     * @param value     Valor monetario de la moneda.
     * @param parent    Panel contenedor.
     * @param player    Instancia del jugador (puede ser null, pero entonces no se recoge).
     * @param onPickup  Callback a ejecutar al recogerse.
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
     * Constructor legacy para compatibilidad.
     * <p>
     * <strong>Deprecated:</strong> {@code StatsService} ya no se usa internamente, se debe usar el constructor sin él.
     * </p>
     *
     * @deprecated Usar {@link #Coin(double, double, int, Pane, Player, Consumer)}
     */
    @Deprecated
    public Coin(double x, double y, int value, Pane parent, StatsService statsService, Player player, Consumer<Coin> onPickup) {
        this(x, y, value, parent, player, onPickup);
    }

    /**
     * Actualiza la animación y comprueba colisiones.
     *
     * @param dt Delta time.
     */
    @Override
    public void update(double dt) {
        if (pickedUp || dt <= 0) return;

        // Animación de flotación
        floatTimer += dt * FLOAT_SPEED;
        view.setLayoutY(baseY + Math.sin(floatTimer) * FLOAT_AMPLITUDE);

        if (player == null || player.isDead()) return;

        // Detección de recogida
        if (view.getBoundsInParent().intersects(player.getBounds())) {
            pickedUp = true;
            onPickup.accept(this);
            parent.getChildren().remove(view);
            // Nota: La eliminación de la lista de entidades del GameLoop debe gestionarse externamente (ej. onRemove callback)
        }
    }

    @Override
    public Node getView() {
        return view;
    }

    /**
     * Obtiene el valor de la moneda.
     * @return Cantidad de dinero que otorga.
     */
    public int getValue() {
        return value;
    }
}
