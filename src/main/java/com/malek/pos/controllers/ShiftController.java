package com.malek.pos.controllers;

import com.malek.pos.database.ShiftDAO;
import com.malek.pos.models.Shift;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import java.math.BigDecimal;

public class ShiftController {

    @FXML
    private Label lblTitle;
    @FXML
    private VBox boxOpenShift;
    @FXML
    private VBox boxCloseShift;

    @FXML
    private TextField txtFloat;

    @FXML
    private TextField txtDeclaredCash;
    @FXML
    private TextField txtDeclaredCard;

    @FXML
    private Label lblStatus;

    private final ShiftDAO shiftDAO = new ShiftDAO();
    private Shift currentShift;
    private Runnable onCloseCallback;

    @FXML
    public void initialize() {
        // Check if there is an open shift
        currentShift = shiftDAO.getCurrentOpenShift();
        updateUI();
    }

    public void setOnCloseCallback(Runnable callback) {
        this.onCloseCallback = callback;
    }

    private void updateUI() {
        if (currentShift == null) {
            // No open shift -> Show Open UI
            boxOpenShift.setVisible(true);
            boxOpenShift.setManaged(true);
            boxCloseShift.setVisible(false);
            boxCloseShift.setManaged(false);
            lblTitle.setText("START SHIFT");
            txtFloat.requestFocus();
        } else {
            // Open shift exists -> Show Close UI
            boxOpenShift.setVisible(false);
            boxOpenShift.setManaged(false);
            boxCloseShift.setVisible(true);
            boxCloseShift.setManaged(true);
            lblTitle.setText("END SHIFT (Z-READ)");
            txtDeclaredCash.requestFocus();
        }
    }

    @FXML
    private void onStartShift() {
        try {
            BigDecimal floatAmt = new BigDecimal(txtFloat.getText().trim());
            int userId = LoginController.currentUser != null ? LoginController.currentUser.getUserId() : 1;
            int shiftId = shiftDAO.startShift(userId, floatAmt);
            if (shiftId > 0) {
                if (onCloseCallback != null)
                    onCloseCallback.run();
                closeWindow();
            } else {
                lblStatus.setText("Error starting shift.");
            }
        } catch (NumberFormatException e) {
            lblStatus.setText("Invalid Amount.");
        }
    }

    @FXML
    private void onEndShift() {
        try {
            if (currentShift == null)
                return;

            BigDecimal dCash = parse(txtDeclaredCash.getText());
            BigDecimal dCard = parse(txtDeclaredCard.getText());

            currentShift.setDeclaredCash(dCash);
            currentShift.setDeclaredCard(dCard);

            // Calculate System Totals
            BigDecimal sysCash = shiftDAO.getSystemCashTotal(currentShift.getShiftId());
            BigDecimal sysCard = shiftDAO.getSystemCardTotal(currentShift.getShiftId());

            currentShift.setSystemCash(sysCash);
            currentShift.setSystemCard(sysCard);

            shiftDAO.closeShift(currentShift);

            System.out.println("Shift Closed. Variance Cash: " + dCash.subtract(sysCash));

            if (onCloseCallback != null)
                onCloseCallback.run();
            closeWindow();

        } catch (Exception e) {
            e.printStackTrace();
            lblStatus.setText("Error closing shift.");
        }
    }

    private BigDecimal parse(String s) {
        try {
            return new BigDecimal(s.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private void closeWindow() {
        lblTitle.getScene().getWindow().hide();
    }
}
