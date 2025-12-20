package com.layla.entities;

import com.layla.core.AssetsManager;
import com.layla.core.GameEntity;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.geometry.Rectangle2D;

/**
 * Rock obstacle.
 *
 * Uses a 1-row spritesheet (rocks_sheet.png) with 6 columns (32x32 each).
 * GameController chooses which column to use depending on the floor.
 */
public final class Rock implements GameEntity {

    // Keep this size for collisions (your level generation uses Rock.SIZE as spacing)
    public static final double SIZE = 40.0;

    private static final int SHEET_COLS = 6;
    private static final int FRAME_W = 32;
    private static final int FRAME_H = 32;

    // Cached sheet image (loaded once)
    private static Image SHEET;

    private final ImageView view = new ImageView();
    private final Pane parent;

    private int skinCol = 0; // 0..5

    public Rock(double x, double y, Pane parent) {
        this(x, y, parent, 0);
    }

    public Rock(double x, double y, Pane parent, int skinCol) {
        this.parent = parent;

        ensureSheetLoaded();

        view.setManaged(false);
        view.setMouseTransparent(true);
        view.setSmooth(false); // pixel-art friendly

        view.setFitWidth(SIZE);
        view.setFitHeight(SIZE);
        view.setPreserveRatio(false);

        if (SHEET != null) {
            view.setImage(SHEET);
            setSkinColumn(skinCol);
        }

        view.setLayoutX(x - SIZE / 2.0);
        view.setLayoutY(y - SIZE / 2.0);

        parent.getChildren().add(view);
    }

    private static void ensureSheetLoaded() {
        if (SHEET != null) return;

        // Primary expected path (matches your other assets under resources/assets/images)
        SHEET = AssetsManager.loadImage("assets/images/rocks_sheet.png");

        // Fallback: allow placing it directly under /assets/
        if (SHEET == null) {
            SHEET = AssetsManager.loadImage("assets/rocks_sheet.png");
        }
    }

    /**
     * Picks which sprite column to show (0..5).
     */
    public void setSkinColumn(int col) {
        int safe = Math.max(0, Math.min(SHEET_COLS - 1, col));
        this.skinCol = safe;

        if (SHEET == null) return;

        view.setViewport(new Rectangle2D(safe * FRAME_W, 0, FRAME_W, FRAME_H));
    }

    public int getSkinColumn() {
        return skinCol;
    }

    @Override
    public void update(double dt) {
        // static obstacle
    }

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public Bounds getBounds() {
        // Use view layout + SIZE (consistent for collisions)
        return new BoundingBox(view.getLayoutX(), view.getLayoutY(), SIZE, SIZE);
    }

    /**
     * Removes the rock from the scene graph.
     * GameController can still remove it from gameLoop/obstacles, this just clears the Node safely.
     */
    public void destroy() {
        if (parent != null) {
            parent.getChildren().remove(view);
        }
    }
}
