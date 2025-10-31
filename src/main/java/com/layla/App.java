package com.layla;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        Label label = new Label("Proyecto base configurado correctamente ✅");
        StackPane root = new StackPane(label);
        Scene scene = new Scene(root, 420, 200);

        stage.setTitle("The Binding of Layla");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        // launch() and launch(args) are both fine; using args is conventional
        launch(args);
    }
}
