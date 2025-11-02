package com.layla.ui;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Disabled;
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
import javafx.stage.Stage;

/**
 * SceneRouter / UI smoke tests.
 * Pensado para ser estable aunque haya animaciones y overlays.
 */
@ExtendWith(ApplicationExtension.class)
@TestMethodOrder(OrderAnnotation.class)
public class SceneRouterSmokeTest {

    // Texto alternativo para localizar botones por idioma
    private static final List<String> TEXT_BACK = List.of("Back", "Atrás", "Volver", "Cerrar");
    private static final List<String> TEXT_OK   = List.of("OK", "Aceptar", "Sí", "Si");

    // Estilo del overlay (coincide con OverlayRouter.setStyle rgba(...))
    private static final String OVERLAY_STYLE_TOKEN = "rgba(0,0,0";

    @Start
    private void start(Stage stage) throws Exception {
        new App().start(stage);
        // Navegación SIN fade para evitar esperas por animación en test
        Platform.runLater(() -> SceneRouter.go("ui/main_menu.fxml", 1280, 720));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // ---------- Helpers ----------

    /** Espera hasta que exista un nodo accesible por query (CSS o fx:id '#id'). */
    private static void waitForNode(FxRobot robot, String query) {
        try {
            WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS,
                    () -> robot.lookup(query).tryQuery().isPresent());
        } catch (TimeoutException e) {
            fail("Timeout esperando nodo: " + query, e);
        }
    }

