package com.malek.pos.controllers;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class PaymentController {

    @FXML
    private Label lblTotalDue;
    @FXML
    private TextField txtCash;
    @FXML
    private TextField txtCard;
    @FXML
    private TextField txtAccount;
    @FXML
    private TextField txtBank; // New

    @FXML
    private Label lblChange;
    @FXML
    private Button btnFinalize;

    private BigDecimal totalAmount;
    private Stage dialogStage;
    private boolean saleCompleted = false;

    // Result Holders
    private BigDecimal paidCash = BigDecimal.ZERO;
    private BigDecimal paidCard = BigDecimal.ZERO;
    private BigDecimal paidAccount = BigDecimal.ZERO;
    private BigDecimal paidBank = BigDecimal.ZERO; // New
    private BigDecimal changeOut = BigDecimal.ZERO;

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;

        // Add global key handler for F-keys in the dialog
        this.dialogStage.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F1)
                txtCash.requestFocus();
            else if (event.getCode() == KeyCode.F2)
                txtCard.requestFocus();
            else if (event.getCode() == KeyCode.F3)
                txtAccount.requestFocus();
            else if (event.getCode() == KeyCode.F4)
                txtBank.requestFocus(); // New
        });
    }

    public void setTotalAmount(BigDecimal amount, boolean allowAccount) {
        this.totalAmount = amount;
        lblTotalDue.setText(amount.setScale(2, RoundingMode.HALF_UP).toString());

        if (!allowAccount) {
            txtAccount.setDisable(true);
            txtAccount.setPromptText("N/A");
        }
    }

    @FXML
    public void initialize() {
        // Auto-calculate change on text change
        txtCash.textProperty().addListener((obs, o, n) -> calculateChange());
        txtCard.textProperty().addListener((obs, o, n) -> calculateChange());
        txtAccount.textProperty().addListener((obs, o, n) -> calculateChange());
        txtBank.textProperty().addListener((obs, o, n) -> calculateChange()); // New
    }

    private void calculateChange() {
        paidCash = parse(txtCash.getText());
        paidCard = parse(txtCard.getText());
        paidAccount = parse(txtAccount.getText());
        paidBank = parse(txtBank.getText()); // New

        BigDecimal totalPaid = paidCash.add(paidCard).add(paidAccount).add(paidBank);
        BigDecimal change = totalPaid.subtract(totalAmount);

        lblChange.setText(change.setScale(2, RoundingMode.HALF_UP).toString());
        changeOut = change;

        // Enable finalize if paid enough
        boolean enough = totalPaid.compareTo(totalAmount) >= 0;
        btnFinalize.setDisable(!enough);
    }

    private BigDecimal parse(String str) {
        try {
            if (str == null || str.trim().isEmpty())
                return BigDecimal.ZERO;
            return new BigDecimal(str.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    @FXML
    private void onFinalize() {
        saleCompleted = true;
        dialogStage.close();
    }

    @FXML
    private void onCancel() {
        dialogStage.close();
    }

    public boolean isSaleCompleted() {
        return saleCompleted;
    }

    public BigDecimal getPaidCash() {
        return paidCash;
    }

    public BigDecimal getPaidCard() {
        return paidCard;
    }

    public BigDecimal getPaidAccount() {
        return paidAccount;
    }

    public BigDecimal getPaidBank() {
        return paidBank;
    }

    public BigDecimal getChangeOut() {
        return changeOut;
    }
}
