package com.layla.entities;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Supplier;

import javafx.embed.swing.JFXPanel;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerMovementTest {

    private static final double DT = 0.016; // ~60fps

    @BeforeAll
    static void initFx() {
        new JFXPanel();
    }

    @Test
    void playerAcceleratesWhenInputPresent() {
        Pane pane = createPane(800, 600);
        FakeInput input = new FakeInput();
        input.set(1.0, 0.0);

        Player player = new Player(input, pane);
        player.setPosition(100, 100);

        runFrames(player, 35);

        assertTrue(player.getView().getTranslateX() > 100.0,
                "Player should have moved to the right when input is present");
    }

    @Test
    void playerClampsAtRightBoundary() {
        Pane pane = createPane(800, 600);
        FakeInput input = new FakeInput();
        input.set(1.0, 0.0);

        Player player = new Player(input, pane);
        double startX = 800.0 - player.getWidth() - 0.5;
        player.setPosition(startX, 200);

        runFrames(player, 60);

        double maxX = 800.0 - player.getWidth();
        assertTrue(player.getView().getTranslateX() <= maxX + 1e-6,
                "Player should clamp to the right boundary");
    }

    @Test
    void diagonalMovementIsNormalized() {
        Pane pane = createPane(800, 600);

        FakeInput horizontalInput = new FakeInput();
        horizontalInput.set(1.0, 0.0);
        Player horizontal = new Player(horizontalInput, pane);
        horizontal.setPosition(0, 0);
        runFrames(horizontal, 120);
        double horizontalDistance = horizontal.getView().getTranslateX();

        FakeInput diagonalInput = new FakeInput();
        diagonalInput.set(1.0, 1.0);
        Player diagonal = new Player(diagonalInput, pane);
        diagonal.setPosition(0, 0);
        runFrames(diagonal, 120);
        Node diagView = diagonal.getView();
        double diagonalDistance = Math.hypot(diagView.getTranslateX(), diagView.getTranslateY());

        assertTrue(diagonalDistance <= horizontalDistance * 1.05,
                "Diagonal speed should be normalized relative to axial movement");
    }

    private static void runFrames(Player player, int frames) {
        for (int i = 0; i < frames; i++) {
            player.update(DT);
        }
    }

    private static final class FakeInput implements Supplier<double[]> {
        private double x;
        private double y;

        void set(double x, double y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public double[] get() {
            return new double[] {x, y};
        }
    }

    private static Pane createPane(double width, double height) {
        Pane pane = new Pane();
        pane.setPrefSize(width, height);
        pane.setMinSize(width, height);
        pane.setMaxSize(width, height);
        pane.resize(width, height);
        return pane;
    }
}
