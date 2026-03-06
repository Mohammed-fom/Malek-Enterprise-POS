package com.malek.pos.controllers;

import com.malek.pos.utils.LicenseManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LicenseController {

    @FXML
    private TextField txtLicenseKey;

    @FXML
    private Label lblStatus;

    private Stage stage;
    private Runnable onSuccess;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setOnSuccess(Runnable onSuccess) {
        this.onSuccess = onSuccess;
    }

    @FXML
    public void initialize() {
        Platform.runLater(() -> txtLicenseKey.requestFocus());
    }

    @FXML
    private void handleActivate() {
        String key = txtLicenseKey.getText().trim();

        if (key.isEmpty()) {
            lblStatus.setText("Please enter a subscription key.");
            lblStatus.setStyle("-fx-text-fill: red;");
            return;
        }

        if (LicenseManager.validateKey(key)) {
            lblStatus.setText("License activated successfully!");
            lblStatus.setStyle("-fx-text-fill: green;");
            LicenseManager.saveLicenseKey(key);

            // Proceed to the next screen
            if (onSuccess != null) {
                onSuccess.run();
            }
            if (stage != null) {
                stage.close();
            }
        } else {
            lblStatus.setText("Invalid or expired subscription key.");
            lblStatus.setStyle("-fx-text-fill: red;");
        }
    }

    @FXML
    private void handleExit() {
        Platform.exit();
        System.exit(0);
    }
}
