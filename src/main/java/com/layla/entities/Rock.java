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
 * Entidad Roca (Obstáculo estático).
 * <p>
 * Funcionalidad:
 * <ul>
 * <li>Actúa como un bloqueador de movimiento y proyectiles.</li>
 * <li>Utiliza un spritesheet con múltiples variantes visuales (skins).</li>
 * <li>Su tamaño de colisión es constante ({@link #SIZE}) para facilitar la generación de niveles.</li>
 * </ul>
 */
public final class Rock implements GameEntity {

    /** Tamaño lógico y visual de la roca. */
    public static final double SIZE = 40.0;

    private static final int SHEET_COLS = 6;
    private static final int FRAME_W = 32;
    private static final int FRAME_H = 32;

    /** Referencia estática al spritesheet para cargarla una sola vez. */
    private static Image SHEET;

    private final Pane parent;
    private final ImageView view = new ImageView();

    /** Índice de la columna del spritesheet a usar (skin). */
    private int skinCol;

    /**
     * Crea una roca con la skin por defecto.
     *
     * @param x      Posición X central.
     * @param y      Posición Y central.
     * @param parent Contenedor JavaFX.
     */
    public Rock(double x, double y, Pane parent) {
        this(x, y, parent, 0);
    }

    /**
     * Crea una roca con una skin específica.
     *
     * @param x       Posición X central.
     * @param y       Posición Y central.
     * @param parent  Contenedor JavaFX.
     * @param skinCol Índice de la columna del sprite (0 a 5).
     */
    public Rock(double x, double y, Pane parent, int skinCol) {
        this.parent = Objects.requireNonNull(parent, "parent");

        ensureSheetLoaded();

        view.setManaged(false);
        view.setMouseTransparent(true);
        view.setSmooth(false); // Estilo pixel-art
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

    /**
     * Carga el spritesheet si no está cargado.
     */
    private static void ensureSheetLoaded() {
        if (SHEET != null) return;

        SHEET = AssetsManager.loadImage("assets/images/rocks_sheet.png");
        if (SHEET == null) {
            // Intento de ruta alternativa
            SHEET = AssetsManager.loadImage("assets/rocks_sheet.png");
        }
    }

    /**
     * Asegura que el índice de columna esté dentro del rango válido.
     */
    private static int clampColumn(int col) {
        return Math.max(0, Math.min(SHEET_COLS - 1, col));
    }

    /**
     * Cambia la apariencia de la roca seleccionando una columna del spritesheet.
     *
     * @param col Índice de columna (0-5).
     */
    public void setSkinColumn(int col) {
        int safe = clampColumn(col);
        this.skinCol = safe;

        // Define la región visible de la imagen (viewport)
        view.setViewport(new Rectangle2D(safe * FRAME_W, 0, FRAME_W, FRAME_H));
    }

    /**
     * Obtiene el índice de skin actual.
     */
    public int getSkinColumn() {
        return skinCol;
    }

    /**
     * Actualización por frame (sin efecto para objetos estáticos).
     */
    @Override
    public void update(double dt) {
        // No-op
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
     * Elimina visualmente la roca.
     */
    public void destroy() {
        parent.getChildren().remove(view);
    }
}
