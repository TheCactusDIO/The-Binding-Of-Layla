package com.layla.entities;

import java.io.InputStream;
import java.util.Objects;
import java.util.function.Consumer;

import com.layla.core.GameEntity;
import com.layla.items.ItemId;
import com.layla.items.ItemRegistry;
import com.layla.services.StatsService;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;

/**
 * Pedestal de ítem estilo Isaac:
 * - Base de piedra con sombra.
 * - Ítem flotando con efecto de "bobbing".
 * - Al colisionar con Player → concede el ítem vía StatsService y se elimina.
 */
public final class ItemPedestal implements GameEntity {

    private static final double WIDTH  = 40.0;
    private static final double HEIGHT = 40.0;

    // Frecuencia y amplitud del "bobbing" del icono
    private static final double BOB_FREQ  = 2.5;  // ciclos por segundo aprox
    private static final double BOB_AMPL  = 4.0;  // píxeles

    private final ItemId itemId;
    private final StatsService statsService;
    private final Consumer<GameEntity> onRemove;
    private final Consumer<String> playSfx;

    private final StackPane root = new StackPane();
    private final Rectangle base;
    private final Ellipse shadow;
    private final ImageView icon;

    private boolean taken = false;
    private double bobTime = 0.0;

    public ItemPedestal(ItemId itemId,
                        Pane parent,
                        StatsService statsService,
                        Consumer<GameEntity> onRemove,
                        Consumer<String> playSfx) {
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.statsService = Objects.requireNonNull(statsService, "statsService");
        this.onRemove = (onRemove != null ? onRemove : e -> {});
        this.playSfx = (playSfx != null ? playSfx : k -> {});

        Objects.requireNonNull(parent, "parent");

        // Sombra en el suelo
        shadow = new Ellipse(WIDTH * 0.45, HEIGHT * 0.18);
        shadow.setFill(Color.color(0, 0, 0, 0.45));
        shadow.setTranslateY(HEIGHT * 0.20);

        // Base de piedra del pedestal
        base = new Rectangle(WIDTH * 0.8, HEIGHT * 0.55);
        base.setArcWidth(10);
        base.setArcHeight(10);
        base.setFill(Color.DARKSLATEGRAY);
        base.setStroke(Color.BLACK);
        base.setStrokeWidth(1.5);
        base.setTranslateY(HEIGHT * 0.05);

        // Icono del ítem flotando encima del pedestal
        icon = new ImageView();
        icon.setFitWidth(WIDTH * 0.7);
        icon.setFitHeight(HEIGHT * 0.7);
        icon.setPreserveRatio(true);
        icon.setTranslateY(-HEIGHT * 0.35);

        // Glow ligero en el icono para que resalte
        DropShadow glow = new DropShadow();
        glow.setRadius(16);
        glow.setOffsetY(0);
        glow.setColor(Color.color(1.0, 1.0, 0.7, 0.8));
        icon.setEffect(glow);

        loadIcon();

        root.setManaged(false);
        root.getChildren().addAll(shadow, base, icon);

        parent.getChildren().add(root);
    }

    private void loadIcon() {
        String path = ItemRegistry.getIconPath(itemId);
        if (path == null) {
            System.err.println("[ItemPedestal] No icon path for " + itemId);
            return;
        }

        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                System.err.println("[ItemPedestal] Resource not found for " + itemId + " at " + path);
                return;
            }
            Image img = new Image(in);
            icon.setImage(img);
        } catch (Exception ex) {
            System.err.println("[ItemPedestal] Error loading icon for " + itemId + ": " + ex.getMessage());
        }
    }

    public void setPosition(double x, double y) {
        root.setLayoutX(x);
        root.setLayoutY(y);
    }

    public double getWidth() {
        return WIDTH;
    }

    public double getHeight() {
        return HEIGHT;
    }

    public ItemId getItemId() {
        return itemId;
    }

    @Override
    public void update(double dt) {
        // Animación de "bobbing" del icono, estilo Isaac
        if (dt <= 0) return;

        bobTime += dt;
        double offset = Math.sin(bobTime * BOB_FREQ * Math.PI * 2.0) * BOB_AMPL;
        icon.setTranslateY(-HEIGHT * 0.35 + offset);
    }

    @Override
    public Node getView() {
        return root;
    }

    @Override
    public Bounds getBounds() {
        return root.getBoundsInParent();
    }

    @Override
    public void onCollision(GameEntity other) {
        if (taken) return;

        if (other instanceof Player) {
            boolean granted = statsService.grantItem(itemId);
            if (!granted) {
                return;
            }

            taken = true;
            // Sonido de pickup de ítem (usa SoundService con key "item")
            playSfx.accept("item");
            onRemove.accept(this);
        }
    }
}
