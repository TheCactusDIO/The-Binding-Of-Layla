package com.layla.ui;

import javafx.scene.control.DialogPane;

public final class UIStyles {
    private UIStyles() {}
    public static String hud()   { return UIStyles.class.getResource("/ui/styles/hud.css").toExternalForm(); }
    public static String game()  { return UIStyles.class.getResource("/ui/styles/game.css").toExternalForm(); }
    public static String global(){ return UIStyles.class.getResource("/ui/styles/global.css").toExternalForm(); }

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
