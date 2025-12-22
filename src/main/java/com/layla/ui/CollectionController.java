package com.layla.ui;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/**
 * Controlador de la pantalla "Collection".
 *
 * Responsabilidades:
 *  - Cargar el contenido de cada Tab desde su FXML.
 *  - Mantener referencias a los controladores hijos para poder refrescarlos al entrar o al cambiar de pestaña.
 *  - Gestionar navegación de vuelta al menú.
 *
 * Nota: Esta clase asume que los controladores de las pestañas implementan ViewLifecycle (onEnter()).
 */
public class CollectionController implements ViewLifecycle {

    // =========================
    // FXML
    // =========================
    @FXML private TabPane tabPane;
    @FXML private Tab tabBestiary;
    @FXML private Tab tabItems;
    @FXML private Tab tabAchievements;
    @FXML private Tab tabRanking;

    // =========================
    // Controladores hijos (una referencia por pestaña)
    // =========================
    private ViewLifecycle bestiaryController;
    private ViewLifecycle itemsController;
    private ViewLifecycle achievementsController;
    private ViewLifecycle rankingController;

    /**
     * Inicializa la pantalla:
     *  - Carga el contenido FXML de cada pestaña dentro del TabPane.
     *  - Captura el controller de cada FXML si implementa ViewLifecycle.
     *  - Registra un listener para refrescar automáticamente al cambiar de pestaña.
     */
    @FXML
    private void initialize() {
        // Cargar pestañas (log claro si falta algún recurso)
        bestiaryController = loadTabContent(tabBestiary, "ui/bestiary.fxml");
        itemsController = loadTabContent(tabItems, "ui/items.fxml");
        achievementsController = loadTabContent(tabAchievements, "ui/achievements.fxml");
        rankingController = loadTabContent(tabRanking, "ui/ranking.fxml");

        // Refrescar la pestaña visible cuando el usuario cambie de tab
        if (tabPane != null) {
            tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (newTab != null) {
                    refreshSelectedTab(newTab);
                }
            });
        }
    }

    /**
     * Lifecycle: se llama cuando se entra a la vista Collection.
     * Refresca la pestaña actualmente seleccionada (o la primera si no hay selección).
     */
    @Override
    public void onEnter() {
        if (tabPane == null) return;

        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected == null && !tabPane.getTabs().isEmpty()) {
            selected = tabPane.getTabs().get(0);
            tabPane.getSelectionModel().select(selected);
        }

        if (selected != null) {
            refreshSelectedTab(selected);
        }
    }

    /**
     * Carga un FXML dentro de un Tab.
     *
     * Limpieza y robustez:
     *  - Comprueba que el recurso existe (URL != null) para evitar NPE.
     *  - Setea el content del Tab.
     *  - Devuelve el controller si implementa ViewLifecycle (si no, devuelve null).
     *
     * @param tab      Tab destino
     * @param fxmlPath Ruta dentro de resources, ej: "ui/bestiary.fxml"
     * @return controller casteado a ViewLifecycle o null si no aplica / falla
     */
    private ViewLifecycle loadTabContent(Tab tab, String fxmlPath) {
        if (tab == null) return null;

        URL url = getClass().getResource("/" + Objects.requireNonNull(fxmlPath));
        if (url == null) {
            logError("No se encuentra el recurso", fxmlPath, null);
            return null;
        }

        try {
            FXMLLoader loader = new FXMLLoader(url);
            Parent content = loader.load();
            tab.setContent(content);

            Object controller = loader.getController();
            if (controller instanceof ViewLifecycle vc) {
                return vc;
            }

            // No es error fatal: el Tab funciona igual, solo que no podremos refrescar con onEnter().
            logWarn("El controller NO implementa ViewLifecycle (no se refrescará al cambiar de pestaña)", fxmlPath);
            return null;

        } catch (IOException e) {
            logError("Error cargando FXML", fxmlPath, e);
            return null;
        }
    }

    /**
     * Refresca la pestaña indicada llamando al onEnter() del controller asociado.
     * Esto mantiene los datos al día cuando el usuario cambia de pestaña.
     *
     * @param tab Tab actualmente visible/seleccionado
     */
    private void refreshSelectedTab(Tab tab) {
        if (tab == null) return;

        if (tab == tabBestiary) {
            callOnEnter(bestiaryController);
            return;
        }

        if (tab == tabItems) {
            callOnEnter(itemsController);
            return;
        }

        if (tab == tabAchievements) {
            callOnEnter(achievementsController);
            return;
        }

        if (tab == tabRanking) {
            callOnEnter(rankingController);
        }
    }

    /**
     * Helper seguro para invocar onEnter() sin NPE.
     * Si el controller es null, no hace nada.
     *
     * @param controller Controller que implementa ViewLifecycle
     */
    private void callOnEnter(ViewLifecycle controller) {
        if (controller != null) {
            controller.onEnter();
        }
    }

    /**
     * Handler del botón "Back" (FXML).
     * Vuelve al menú principal con fade manteniendo tamaño.
     */
    @FXML
    private void onBack() {
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }

    // =========================
    // Logging (simple y consistente)
    // =========================

    /**
     * Log de advertencia (no fatal).
     */
    private static void logWarn(String msg, String fxmlPath) {
        System.err.println("[Collection][WARN] " + msg + " -> " + fxmlPath);
    }

    /**
     * Log de error (incluye stacktrace si hay excepción).
     */
    private static void logError(String msg, String fxmlPath, Exception e) {
        System.err.println("[Collection][ERROR] " + msg + " -> " + fxmlPath);
        if (e != null) {
            e.printStackTrace();
        }
    }
}
