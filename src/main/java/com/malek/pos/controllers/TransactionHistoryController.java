package com.malek.pos.controllers;

import com.malek.pos.database.TransactionDAO;
import com.malek.pos.models.Transaction;
import com.malek.pos.models.TransactionItem;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;
import javafx.stage.Modality;
import java.io.IOException;

public class TransactionHistoryController {

    // Master Table
    @FXML
    private TableView<Transaction> tblTransactions;
    @FXML
    private TableColumn<Transaction, Integer> colId;
    @FXML
    private TableColumn<Transaction, String> colCustomId;
    @FXML
    private TableColumn<Transaction, String> colDate;
    @FXML
    private TableColumn<Transaction, String> colType;
    @FXML
    private TableColumn<Transaction, String> colDebtor;
    @FXML
    private TableColumn<Transaction, BigDecimal> colTotal;
    @FXML
    private TableColumn<Transaction, String> colStatus;

    // Filters
    @FXML
    private DatePicker dpStartDate;
    @FXML
    private DatePicker dpEndDate;
    @FXML
    private ComboBox<String> cmbType;
    @FXML
    private TextField txtSearch;

    // Detail View
    @FXML
    private Label lblDetailId;
    @FXML
    private Label lblDetailDate;
    @FXML
    private Label lblDetailCustomer;
    @FXML
    private TableView<TransactionItem> tblDetailItems;
    @FXML
    private TableColumn<TransactionItem, String> colDetailProduct;
    @FXML
    private TableColumn<TransactionItem, BigDecimal> colDetailQty;
    @FXML
    private TableColumn<TransactionItem, BigDecimal> colDetailPrice;
    @FXML
    private TableColumn<TransactionItem, BigDecimal> colDetailTotal;

    @FXML
    private Label lblDetailSubtotal;
    @FXML
    private Label lblDetailTax;
    @FXML
    private Label lblDetailTotal;

    @FXML
    private Button btnReprint;
    @FXML
    private Button btnRefund;

    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final ObservableList<Transaction> transactionList = FXCollections.observableArrayList();
    private FilteredList<Transaction> filteredData;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ObservableList<TransactionItem> detailItems = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupMasterTable();
        setupDetailTable();
        setupFilters();

        loadData();

        // Subscribe to refresh events
        // Subscribe to refresh events
        com.malek.pos.utils.EventBus.subscribe(com.malek.pos.utils.EventBus.REFRESH_HISTORY, e -> loadData());

