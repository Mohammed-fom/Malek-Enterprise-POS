package com.malek.pos.controllers;

import com.malek.pos.database.TransactionDAO;
import com.malek.pos.models.Transaction;
import com.malek.pos.utils.ReceiptService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class QuotesController {

    @FXML
    private TableView<Transaction> tblQuotes;
    @FXML
    private TableColumn<Transaction, Integer> colId;
    @FXML
    private TableColumn<Transaction, String> colDate;
    @FXML
    private TableColumn<Transaction, String> colCustomer;
    @FXML
    private TableColumn<Transaction, BigDecimal> colTotal;
    @FXML
    private TableColumn<Transaction, String> colStatus;

    private final TransactionDAO transactionDAO = new TransactionDAO();
    private Consumer<Transaction> onQuoteSelected;
    private Stage dialogStage;

    @FXML
    public void initialize() {
        setupTable();
        refreshQuotes();
    }

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    public void setOnQuoteSelected(Consumer<Transaction> onQuoteSelected) {
        this.onQuoteSelected = onQuoteSelected;
    }

    private void setupTable() {
        colId.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getTransactionId()));
        colDate.setCellValueFactory(c -> {
            if (c.getValue().getTransactionDate() != null) {
                return new SimpleStringProperty(
                        c.getValue().getTransactionDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            }
            return new SimpleStringProperty("");
        });
        colCustomer.setCellValueFactory(c -> {
            // Transaction model might not have customer name pre-joined.
            // In a real app we'd join in SQL. For now we might just have ID or simple name
            // if available.
            // Checking if Transaction has DebtorName... Assuming it does via DAO join or we
            // might need to fetch.
            // Let's assume TransactionDAO.getOpenQuotes() populates debtor name or we just
            // show ID for now if name missing.
            // Ideally Transaction model has getCustomerName() or similar if DAO joins.
            // Let's use a placeholder or ID if name unavailable.
            return new SimpleStringProperty(
                    c.getValue().getDebtorId() != null ? "Acc #" + c.getValue().getDebtorId() : "Walk-in");
        });
        colTotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getGrandTotal()));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
    }

    private void refreshQuotes() {
        tblQuotes.setItems(FXCollections.observableArrayList(transactionDAO.getOpenQuotes()));
    }

    @FXML
    private void onLoad() {
        Transaction selected = tblQuotes.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;

        if (onQuoteSelected != null) {
            // Need to fetch items if not already loaded?
            // DAO.getOpenQuotes might return light objects.
            // Fetch full items just in case.
            selected.setItems(transactionDAO.getTransactionItems(selected.getTransactionId()));
            onQuoteSelected.accept(selected);
        }
        if (dialogStage != null)
            dialogStage.close();
    }

    @FXML
    private void onReprint() {
        Transaction selected = tblQuotes.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;

        // Fetch items for print
        selected.setItems(transactionDAO.getTransactionItems(selected.getTransactionId()));
        ReceiptService.printReceipt(selected);
    }

    @FXML
    private void onDelete() {
        Transaction selected = tblQuotes.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;

        Alert alert = new Alert(AlertType.CONFIRMATION, "Delete Quote #" + selected.getTransactionId() + "?",
                ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(r -> {
            if (r == ButtonType.YES) {
                transactionDAO.updateTransactionStatus(selected.getTransactionId(), "DELETED");
                refreshQuotes();
            }
        });
    }

    @FXML
    private void onClose() {
        if (dialogStage != null)
            dialogStage.close();
    }
}
