package com.malek.pos.controllers;

import com.malek.pos.database.UserDAO;
import com.malek.pos.models.User;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginController {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @FXML
    private TextField txtUsername;
    @FXML
    private PasswordField txtPassword;
    @FXML
    private PasswordField txtPin;
    @FXML
    private Label lblStatus;

    private final UserDAO userDAO = new UserDAO();
    private Stage stage;
    private Runnable onLoginSuccess;

    // We can use a static field to hold the logged in user globally for simplicity
    public static User currentUser;

    @FXML
    public void initialize() {
        Platform.runLater(() -> txtUsername.requestFocus());
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setOnLoginSuccess(Runnable onLoginSuccess) {
        this.onLoginSuccess = onLoginSuccess;
    }

    @FXML
    private void handleLogin() {
        String user = txtUsername.getText().trim();
        String pass = txtPassword.getText().trim();

        if (user.isEmpty() || pass.isEmpty()) {
            lblStatus.setText("Credentials required.");
            return;
        }

        User u = userDAO.authenticate(user, pass);
        if (u != null) {
            proceed(u);
        } else {
            lblStatus.setText("Invalid Username or Password.");
        }
    }

    @FXML
    private void handlePinLogin() {
        String pin = txtPin.getText().trim();
        if (pin.isEmpty())
            return;

        User u = userDAO.authenticateByPin(pin);
        if (u != null) {
            proceed(u);
        } else {
            lblStatus.setText("Invalid PIN Code.");
            txtPin.clear();
        }
    }

    private void proceed(User u) {
        currentUser = u;
        logger.info("Logged in as: {} ({})", u.getFullName(), u.getRoleName());
        if (onLoginSuccess != null) {
            onLoginSuccess.run();
        }
        if (stage != null)
            stage.close();
    }
}
