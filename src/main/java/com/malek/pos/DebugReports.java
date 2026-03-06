package com.malek.pos;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class DebugReports extends Application {

    @Override
    public void start(Stage stage) {
        try {
            System.out.println("Attempting to load reports_screen.fxml...");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/reports_screen.fxml"));
            Parent root = loader.load();
            System.out.println("Successfully loaded FXML!");

            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            System.err.println("CRITICAL FXML ERROR:");
            e.printStackTrace();
            try (java.io.PrintWriter pw = new java.io.PrintWriter("error.log")) {
                e.printStackTrace(pw);
            } catch (java.io.FileNotFoundException ex) {
            }
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
