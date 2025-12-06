package com.layla.entities;

import java.util.Objects;
import java.util.function.Consumer;

import com.layla.core.GameEntity;
import com.layla.model.PlayerStatId;
import com.layla.services.StatsService;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeType;

/**
 * Drop de moneda que cae al suelo y puede ser recogida por el jugador.
 * Incluye lógica de "magnetismo" basada en la estadística PICKUP_RANGE.
 */
public final class Coin implements GameEntity {

    private static final double RADIUS = 6.0;
    private static final double BOB_SPEED = 4.0;
    private static final double BOB_AMPLITUDE = 2.0;
    private static final double MAGNET_SPEED_BASE = 300.0;
    private static final double MAGNET_ACCEL = 800.0;

    private final int value;
    private final Circle view; // Usamos forma básica para no depender de assets externos
    private final Player player; // Referencia para el magnetismo
    private final StatsService stats;
    private final Consumer<Coin> onCollect;

    private double timeAlive = 0.0;
    private double currentMagnetSpeed = 0.0;
    private boolean isMagnetized = false;
    private boolean collected = false;

    public Coin(double x, double y, int value,
                Pane parent,
                StatsService stats,
                Player player,
                Consumer<Coin> onCollect) {

        this.value = value;
        this.stats = Objects.requireNonNull(stats);
        this.player = player; // Puede ser null si el player muere, gestionarlo en update
        this.onCollect = Objects.requireNonNull(onCollect);

        // Aspecto visual (Círculo dorado con borde)
        this.view = new Circle(RADIUS, Color.GOLD);
        this.view.setStroke(Color.ORANGE);
        this.view.setStrokeWidth(1.5);
        this.view.setStrokeType(StrokeType.INSIDE);

        // Posición inicial
        this.view.setLayoutX(x);
        this.view.setLayoutY(y);

        // Sombra ligera (opcional, simulada con efecto o simplemente el círculo)
        this.view.setEffect(new javafx.scene.effect.DropShadow(4.0, Color.color(0,0,0,0.4)));

        parent.getChildren().add(this.view);
    }

    public int getValue() {
        return value;
    }

    @Override
    public void update(double dt) {
        if (collected || player == null || player.isDead()) return;

        timeAlive += dt;

        // 1. Efecto visual de flotación ("Bobbing")
        // Solo lo aplicamos si no se está moviendo hacia el jugador para que no tiemble
        double bobOffset = 0;
        if (!isMagnetized) {
            bobOffset = Math.sin(timeAlive * BOB_SPEED) * BOB_AMPLITUDE;
            view.setTranslateY(bobOffset);
        } else {
            view.setTranslateY(0);
        }

        // 2. Lógica de Magnetismo
        double px = player.getView().getLayoutX() + player.getWidth() / 2.0;
        double py = player.getView().getLayoutY() + player.getHeight() / 2.0;
        double cx = view.getLayoutX();
        double cy = view.getLayoutY();

        double dx = px - cx;
        double dy = py - cy;
        double distSq = dx*dx + dy*dy;

        // Rango de recogida desde StatsService (convertimos stat a double)
        double pickupRange = stats.getStat(PlayerStatId.PICKUP_RANGE);
        double pickupSq = pickupRange * pickupRange;

        // Si entra en rango, se activa el imán y no para hasta recogerlo
        if (distSq < pickupSq) {
            isMagnetized = true;
        }

        if (isMagnetized) {
            double dist = Math.sqrt(distSq);
            if (dist < 1.0) dist = 1.0; // Evitar división por cero

            // Normalizar dirección
            double dirX = dx / dist;
            double dirY = dy / dist;

            // Acelerar hacia el jugador
            currentMagnetSpeed += MAGNET_ACCEL * dt;
            double moveDist = Math.max(MAGNET_SPEED_BASE, currentMagnetSpeed) * dt;

            // Mover
            view.setLayoutX(cx + dirX * moveDist);
            view.setLayoutY(cy + dirY * moveDist);
        }
    }

    @Override
    public void onCollision(GameEntity other) {
        if (collected) return;

        // Si choca con el jugador, se recoge
        if (other instanceof Player) {
            collect();
        }
    }

    private void collect() {
        if (collected) return;
        collected = true;
        onCollect.accept(this);
    }

    @Override
    public Node getView() {
        return view;
    }
}
