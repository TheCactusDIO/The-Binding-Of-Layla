package com.layla;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        stage.setTitle("The Binding of Layla");
        stage.setScene(new Scene(new Label("Proyecto base configurado correctamente ✅"), 420, 200));
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