        // Default Sort: Newest First
        colId.setSortType(TableColumn.SortType.DESCENDING);
        tblTransactions.getSortOrder().add(colId);
        tblTransactions.sort();
    }

    private void setupMasterTable() {
        colId.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getTransactionId()));
        colCustomId.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCustomTransactionId()));
        colDate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTransactionDate().format(dtf)));
        colType.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTransactionType()));
        colDebtor.setCellValueFactory(c -> {
            Integer did = c.getValue().getDebtorId();
            return new SimpleStringProperty(did != null && did != 0 ? "Debtor #" + did : "Cash/Walk-in");
        });
        colTotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getGrandTotal()));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));

        // Selection Listener
        tblTransactions.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            loadTransactionDetails(newVal);
        });

        // Double Click Listener for Preview
        tblTransactions.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Transaction selected = tblTransactions.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    showPreview(selected);
                }
            }
        });
    }

    private void setupDetailTable() {
        tblDetailItems.setItems(detailItems);
        colDetailProduct.setCellValueFactory(c -> c.getValue().descriptionProperty());
        colDetailQty.setCellValueFactory(c -> c.getValue().quantityProperty());
        colDetailPrice.setCellValueFactory(c -> c.getValue().unitPriceProperty());
        colDetailTotal.setCellValueFactory(c -> c.getValue().totalProperty());
    }

    private void setupFilters() {
        // Defaults
        dpStartDate.setValue(LocalDate.now()); // Default to today to avoid huge list initially? Or maybe last 30 days?
        // Let's default to Last 3 Days to show recent activity
        dpStartDate.setValue(LocalDate.now().minusDays(3));
        dpEndDate.setValue(LocalDate.now());

        cmbType.setItems(FXCollections.observableArrayList("ALL", "SALE", "REFUND", "QUOTE"));
        cmbType.getSelectionModel().select("ALL");

        filteredData = new FilteredList<>(transactionList, p -> true);

        // Add Listeners
        dpStartDate.valueProperty().addListener((obs, o, n) -> updateFilter());
        dpEndDate.valueProperty().addListener((obs, o, n) -> updateFilter());
        cmbType.valueProperty().addListener((obs, o, n) -> updateFilter());
        txtSearch.textProperty().addListener((obs, o, n) -> updateFilter());

        // Wrap in SortedList to enable sorting
        SortedList<Transaction> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(tblTransactions.comparatorProperty());
        tblTransactions.setItems(sortedData);
    }

    private void updateFilter() {
        filteredData.setPredicate(txn -> {
            // 1. Date Range
            LocalDate txnDate = txn.getTransactionDate().toLocalDate();
            if (dpStartDate.getValue() != null && txnDate.isBefore(dpStartDate.getValue())) {
                return false;
            }
            if (dpEndDate.getValue() != null && txnDate.isAfter(dpEndDate.getValue())) {
                return false;
            }

            // 2. Type
            String type = cmbType.getValue();
            if (type != null && !"ALL".equals(type)) {
                if (!type.equalsIgnoreCase(txn.getTransactionType())) {
                    return false;
                }
            }

            // 3. Text Search
            String lowerCaseFilter = txtSearch.getText() == null ? "" : txtSearch.getText().toLowerCase();
            if (!lowerCaseFilter.isEmpty()) {
                boolean matches = false;
                if (txn.getCustomTransactionId() != null
                        && txn.getCustomTransactionId().toLowerCase().contains(lowerCaseFilter))
                    matches = true;
                else if (String.valueOf(txn.getTransactionId()).contains(lowerCaseFilter))
                    matches = true;
                else if (txn.getGrandTotal() != null && txn.getGrandTotal().toString().contains(lowerCaseFilter))
                    matches = true;
                // Add more fields if needed

                if (!matches)
                    return false;
            }

            return true;
        });
    }

    @FXML
    private void onResetFilters() {
        dpStartDate.setValue(LocalDate.now().minusDays(3));
        dpEndDate.setValue(LocalDate.now());
        cmbType.getSelectionModel().select("ALL");
        txtSearch.clear();
    }

    private void loadData() {
        transactionList.setAll(transactionDAO.getAllTransactions());
        updateFilter(); // Re-apply current filters
    }

    private void loadTransactionDetails(Transaction txn) {
        if (txn == null) {
            clearDetails();
            return;
        }

        lblDetailId.setText(txn.getCustomTransactionId() != null ? txn.getCustomTransactionId()
                : String.valueOf(txn.getTransactionId()));
        lblDetailDate.setText(txn.getTransactionDate().format(dtf));
        lblDetailCustomer.setText(
                txn.getDebtorId() != null && txn.getDebtorId() != 0 ? "Debtor #" + txn.getDebtorId() : "Walk-in");

        lblDetailSubtotal.setText(formatMoney(txn.getSubtotal()));
        lblDetailTax.setText(formatMoney(txn.getTaxTotal()));
        lblDetailTotal.setText(formatMoney(txn.getGrandTotal()));

        // Enable Reprint
        btnReprint.setDisable(false);

        // Fetch Items
        List<TransactionItem> items = transactionDAO.getTransactionItems(txn.getTransactionId());
        detailItems.setAll(items);
    }

    private void clearDetails() {
        lblDetailId.setText("-");
        lblDetailDate.setText("-");
        lblDetailCustomer.setText("-");
        lblDetailSubtotal.setText("0.00");
        lblDetailTax.setText("0.00");
        lblDetailTotal.setText("0.00");
        detailItems.clear();
        btnReprint.setDisable(true);
    }

    private String formatMoney(BigDecimal amount) {
        return amount != null ? String.format("%.2f", amount) : "0.00";
    }

    @FXML
    private void onRefresh() {
        loadData();
    }

    @FXML
    private void onReprint() {
        Transaction selected = tblTransactions.getSelectionModel().getSelectedItem();
        if (selected != null) {
            // Ensure items are attached to the transaction object for printing
            selected.setItems(new java.util.ArrayList<>(detailItems));

            com.malek.pos.utils.ReceiptService.printReceipt(selected);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Reprint");
            alert.setHeaderText("Receipt Reprinted");
            alert.setContentText("Receipt requested from printer.");
            alert.showAndWait();
        }
    }

    @FXML
    private void onClose() {
        tblTransactions.getScene().getWindow().hide();
    }

    public void setSearch(String query) {
        if (txtSearch != null) {
            txtSearch.setText(query);
        }
    }

    private void showPreview(Transaction txn) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/receipt_preview.fxml"));
            Parent root = loader.load();

            ReceiptPreviewController controller = loader.getController();
            // Ensure items are fetched if lazy loaded? They seem to be fetched in
            // loadTransactionDetails via DAO but
            // txn object in table might not have them if it was from getAllTransactions
            // (summary).
            // Let's verify. transactionDAO.getAllTransactions() usually returns summary.
            // We should fetch items to be safe.
            List<TransactionItem> items = transactionDAO.getTransactionItems(txn.getTransactionId());
            txn.setItems(items);

            controller.setTransaction(txn);

            Stage stage = new Stage();
            stage.setTitle("Receipt Preview - "
                    + (txn.getCustomTransactionId() != null ? txn.getCustomTransactionId() : txn.getTransactionId()));
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Could not open preview: " + e.getMessage()).show();
        }
    }
}
