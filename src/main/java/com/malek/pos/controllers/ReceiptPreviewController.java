package com.malek.pos.controllers;

import com.malek.pos.models.Transaction;
import com.malek.pos.utils.ReceiptService;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;

public class ReceiptPreviewController {

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private Button btnPrint;

    @FXML
    private Button btnClose;

    private Transaction transaction;

    public void setTransaction(Transaction txn) {
        this.transaction = txn;
        loadPreview();
    }

    private void loadPreview() {
        if (transaction == null)
            return;

        try {
            Node previewNode = ReceiptService.createReceiptPreview(transaction);
            // Center the content inside the scroll pane using a VBox or StackPane if needed
            // But usually just setting content works.
            // The receipts are VBoxes, so they should stretch nicely or stick to width.
            scrollPane.setContent(previewNode);
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to load receipt preview: " + e.getMessage()).show();
        }
    }

    @FXML
    private void onPrint() {
        if (transaction != null) {
            ReceiptService.printReceipt(transaction);
            new Alert(Alert.AlertType.INFORMATION, "Print job sent to printer.").show();
        }
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) btnClose.getScene().getWindow();
        stage.close();
    }
}
