package com.layla.entities;

import java.util.function.Consumer;

import com.layla.core.GameEntity;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

public class GreedButton implements GameEntity {

    private final StackPane view;
    private final Rectangle base;
    private final Rectangle button;
    private final Text icon;
    private final Consumer<GreedButton> onPressed;

    private boolean isPressed = false;
    private double cooldown = 0.0;

    // Si es true, el botón está en estado "Activo/Hundido" (Oleada en curso)
    // Si es false, está en estado "Listo/Levantado" (Esperando input)
    private boolean isActiveState = false;

    public GreedButton(double x, double y, Pane parent, Consumer<GreedButton> onPressed) {
        this.onPressed = onPressed;

        view = new StackPane();
        view.setPrefSize(44, 44);
        view.setLayoutX(x - 22);
        view.setLayoutY(y - 22);

        base = new Rectangle(44, 44, Color.rgb(60, 60, 60));
        base.setArcWidth(10); base.setArcHeight(10);
        base.setStroke(Color.BLACK); base.setStrokeWidth(2);

        button = new Rectangle(30, 30, Color.rgb(180, 40, 40)); // Rojo apagado inicial
        button.setArcWidth(6); button.setArcHeight(6);
        button.setStroke(Color.BLACK); button.setStrokeType(StrokeType.INSIDE); button.setStrokeWidth(2);

        icon = new Text("G"); // G de Greed
        icon.setFont(Font.font("Verdana", javafx.scene.text.FontWeight.BOLD, 18));
        icon.setFill(Color.web("#500000"));

        view.getChildren().addAll(base, button, icon);
        view.setEffect(new javafx.scene.effect.DropShadow(10, Color.BLACK));

        parent.getChildren().add(view);
    }

    @Override
    public void update(double dt) {
        if (cooldown > 0) cooldown -= dt;

        // Actualizar visuales según estado
        if (isActiveState) {
            // Estado "Hundido/Peligro": Oleada activa
            button.setFill(Color.RED);
            button.setWidth(26); // Más pequeño visualmente (hundido)
            button.setHeight(26);
            base.setStroke(Color.DARKRED); // Brillo rojo de peligro (spikes)
        } else {
            // Estado "Listo": Esperando pulsación
            button.setFill(Color.rgb(180, 40, 40));
            button.setWidth(30);
            button.setHeight(30);
            base.setStroke(Color.BLACK);
        }
    }

    @Override
    public Node getView() { return view; }

    @Override
    public Bounds getBounds() { return view.getBoundsInParent(); }

    @Override
    public void onCollision(GameEntity other) {
        if (other instanceof Player) {
            if (cooldown <= 0) {
                // Notificar al controlador siempre que se pisa, él decide qué hacer
                onPressed.accept(this);
                cooldown = 1.0; // Evitar rebote inmediato
            }
        }
    }

    // Cambia el estado visual (True = Ronda activa/Peligro, False = Parado/Seguro)
    public void setActiveState(boolean active) {
        this.isActiveState = active;
    }

    // Para cambiar el icono si es el botón de siguiente piso
    public void setAsExit() {
        icon.setText("▼");
        button.setFill(Color.BLUE);
    }
}
