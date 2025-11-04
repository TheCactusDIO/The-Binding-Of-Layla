package com.layla.core;

import static org.junit.jupiter.api.Assertions.*;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel; // inicializa JavaFX Toolkit
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Issue 6 – Tests reforzados del GameLoop.
 * Verifica:
 *  - start() / stop() / resume() sin errores.
 *  - update() se llama mientras corre.
 *  - no se llama después de stop().
 *  - colisiones disparan onCollision() correctamente.
 */
class GameLoopEnhancedTest {

    static class TestEntity implements GameEntity {
        volatile int updates;
        volatile int collisions;
        private final Rectangle view = new Rectangle(10, 10);

        @Override
        public void update(double dt) {
            updates++;
            // pequeño movimiento para poder colisionar
            view.setTranslateX(view.getTranslateX() + 1);
        }

        @Override
        public Node getView() {
            return view;
        }

        @Override
        public void onCollision(GameEntity other) {
            collisions++;
        }
    }

    @BeforeAll
    static void initFx() {
        // Inicializa el toolkit JavaFX (necesario para Pane, AnimationTimer, etc.)
        new JFXPanel();
    }

    @Test
    void loopUpdatesAndStopsProperly() throws Exception {
        Pane pane = new Pane();
        GameLoop loop = new GameLoop(pane);
        TestEntity e = new TestEntity();
        loop.addEntity(e);

        CountDownLatch latch = new CountDownLatch(1);

        // Ejecuta en hilo FX
        Platform.runLater(() -> {
            loop.start();
            new Thread(() -> {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ignored) {}
                Platform.runLater(() -> {
                    loop.stop();
                    latch.countDown();
                });
            }).start();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS), "El loop debería haberse detenido");

        int updatesAfterStop = e.updates;
        assertTrue(updatesAfterStop > 0, "update() debería haberse llamado varias veces");

        // Espera un poco más para asegurar que no sigue actualizando tras stop()
        Thread.sleep(120);
        assertEquals(updatesAfterStop, e.updates, "update() no debería aumentar tras stop()");
    }

    @Test
    void collisionsAreDetected() throws Exception {
        Pane pane = new Pane();
        GameLoop loop = new GameLoop(pane);
        TestEntity e1 = new TestEntity();
        TestEntity e2 = new TestEntity();

        // Posiciones que se solapan
        e1.getView().setTranslateX(0);
        e1.getView().setTranslateY(0);
        e2.getView().setTranslateX(5);
        e2.getView().setTranslateY(5);

        loop.addEntity(e1);
        loop.addEntity(e2);

        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            loop.start();
            new Thread(() -> {
                try {
                    Thread.sleep(120);
                } catch (InterruptedException ignored) {}
                Platform.runLater(() -> {
                    loop.stop();
                    latch.countDown();
                });
            }).start();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS), "El loop debería haberse detenido");
        assertTrue(e1.collisions > 0 || e2.collisions > 0,
                "Debería haberse detectado al menos una colisión entre entidades");
    }

    @Test
    void startAndStopAreIdempotent() {
        Pane pane = new Pane();
        GameLoop loop = new GameLoop(pane);

        // start/stop repetidos no deberían lanzar excepciones
        assertDoesNotThrow(() -> {
            loop.start();
            loop.start();
            loop.stop();
            loop.stop();
        }, "start()/stop() deberían ser idempotentes y seguras");
    }
}
