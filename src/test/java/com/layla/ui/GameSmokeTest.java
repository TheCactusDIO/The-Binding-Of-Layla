package com.layla.ui;

import com.layla.core.GameEntity;
import com.layla.core.GameLoop;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Issue 6 – Game Smoke Test
 * Verifica que el GameController y GameLoop pueden inicializarse correctamente
 * y funcionar algunos frames sin colisiones ni errores fatales.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GameSmokeTest {

    private static Stage stage;
    private static Pane bootPane; // escena básica del @BeforeAll
    private static GameLoop loop;

    /** Entidad dummy simple que se mueve horizontalmente para verificar el loop. */
    static class DummyEntity implements GameEntity {
        private final javafx.scene.shape.Rectangle rect = new javafx.scene.shape.Rectangle(30, 30);

        @Override
        public void update(double dt) {
            rect.setTranslateX(rect.getTranslateX() + 50 * dt);
            if (rect.getTranslateX() > 400) rect.setTranslateX(0);
        }

        @Override
        public Node getView() { return rect; }

        @Override
        public Bounds getBounds() { return rect.getBoundsInParent(); }
    }

    @BeforeAll
    static void initFx() throws Exception {
        // Inicializa el toolkit JavaFX en tests/headless
        new JFXPanel();

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(new Runnable() {
            @Override public void run() {
                stage = new Stage();
                bootPane = new Pane();
                Scene scene = new Scene(bootPane, 800, 600);
                stage.setScene(scene);
                // En headless Monocle no es imprescindible hacer show()
                latch.countDown();
            }
        });
        Assertions.assertTrue(latch.await(3, TimeUnit.SECONDS), "JavaFX Stage debería inicializarse");
    }

    @Test
    @Order(1)
    void gameLoopStartsAndStopsSafely() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(new Runnable() {
            @Override public void run() {
                loop = new GameLoop(bootPane);
                loop.setDebugLoggingEnabled(true);

                DummyEntity e = new DummyEntity();
                loop.addEntity(e);

                // start() no debería lanzar excepciones
                try {
                    loop.start();
                } catch (Exception ex) {
                    Assertions.fail("GameLoop.start() lanzó excepción: " + ex.getMessage());
                }

                // Programamos una parada tras ~300 ms de frames
                new Thread(new Runnable() {
                    @Override public void run() {
                        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                        Platform.runLater(new Runnable() {
                            @Override public void run() {
                                loop.stop();
                                latch.countDown();
                            }
                        });
                    }
                }).start();
            }
        });

        Assertions.assertTrue(latch.await(3, TimeUnit.SECONDS), "GameLoop debería detenerse correctamente");
        Assertions.assertFalse(loop.isRunning(), "Loop debería estar parado");
    }

    @Test
    @Order(2)
    void gameControllerInitializesHUDAndPause() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(new Runnable() {
            @Override public void run() {
                try {
                    GameController gc = new GameController();

                    // ---------- Construimos escena propia para este controller ----------
                    final StackPane root = new StackPane();
                    final Pane gameArea = new Pane();
                    final StackPane overlay = new StackPane();
                    final javafx.scene.layout.HBox hud = new javafx.scene.layout.HBox(10);
                    final Label score = new Label();
                    final Label floor = new Label();
                    final Label health = new Label();

                    // Layout: root contiene gameArea, hud y overlay
                    root.getChildren().addAll(gameArea, hud, overlay);

                    // Montamos esta escena en el stage ANTES de onEnter()
                    stage.setScene(new Scene(root, 800, 600));

                    // ---------- Inyección mínima de nodos simulados ----------
                    // Campos @FXML del GameController:
                    injectField(gc, "root", root);
                    injectField(gc, "gameArea", gameArea);
                    injectField(gc, "overlayLayer", overlay);
                    injectField(gc, "hudBar", hud);
                    injectField(gc, "scoreLabel", score);
                    injectField(gc, "floorLabel", floor);
                    injectField(gc, "healthLabel", health);

                    // Simula entrada a la vista y arranque del juego (timer/score)
                    gc.onEnter();          // aquí GameController crea GameLoop con gameArea (no nulo) y registra input con scene
                    gc.signalGameStart();  // arranca HUD/timer/score desde 500

                    // ---------- Abrir pausa (invocando método @FXML privado por reflexión) ----------
                    try {
                        final var mPause = gc.getClass().getDeclaredMethod("onPausePressed");
                        mPause.setAccessible(true);
                        Platform.runLater(new Runnable() {
                            @Override public void run() {
                                try { mPause.invoke(gc); } catch (Exception ex) { ex.printStackTrace(); }
                            }
                        });
                    } catch (Exception e) {
                        Assertions.fail("No se pudo invocar onPausePressed(): " + e.getMessage());
                    }

                    // ---------- Cerrar pausa (resume) ----------
                    try {
                        final var mResume = gc.getClass().getDeclaredMethod("resumeFromPause");
                        mResume.setAccessible(true);
                        Platform.runLater(new Runnable() {
                            @Override public void run() {
                                try {
                                    mResume.invoke(gc);
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                    Assertions.fail("No se pudo invocar resumeFromPause(): " + ex.getMessage());
                                }
                                latch.countDown();
                            }
                        });
                    } catch (NoSuchMethodException e) {
                        Assertions.fail("GameController no tiene resumeFromPause(): " + e.getMessage());
                    }

                } catch (Throwable t) {
                    t.printStackTrace();
                    Assertions.fail("Error al inicializar GameController: " + t.getMessage());
                }
            }
        });

        Assertions.assertTrue(latch.await(4, TimeUnit.SECONDS),
                "GameController debería abrir/cerrar pausa sin colgarse");
    }

    @Test
    @Order(3)
    void multipleStartsAreIdempotent() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(new Runnable() {
            @Override public void run() {
                try {
                    GameLoop testLoop = new GameLoop(new Pane());
                    testLoop.start();
                    testLoop.start();
                    testLoop.stop();
                    testLoop.stop();
                    latch.countDown();
                } catch (Exception e) {
                    Assertions.fail("start()/stop() deberían ser idempotentes: " + e.getMessage());
                }
            }
        });
        Assertions.assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    // ---------- Helper de inyección reflexiva (menos ruido arriba) ----------
    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
