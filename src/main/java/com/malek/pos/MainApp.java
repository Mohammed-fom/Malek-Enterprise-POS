package com.malek.pos;

import com.malek.pos.controllers.SalesController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // JavaFX Thread Exception Handler
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> logError(e));

        // 1. License Check
        if (!com.malek.pos.utils.LicenseManager.isLicenseValid()) {
            loadLicenseScreen(stage);
            return;
        }

        // 2. Load Login Screen
        loadLoginScreen(stage);
    }

    private void loadLicenseScreen(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/license_screen.fxml"));
        Parent root = loader.load();

        com.malek.pos.controllers.LicenseController controller = loader.getController();

        Stage licenseStage = new Stage();
        licenseStage.setTitle("Activation - Malek Enterprise POS");
        licenseStage.setScene(new Scene(root));

        try {
            javafx.scene.image.Image icon = new javafx.scene.image.Image(getClass().getResourceAsStream("/logo.png"));
            licenseStage.getIcons().add(icon);
        } catch (Exception e) {
            System.out.println("Logo not found or invalid: " + e.getMessage());
        }

        controller.setStage(licenseStage);
        controller.setOnSuccess(() -> {
            try {
                loadLoginScreen(stage);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        licenseStage.show();
    }

    private void loadLoginScreen(Stage stage) throws IOException {
        FXMLLoader loginLoader = new FXMLLoader(getClass().getResource("/login.fxml"));
        Parent loginRoot = loginLoader.load();

        com.malek.pos.controllers.LoginController loginController = loginLoader.getController();

        Stage loginStage = new Stage();
        loginStage.setTitle("Login - Malek Enterprise POS");
        loginStage.setScene(new Scene(loginRoot));

        // Set Icon
        try {
            javafx.scene.image.Image icon = new javafx.scene.image.Image(getClass().getResourceAsStream("/logo.png"));
            loginStage.getIcons().add(icon);
        } catch (Exception e) {
            System.out.println("Logo not found or invalid: " + e.getMessage());
        }

        loginController.setStage(loginStage);

        // Define Success Callback
        loginController.setOnLoginSuccess(() -> {
            try {
                launchSalesScreen(stage);
            } catch (Exception e) {
                e.printStackTrace();
                new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                        "Failed to load Sales Screen: " + e.getMessage()).showAndWait();
            }
        });

        loginStage.show();
    }

    private void launchSalesScreen(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/sales_screen.fxml"));
        Parent root = loader.load();

        SalesController controller = loader.getController();

        javafx.geometry.Rectangle2D visualBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(root, visualBounds.getWidth(), visualBounds.getHeight());

        // Global Key Handling passed to Controller
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            controller.handleGlobalKeys(event);
        });

        // Set Icon
        try {
            javafx.scene.image.Image icon = new javafx.scene.image.Image(getClass().getResourceAsStream("/logo.png"));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            System.out.println("Logo not found or invalid: " + e.getMessage());
        }

        stage.setTitle("Malek Enterprise POS");
        stage.setScene(scene);
        stage.setX(visualBounds.getMinX());
        stage.setY(visualBounds.getMinY());
        stage.setWidth(visualBounds.getWidth());
        stage.setHeight(visualBounds.getHeight());
        stage.setMaximized(true);
        stage.show();
    }

    public static void main(String[] args) {
        // Global Exception Handler for non-FX threads
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            logError(e);
        });

        launch();
    }

    private static void logError(Throwable e) {
        System.err.println("CRITICAL ERROR: " + e.getMessage());
        e.printStackTrace();

        try {
            java.nio.file.Path logDir = java.nio.file.Paths.get("logs");
            if (!java.nio.file.Files.exists(logDir)) {
                java.nio.file.Files.createDirectories(logDir);
            }

            try (java.io.PrintWriter pw = new java.io.PrintWriter(
                    new java.io.FileWriter("logs/crash.log", true))) {
                pw.println("--- CRITICAL ERROR [" + java.time.LocalDateTime.now() + "] ---");
                e.printStackTrace(pw);
                pw.println("--------------------------------------------------");
            }
        } catch (Exception ioEx) {
            ioEx.printStackTrace();
        }
    }
}
