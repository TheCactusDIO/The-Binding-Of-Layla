package com.layla.ui;

import java.io.IOException;
import java.util.function.Consumer;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

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
    // CONTROLADORES HIJOS
    // =========================
    // Guardamos referencias para poder llamar a onEnter() cuando el usuario cambie de pestaña.

    private BestiaryController bestiaryController;
    private ItemsController itemsController;
    private AchievementsController achievementsController;
    private RankingController rankingController;

    @FXML
    private void initialize() {
        // Cargamos el contenido de cada Tab por FXML para:
        // 1) Tener el UI embebido dentro del TabPane.
        // 2) Capturar el controller y poder refrescar al cambiar de pestaña.
        loadTab(tabBestiary, "ui/bestiary.fxml", c -> {
            if (c instanceof BestiaryController bc) bestiaryController = bc;
        });

        loadTab(tabItems, "ui/items.fxml", c -> {
            if (c instanceof ItemsController ic) itemsController = ic;
        });

        loadTab(tabAchievements, "ui/achievements.fxml", c -> {
            if (c instanceof AchievementsController ac) achievementsController = ac;
        });

        loadTab(tabRanking, "ui/ranking.fxml", c -> {
            if (c instanceof RankingController rc) rankingController = rc;
        });

        // Al cambiar de pestaña, refrescamos la vista visible.
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                refreshTab(newTab);
            }
        });
    }

    /**
     * Carga un FXML dentro de un Tab y permite capturar su controller.
     *
     * @param tab           Tab donde se insertará el contenido.
     * @param fxmlPath      Ruta relativa dentro de resources (ej: "ui/bestiary.fxml").
     * @param onController  Callback opcional para capturar el controller.
     */
    private void loadTab(Tab tab, String fxmlPath, Consumer<Object> onController) {
        if (tab == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/" + fxmlPath));
            Parent content = loader.load();
            tab.setContent(content);

            if (onController != null) {
                onController.accept(loader.getController());
            }

        } catch (IOException e) {
            System.err.println("[Collection] Error cargando pestaña " + fxmlPath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onEnter() {
        // Al abrir la pantalla, refrescamos la pestaña actualmente seleccionada.
        Tab current = tabPane != null ? tabPane.getSelectionModel().getSelectedItem() : null;
        if (current != null) {
            refreshTab(current);
        }
    }

    /**
     * Refresca la pestaña indicada llamando al onEnter() del controller correspondiente.
     * Esto asegura que las stats/estado se actualicen al cambiar de pestaña.
     */
    private void refreshTab(Tab tab) {
        if (tab == tabBestiary && bestiaryController != null) {
            bestiaryController.onEnter();
        } else if (tab == tabItems && itemsController != null) {
            itemsController.onEnter();
        } else if (tab == tabAchievements && achievementsController != null) {
            achievementsController.onEnter();
        } else if (tab == tabRanking && rankingController != null) {
            rankingController.onEnter();
        }
    }

    @FXML
    private void onBack() {
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }
}
