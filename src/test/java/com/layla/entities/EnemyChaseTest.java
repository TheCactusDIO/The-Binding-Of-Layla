package com.layla.entities;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.layla.model.Enemy;
import com.layla.model.EnemyType;

import javafx.embed.swing.JFXPanel;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EnemyChaseTest {

    private static final double DT = 0.016;

    @BeforeAll
    static void initFx() {
        new JFXPanel();
    }

    @Test
    void enemyMovesTowardsPlayerCenter() {
        Pane pane = createPane(800, 600);
        Enemy enemy = new Enemy(
                EnemyType.SHOOTER,
                pane,
                () -> new double[] {400.0, 300.0},
                e -> {},
                g -> {},
                s -> {},
                1.0,
                1.0,
                1.0
        );
        enemy.setPosition(100.0, 100.0);
        Rectangle view = (Rectangle) enemy.getView();

        double initialX = view.getLayoutX();
        double initialY = view.getLayoutY();
        double initialDist = distanceToTarget(view, 400.0, 300.0);

        for (int i = 0; i < 120; i++) {
            enemy.update(DT);
        }

        double finalX = view.getLayoutX();
        double finalY = view.getLayoutY();
        double finalDist = distanceToTarget(view, 400.0, 300.0);

        assertTrue(finalX > initialX, "Enemy should move towards increasing X towards player");
        assertTrue(finalY > initialY, "Enemy should move towards increasing Y towards player");
        assertTrue(finalDist < initialDist, "Enemy should get closer to the player center");
    }

    private static double distanceToTarget(Rectangle view, double tx, double ty) {
        double cx = view.getLayoutX() + view.getWidth() * 0.5;
        double cy = view.getLayoutY() + view.getHeight() * 0.5;
        return Math.hypot(tx - cx, ty - cy);
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
