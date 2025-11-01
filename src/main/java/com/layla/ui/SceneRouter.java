package com.layla.ui;

import java.io.IOException;
import java.io.InputStream;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SceneRouter {
    private static Stage primaryStage;

    public static void init(Stage stage) {
        primaryStage = stage;
    }

    public static void go(String fxmlPath, int width, int height) {
        if (primaryStage == null) {
            throw new IllegalStateException("SceneRouter not initialized. Call SceneRouter.init(stage) first.");
        }

        try (InputStream is = SceneRouter.class.getResourceAsStream("/" + fxmlPath)) {
            if (is == null) {
                System.err.println("FXML not found: " + fxmlPath);
                return;
            }
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(SceneRouter.class.getResource("/" + fxmlPath));
            Parent root = loader.load(is);
            Scene scene = new Scene(root, width, height);
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
