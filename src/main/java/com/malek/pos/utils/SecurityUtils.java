package com.malek.pos.utils;

import com.malek.pos.database.UserDAO;
import com.malek.pos.models.User;
import javafx.scene.control.TextInputDialog;
import java.util.Optional;

public class SecurityUtils {

    private static final UserDAO userDAO = new UserDAO();

    /**
     * Prompts for a Supervisor PIN and verifies if the user has Admin privileges.
     * 
     * @return The Supervisor User if successful, null otherwise.
     */
    public static User requestSupervisorOverride() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Supervisor Override Required");
        dialog.setHeaderText("Restricted Action");
        dialog.setContentText("Enter Supervisor PIN:");

        // Simple masking is hard with standard TextInputDialog without hacking the
        // layout.
        // For a true POS, we'd use a custom FXML number pad.
        // For this prototype, standard text input is acceptable but we warn the user.

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String pin = result.get();
            User u = userDAO.authenticateByPin(pin);

            if (u != null && u.isAdmin()) {
                return u;
            } else {
                new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                        "Invalid PIN or insufficient privileges.").showAndWait();
            }
        }
        return null;
    }
}
