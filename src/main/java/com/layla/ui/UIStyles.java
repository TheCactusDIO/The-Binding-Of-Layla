package com.layla.ui;

import javafx.scene.control.DialogPane;

public final class UIStyles {
    private UIStyles() {}

    // GPT — aplica nuestras hojas de estilo y clase base al DialogPane
    public static void applyDialogStyle(DialogPane dp) {
        dp.getStyleClass().add("game-dialog");
        addStylesheet(dp, "/ui/styles/global.css");
        addStylesheet(dp, "/ui/styles/dialogs.css");
    }

    private static void addStylesheet(DialogPane dp, String resourcePath) {
        var url = UIStyles.class.getResource(resourcePath);
        if (url != null) {
            dp.getStylesheets().add(url.toExternalForm());
        } else {
            System.err.println("[UIStyles] Stylesheet not found: " + resourcePath);
        }
    }
}
