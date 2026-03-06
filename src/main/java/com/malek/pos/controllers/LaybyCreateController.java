package com.malek.pos.controllers;

import com.malek.pos.database.LaybyDAO;
import com.malek.pos.models.Layby;
import com.malek.pos.models.TransactionItem;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.util.List;

public class LaybyCreateController {

    @FXML
    private Label lblTotalAmount;
    @FXML
    private TextField txtName;
    @FXML
    private TextField txtPhone;
    @FXML
    private TextArea txtAddress;
    @FXML
    private ComboBox<Integer> cmbDuration;
    @FXML
    private TextField txtDeposit;

    private List<TransactionItem> items;
    private BigDecimal totalAmount;
    private Stage dialogStage;
    private boolean completed = false;

    @FXML
    public void initialize() {
        cmbDuration.setItems(FXCollections.observableArrayList(3, 4, 5, 6));
        cmbDuration.getSelectionModel().select(0); // Default 3
    }

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    public void setTransactionData(List<TransactionItem> items, BigDecimal totalAmount) {
        this.items = items;
        this.totalAmount = totalAmount;
        lblTotalAmount.setText(totalAmount.toString());

        // Suggest 10% deposit?
        BigDecimal suggested = totalAmount.multiply(new BigDecimal("0.10")).setScale(2, java.math.RoundingMode.HALF_UP);
        txtDeposit.setText(suggested.toString());
    }

    public boolean isCompleted() {
        return completed;
    }

    @FXML
    private void onCancel() {
        dialogStage.close();
    }

    @FXML
    private void onCreate() {
        if (txtName.getText().isEmpty() || txtPhone.getText().isEmpty() || txtAddress.getText().isEmpty()) {
            showAlert("Missing Information", "Please fill in Customer Name, Phone, and Address.");
            return;
        }

        BigDecimal deposit;
        try {
            deposit = new BigDecimal(txtDeposit.getText());
            if (deposit.compareTo(BigDecimal.ZERO) < 0 || deposit.compareTo(totalAmount) > 0)
                throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showAlert("Invalid Deposit", "Please enter a valid deposit amount.");
            return;
        }

        Layby layby = new Layby();
        layby.setCustomerName(txtName.getText());
        layby.setCustomerPhone(txtPhone.getText());
        layby.setCustomerAddress(txtAddress.getText());
        layby.setTotalAmount(totalAmount);
        layby.setDurationMonths(cmbDuration.getValue());
        layby.setItems(items);
        layby.setUserId(LoginController.currentUser != null ? LoginController.currentUser.getUserId() : 1);

        LaybyDAO dao = new LaybyDAO();
        if (dao.createLayby(layby, deposit, "CASH")) { // Default cash for deposit for now
            completed = true;
            // Update object for receipt
            layby.setAmountPaid(deposit);

            new Alert(Alert.AlertType.INFORMATION, "Layby Created Successfully! ID: " + layby.getCustomLaybyId())
                    .showAndWait();

            // Auto Print
            com.malek.pos.utils.ReceiptService.printLaybyReceipt(layby, deposit);

            // Real-time Dashboard Update
            com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_HISTORY);
            com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_STOCK);
            com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_REPORTS);

            dialogStage.close();
        } else {
            showAlert("Error", "Failed to create Layby. Check database.");
        }
    }

    private void showAlert(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Validation Error");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
