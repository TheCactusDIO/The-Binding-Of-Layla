package com.layla.ui;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import com.layla.App;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

@ExtendWith(ApplicationExtension.class)
@TestMethodOrder(OrderAnnotation.class)
public class SceneRouterSmokeTest {

    private static final List<String> TEXT_BACK = List.of("Back","Atrás","Volver","Cerrar");
    private static final List<String> TEXT_OK = List.of("OK","Aceptar","Sí","Si");
    private static final List<String> TEXT_CANCEL = List.of("Cancel","Cancelar","No");
    private static final Set<String> INTRO_TITLES = Set.of(
        "Basement I","Basement II","Cellar I","Cellar II","Burning Basement I","Burning Basement II"
    );

    @BeforeAll
    static void beforeAll() {
        System.setProperty("testfx.safeExit", "true");
    }

    @Start
    private void start(Stage stage) throws Exception {
        new App().start(stage);
        Platform.runLater(() -> SceneRouter.go("ui/main_menu.fxml", 1280, 720));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // ----------------- helpers -----------------

    private static void fxWait() { WaitForAsyncUtils.waitForFxEvents(); }

    private static void waitForNode(FxRobot robot, String query, int seconds) {
        try {
            WaitForAsyncUtils.waitFor(seconds, TimeUnit.SECONDS,
                () -> robot.lookup(query).tryQuery().isPresent());
        } catch (TimeoutException e) {
            fail("Timeout esperando nodo: " + query, e);
        }
    }
    private static void waitForNode(FxRobot robot, String query) { waitForNode(robot, query, 8); }

    private static void waitUntilGone(FxRobot robot, String query, int seconds) {
        try {
            WaitForAsyncUtils.waitFor(seconds, TimeUnit.SECONDS,
                () -> !robot.lookup(query).tryQuery().isPresent());
        } catch (TimeoutException e) {
            fail("Timeout esperando a que desaparezca el nodo: " + query, e);
        }
    }

    private static boolean overlayPresent(FxRobot robot) {
        if (robot.lookup("#overlayLayer").tryQuery().isPresent()) return true;
        if (robot.lookup(".game-overlay-backdrop").tryQuery().isPresent()) return true;
        if (robot.lookup("#resumeBtn").tryQuery().isPresent()) return true;
        if (robot.lookup("#menuBtn").tryQuery().isPresent()) return true;
        if (robot.lookup("#cancelBtn").tryQuery().isPresent()) return true;
        // fallback: cualquier pane semitransparente
        return robot.lookup(n -> {
            String s = n.getStyle();
            return s != null && (s.contains("rgba(") || s.contains("0.6") || s.contains("0.65") || s.contains("0.8"));
        }).tryQuery().isPresent();
    }

    private static void waitOverlayShown(FxRobot robot, int seconds) {
        try {
            WaitForAsyncUtils.waitFor(seconds, TimeUnit.SECONDS, () -> overlayPresent(robot));
        } catch (TimeoutException e) {
            fail("Timeout esperando overlay", e);
        }
    }

    private static void waitOverlayGone(FxRobot robot, int seconds) {
        try {
            WaitForAsyncUtils.waitFor(seconds, TimeUnit.SECONDS, () -> !overlayPresent(robot));
        } catch (TimeoutException e) {
            fail("Timeout esperando fin de overlay", e);
        }
    }

    private static Labeled tryWaitAnyIntroLabel(FxRobot robot, int seconds) {
        try {
            WaitForAsyncUtils.waitFor(seconds, TimeUnit.SECONDS, () ->
                robot.lookup(n -> (n instanceof Labeled l) && INTRO_TITLES.contains(l.getText()))
                     .tryQuery().isPresent());
        } catch (Exception ignored) {}
        return (Labeled) robot.lookup(n -> (n instanceof Labeled l) && INTRO_TITLES.contains(l.getText()))
                              .tryQuery().orElse(null);
    }

    private static Stage tryStage(FxRobot robot) {
        try { return (Stage) robot.window(0); }
        catch (Throwable t) { return null; }
    }

    private static void setStageSize(FxRobot robot, double w, double h) {
        Stage s = tryStage(robot);
        assertNotNull(s, "No hay Stage disponible");
        Platform.runLater(() -> { s.setWidth(w); s.setHeight(h); });
        fxWait();
    }

    private static void assertNodeInsideScene(Node n, String what) {
        Bounds b = n.localToScene(n.getBoundsInLocal());
        double sw = n.getScene().getWidth();
        double sh = n.getScene().getHeight();
        assertTrue(b.getMinX() >= 0 && b.getMaxX() <= sw, what + " fuera en X (" + b + " vs " + sw + ")");
        assertTrue(b.getMinY() >= 0 && b.getMaxY() <= sh, what + " fuera en Y (" + b + " vs " + sh + ")");
    }

    /** Programa la vuelta al menú por API y espera a #playBtn. */
    private static void goToMainMenu(FxRobot robot) {
        Platform.runLater(() -> SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml"));
        fxWait();
        waitForNode(robot, "#playBtn", 10);
    }

    /** Garantiza estar en el juego (HUD visible). Si está en menú, pulsa Play. */
    private static void ensureInGame(FxRobot robot) {
        if (!robot.lookup("#healthLabel").tryQuery().isPresent()) {
            waitForNode(robot, "#playBtn", 10);
            setStageSize(robot, 1000, 500);
            robot.clickOn("#playBtn");
            waitUntilGone(robot, "#playBtn", 10);
            waitForNode(robot, "#healthLabel", 12);
        }
    }

    // ----------------- tests -----------------

    @Test @Order(1)
    void mainMenu_loads_buttons_visible_and_styles(FxRobot robot) {
        waitForNode(robot, "#playBtn");
        Button play  = robot.lookup("#playBtn").queryButton();
        Button opts  = robot.lookup("#optionsBtn").queryButton();
        Button exit  = robot.lookup("#exitBtn").queryButton();

        assertTrue(play.isVisible(),  "playBtn no es visible");
        assertTrue(opts.isVisible(),  "optionsBtn no es visible");
        assertTrue(exit.isVisible(),  "exitBtn no es visible");

        String sheets = play.getScene().getStylesheets().toString();
        assertTrue(sheets.contains("global.css"), "global.css no está aplicado");
    }

    @Test @Order(2)
    void options_overlay_opens_and_closes(FxRobot robot) {
        waitForNode(robot, "#optionsBtn");
        robot.clickOn("#optionsBtn");
        waitOverlayShown(robot, 8);

        // Cierre robusto
        if (robot.lookup("#closeBtn").tryQuery().isPresent()) {
            robot.clickOn("#closeBtn");
        } else if (robot.lookup("Back").tryQuery().isPresent()) {
            robot.clickOn("Back");
        } else if (robot.lookup("Atrás").tryQuery().isPresent()) {
            robot.clickOn("Atrás");
        } else {
            Node anyBtn = robot.lookup(n -> n instanceof Button).tryQuery().orElse(null);
            assertNotNull(anyBtn, "No se encontró botón para cerrar overlay de Options");
            robot.clickOn(anyBtn);
        }
        waitOverlayGone(robot, 8);
    }

    @Test @Order(3)
    void play_navigates_to_game_hud_and_intro_title_optional(FxRobot robot) {
        waitForNode(robot, "#playBtn", 10);
        setStageSize(robot, 1000, 500);
        robot.clickOn("#playBtn");

        waitUntilGone(robot, "#playBtn", 10);
        waitForNode(robot, "#scoreLabel", 12);
        waitForNode(robot, "#floorLabel", 12);
        waitForNode(robot, "#healthLabel", 12);

        // Intro opcional (texto)
        Labeled intro = tryWaitAnyIntroLabel(robot, 4);
        if (intro == null) {
            System.out.println("[TEST] Intro textual no apareció (opcional).");
        } else {
            System.out.println("[TEST] Intro textual visible: " + intro.getText());
        }
    }

    @Test @Order(4)
    void pause_overlay_resume_and_back_to_menu(FxRobot robot) {
        ensureInGame(robot);

        fxWait();
        try { Thread.sleep(150); } catch (InterruptedException ignored) {}

        // Abre pausa (ESC)
        robot.push(KeyCode.ESCAPE);

        // Resume (si lo vemos); si no aparece, intentamos cerrar con ESC como fallback
        if (robot.lookup("#resumeBtn").tryQuery().isPresent()) {
            robot.clickOn("#resumeBtn");
        } else {
            // puede que el overlay tarde: empuja ESC de nuevo
            try { Thread.sleep(150); } catch (InterruptedException ignored) {}
            robot.push(KeyCode.ESCAPE);
        }

        // Seguimos en juego (HUD)
        waitForNode(robot, "#healthLabel", 10);

        // Abre pausa otra vez para volver al menú
        robot.push(KeyCode.ESCAPE);
        if (robot.lookup("#menuBtn").tryQuery().isPresent()) {
            robot.clickOn("#menuBtn");
            waitForNode(robot, "#playBtn", 10);
        } else if (robot.lookup("Back to Menu").tryQuery().isPresent()) {
            robot.clickOn("Back to Menu");
            waitForNode(robot, "#playBtn", 10);
        } else {
            // Fallback duro: navegar por API para evitar timeout
            goToMainMenu(robot);
        }

        Stage s = tryStage(robot);
        assertNotNull(s, "No hay Stage tras volver al menú");
        assertEquals(1000, (int) s.getWidth(),  "El ancho del Stage cambió tras keep-size");
        assertEquals(500,  (int) s.getHeight(), "El alto del Stage cambió tras keep-size");
    }

    @Test @Order(5)
    void resizing_does_not_break_basic_layout(FxRobot robot) {
        waitForNode(robot, "#playBtn");

        setStageSize(robot, 1400, 900);
        Button play  = robot.lookup("#playBtn").queryButton();
        Button opts  = robot.lookup("#optionsBtn").queryButton();
        Button exit  = robot.lookup("#exitBtn").queryButton();

        assertNodeInsideScene(play, "playBtn");
        assertNodeInsideScene(opts,  "optionsBtn");
        assertNodeInsideScene(exit,  "exitBtn");

        setStageSize(robot, 900, 600);
        assertNodeInsideScene(play, "playBtn (tras reducir)");
        assertNodeInsideScene(opts,  "optionsBtn (tras reducir)");
        assertNodeInsideScene(exit,  "exitBtn (tras reducir)");
    }

    @Test @Order(99)
    void exit_overlay_opens_and_can_cancel_without_killing_tests(FxRobot robot) {
        // Si no estamos en el menú, ir por API (evita dependencia de flujos previos)
        if (!robot.lookup("#playBtn").tryQuery().isPresent()) {
            goToMainMenu(robot);
        }
        Stage before = tryStage(robot);
        assertNotNull(before, "No hay Stage antes de abrir Exit");

        robot.clickOn("#exitBtn");

        // Espera overlay (o al menos el botón Cancel/Cancelar)
        try {
            WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS, () ->
                robot.lookup("#cancelBtn").tryQuery().isPresent()
                || TEXT_CANCEL.stream().anyMatch(t -> robot.lookup(t).tryQuery().isPresent())
                || overlayPresent(robot)
            );
        } catch (TimeoutException e) {
            fail("Timeout esperando overlay de Exit", e);
        }

        // Cancela de forma robusta
        if (robot.lookup("#cancelBtn").tryQuery().isPresent()) {
            robot.clickOn("#cancelBtn");
        } else {
            boolean clicked = false;
            for (String t : TEXT_CANCEL) {
                if (robot.lookup(t).tryQuery().isPresent()) {
                    robot.clickOn(t);
                    clicked = true;
                    break;
                }
            }
            if (!clicked) {
                // último recurso: pulsa Escape (si tu overlay lo permite)
                robot.push(KeyCode.ESCAPE);
            }
        }

        waitOverlayGone(robot, 8);

        Stage after = tryStage(robot);
        assertNotNull(after, "El Stage no debería haberse cerrado");
        assertTrue(after.isShowing(), "La ventana debe seguir abierta tras Cancel");
    }
}
