package com.layla.entities;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import com.layla.model.Enemy;
import com.layla.model.EnemyType;

import javafx.embed.swing.JFXPanel;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EnemyDamageTest {

    @BeforeAll
    static void initFx() {
        new JFXPanel();
    }

    @Test
    void enemyTakesDamageAndDies() {
        Pane pane = createPane(400, 400);
        AtomicBoolean removed = new AtomicBoolean(false);

        var profile = com.layla.AppContext.balance().profile(EnemyType.SHOOTER);
        profile.baseHp = 6.0;

        Enemy enemy = new Enemy(
                EnemyType.SHOOTER,
                pane,
                () -> new double[] {200.0, 200.0},
                e -> removed.set(true),
                g -> {},
                s -> {},
                1.0,
                1.0,
                1.0
        );
        enemy.setPosition(150.0, 150.0);

        Projectile projectile = new Projectile(1.0, 0.0, 100.0, 1.0, 3.0, pane, g -> {});

        enemy.onCollision(projectile); // health -> 3
        assertFalse(enemy.isDead(), "Enemy should survive after first hit");

        enemy.onCollision(projectile); // health -> 0
        assertTrue(enemy.isDead(), "Enemy should die after lethal damage");
        assertTrue(removed.get(), "Removal callback should be invoked when enemy dies");
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
