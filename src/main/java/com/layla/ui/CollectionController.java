package com.layla.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.io.IOException;

public class CollectionController implements ViewLifecycle {

    @FXML private TabPane tabPane;
    @FXML private Tab tabBestiary;
    @FXML private Tab tabItems;
    @FXML private Tab tabAchievements;

    // Controladores hijos para llamar a sus onEnter si es necesario
    private BestiaryController bestiaryController;
    private ItemsController itemsController;
    private AchievementsController achievementsController;

    @FXML
    private void initialize() {
        // Cargar contenidos dinámicamente para tener referencias a sus controladores
        loadTabContent(tabBestiary, "ui/bestiary.fxml", c -> {
            if (c instanceof BestiaryController bc) bestiaryController = bc;
        });

        loadTabContent(tabItems, "ui/items.fxml", c -> {
            if (c instanceof ItemsController ic) itemsController = ic;
        });

        loadTabContent(tabAchievements, "ui/achievements.fxml", c -> {
            if (c instanceof AchievementsController ac) achievementsController = ac;
        });

        // Listener para refrescar datos al cambiar de pestaña
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            refreshCurrentTab(newTab);
        });
    }

    private void loadTabContent(Tab tab, String fxmlPath, java.util.function.Consumer<Object> onController) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/" + fxmlPath));
            Parent content = loader.load();
            tab.setContent(content);
            if (onController != null) onController.accept(loader.getController());
        } catch (IOException e) {
            System.err.println("Error loading tab " + fxmlPath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onEnter() {
        // Refrescar la pestaña que esté activa al abrir
        refreshCurrentTab(tabPane.getSelectionModel().getSelectedItem());
    }

    private void refreshCurrentTab(Tab tab) {
        if (tab == tabBestiary && bestiaryController != null) bestiaryController.onEnter();
        else if (tab == tabItems && itemsController != null) itemsController.onEnter();
        else if (tab == tabAchievements && achievementsController != null) achievementsController.onEnter();
    }

    @FXML
    private void onBack() {
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }
}
