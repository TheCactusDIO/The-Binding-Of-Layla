package com.layla.entities;

import java.util.Objects;

import com.layla.core.AssetsManager;
import com.layla.core.GameEntity;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;

/**
 * Roca (obstáculo estático).
 *
 * <p>Renderiza un sprite desde un spritesheet de 1 fila (rocks_sheet.png) con 6 columnas (32x32).
 * El controlador puede elegir la columna (skin) según el piso.</p>
 *
 * <p>La colisión usa siempre {@link #SIZE} para que sea consistente con tu generación de niveles.</p>
 */
public final class Rock implements GameEntity {

    /** Tamaño lógico/visual de la roca para colisiones y separación en generación de niveles. */
    public static final double SIZE = 40.0;

    private static final int SHEET_COLS = 6;
    private static final int FRAME_W = 32;
    private static final int FRAME_H = 32;

    /** Spritesheet cacheado (se carga una sola vez). */
    private static Image SHEET;

    private final Pane parent;
    private final ImageView view = new ImageView();

    private int skinCol; // 0..5

    /**
     * Crea una roca con la skin por defecto (columna 0).
     *
     * @param x centro X donde colocar la roca
     * @param y centro Y donde colocar la roca
     * @param parent contenedor JavaFX donde se añade el nodo
     */
    public Rock(double x, double y, Pane parent) {
        this(x, y, parent, 0);
    }

    /**
     * Crea una roca con una columna concreta del spritesheet.
     *
     * @param x centro X donde colocar la roca
     * @param y centro Y donde colocar la roca
     * @param parent contenedor JavaFX donde se añade el nodo
     * @param skinCol columna del spritesheet (0..5). Si se sale de rango, se ajusta.
     */
    public Rock(double x, double y, Pane parent, int skinCol) {
        this.parent = Objects.requireNonNull(parent, "parent");

        ensureSheetLoaded();

        view.setManaged(false);
        view.setMouseTransparent(true);
        view.setSmooth(false); // pixel-art friendly
        view.setFitWidth(SIZE);
        view.setFitHeight(SIZE);
        view.setPreserveRatio(false);

        if (SHEET != null) {
            view.setImage(SHEET);
        }
        setSkinColumn(skinCol);

        view.setLayoutX(x - SIZE / 2.0);
        view.setLayoutY(y - SIZE / 2.0);

        this.parent.getChildren().add(view);
    }

    private static void ensureSheetLoaded() {
        if (SHEET != null) return;

        // Ruta principal esperada
        SHEET = AssetsManager.loadImage("assets/images/rocks_sheet.png");

        // Fallback
        if (SHEET == null) {
            SHEET = AssetsManager.loadImage("assets/rocks_sheet.png");
        }
    }

    private static int clampColumn(int col) {
        return Math.max(0, Math.min(SHEET_COLS - 1, col));
    }

    /**
     * Selecciona qué columna del spritesheet se muestra (0..5).
     *
     * @param col columna deseada (se ajusta a rango válido)
     */
    public void setSkinColumn(int col) {
        int safe = clampColumn(col);
        this.skinCol = safe;

        // El viewport se puede setear incluso si la imagen aún no existe; no pasa nada.
        view.setViewport(new Rectangle2D(safe * FRAME_W, 0, FRAME_W, FRAME_H));
    }

    /**
     * @return la columna (skin) actual (0..5)
     */
    public int getSkinColumn() {
        return skinCol;
    }

    @Override
    public void update(double dt) {
        // Obstáculo estático: sin lógica por frame.
    }

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public Bounds getBounds() {
        return new BoundingBox(view.getLayoutX(), view.getLayoutY(), SIZE, SIZE);
    }

    /**
     * Elimina el nodo de la escena. (La eliminación del GameLoop/listas se hace fuera.)
     */
    public void destroy() {
        parent.getChildren().remove(view);
    }
}