    /** Espera hasta que NO exista un nodo accesible por query. */
    private static void waitUntilGone(FxRobot robot, String query) {
        try {
            WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS,
                    () -> !robot.lookup(query).tryQuery().isPresent());
        } catch (TimeoutException e) {
            fail("Timeout esperando a que desaparezca el nodo: " + query, e);
        }
    }

    /** Espera hasta que no exista ningún nodo que cumpla el predicado del overlay. */
    private static void waitOverlayGone(FxRobot robot) {
        try {
            WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, () ->
                    !robot.lookup((Node n) -> n.getStyle() != null && n.getStyle().contains(OVERLAY_STYLE_TOKEN))
                          .tryQuery().isPresent()
            );
        } catch (TimeoutException e) {
            fail("Timeout esperando a que desaparezca el overlay", e);
        }
    }

    /** Click por texto usando varias alternativas (idiomas). */
    private static void clickAnyByText(FxRobot robot, List<String> candidates) {
        for (String text : candidates) {
            if (robot.lookup(text).tryQuery().isPresent()) {
                robot.clickOn(text);
                return;
            }
        }
        fail("No se encontró ningún botón con textos: " + candidates);
    }

    /** Devuelve el Stage actual si existe, o null si no hay ventanas. */
    private static Stage tryStage(FxRobot robot) {
        try {
            return (Stage) robot.window(0);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Fuerza tamaño de Stage y espera a FX. */
    private static void setStageSize(FxRobot robot, double w, double h) {
        Stage s = tryStage(robot);
        assertNotNull(s, "No hay Stage disponible");
        Platform.runLater(() -> { s.setWidth(w); s.setHeight(h); });
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Comprueba que un Node está dentro de la escena (no clipeado). */
    private static void assertNodeInsideScene(Node n, String what) {
        Bounds b = n.localToScene(n.getBoundsInLocal());
        double sw = n.getScene().getWidth();
        double sh = n.getScene().getHeight();
        assertTrue(b.getMinX() >= 0 && b.getMaxX() <= sw, what + " fuera en X (" + b + " vs " + sw + ")");
        assertTrue(b.getMinY() >= 0 && b.getMaxY() <= sh, what + " fuera en Y (" + b + " vs " + sh + ")");
    }

    // ---------- TESTS ----------

    @Test
    @Order(1)
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
        // Si tu SceneRouter añade también menu.css, descomenta:
        // assertTrue(sheets.contains("menu.css"), "menu.css no está aplicado");
    }

    @Test
    @Order(2)
    void options_overlay_opens_and_closes(FxRobot robot) {
        waitForNode(robot, "#optionsBtn");
        robot.clickOn("#optionsBtn");

        // Espera a que aparezca el overlay
        try {
            WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, () ->
                    robot.lookup((Node n) -> n.getStyle() != null && n.getStyle().contains(OVERLAY_STYLE_TOKEN))
                         .tryQuery().isPresent()
            );
        } catch (TimeoutException e) {
            fail("El overlay no apareció al abrir Options", e);
        }

        // Cierra con Back (varios textos válidos)
        clickAnyByText(robot, TEXT_BACK);

        // Espera a que desaparezca
        waitOverlayGone(robot);
    }

    @Test
    @Order(3)
    void play_changes_scene_and_keeps_size(FxRobot robot) {
        waitForNode(robot, "#playBtn");

        setStageSize(robot, 1000, 500); // tamaño conocido
        robot.clickOn("#playBtn");

        // Espera a que desaparezca el botón del menú (nueva escena cargada)
        waitUntilGone(robot, "#playBtn");

        // Verifica tamaño conservado
        Stage s = tryStage(robot);
        assertNotNull(s, "No hay Stage tras Play");
        assertEquals(1000, (int) s.getWidth(),  "El ancho del Stage cambió tras Play");
        assertEquals(500,  (int) s.getHeight(), "El alto del Stage cambió tras Play");
    }

    /**
     * Futuro: cuando `SceneRouter.goWithFade(String,int,int)` haga "reset-size" real del Stage,
     * quita @Disabled y este test validará el cambio a 1280x720.
     */
    @Test
    @Order(4)
    @Disabled("Pendiente: implementar resize real del Stage en goWithFade(...)")
    void router_keep_size_vs_reset_size(FxRobot robot) {
        // Volver al menú conservando tamaño
        Platform.runLater(() -> SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml"));
        WaitForAsyncUtils.waitForFxEvents();
        waitForNode(robot, "#playBtn");

        // Guardar tamaño actual
        Stage s = tryStage(robot);
        assertNotNull(s, "No hay Stage al volver al menú");
        int keptW = (int) s.getWidth();
        int keptH = (int) s.getHeight();

        // Reset de tamaño a 1280x720
        Platform.runLater(() -> SceneRouter.goWithFade("ui/main_menu.fxml", 1280, 720));
        WaitForAsyncUtils.waitForFxEvents();
        waitForNode(robot, "#playBtn");

        assertEquals(1280, (int) s.getWidth(),  "goWithFade no aplicó ancho 1280");
        assertEquals(720,  (int) s.getHeight(), "goWithFade no aplicó alto 720");
        assertTrue(keptW != 1280 || keptH != 720, "keep-size y reset-size deberían diferir");
    }

    @Test
    @Order(5)
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

    @Test
    @Order(99)
    void exit_overlay_opens_and_can_cancel_without_killing_tests(FxRobot robot) {
        // Abre overlay de Exit
        waitForNode(robot, "#exitBtn");
        robot.clickOn("#exitBtn");

        // Espera a que aparezca el overlay (mismo token rgba que en settings)
        try {
            WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, () ->
                    robot.lookup((Node n) -> n.getStyle() != null && n.getStyle().contains("rgba(0,0,0"))
                        .tryQuery().isPresent()
            );
        } catch (TimeoutException e) {
            fail("El overlay de Exit no apareció", e);
        }

        // Pulsa "Cancel" para no cerrar ventana ni JVM
        // (adapta si traduces el texto del botón)
        robot.clickOn("Cancel");

        // Overlay debería desaparecer
        waitOverlayGone(robot);

        // Y la ventana debe seguir activa
        Stage s = tryStage(robot);
        assertNotNull(s, "El Stage no debería haberse cerrado con Cancel");
        assertTrue(s.isShowing(), "El Stage debería seguir mostrándose tras Cancel");
    }
}
