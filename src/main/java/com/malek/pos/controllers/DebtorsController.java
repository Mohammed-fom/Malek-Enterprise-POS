package com.malek.pos.controllers;

import com.malek.pos.database.DebtorDAO;
import com.malek.pos.database.TransactionDAO;
import com.malek.pos.models.Debtor;
import com.malek.pos.models.Transaction;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class DebtorsController {

    @FXML
    private TextField txtSearch;
    @FXML
    private TableView<Debtor> tblDebtors;

    @FXML
    private TableColumn<Debtor, String> colAccount;
    @FXML
    private TableColumn<Debtor, String> colName;
    @FXML
    private TableColumn<Debtor, String> colPhone;
    @FXML
    private TableColumn<Debtor, BigDecimal> colBalance;
    @FXML
    private TableColumn<Debtor, BigDecimal> colLimit;
    @FXML
    private TableColumn<Debtor, String> colTier;

    private final DebtorDAO debtorDAO = new DebtorDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final ObservableList<Debtor> debtorList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupColumns();
        loadData();

        txtSearch.textProperty().addListener((obs, o, n) -> {
            if (n.isEmpty())
                loadData();
        });

        // Subscribe to real-time updates
        com.malek.pos.utils.EventBus.subscribe(com.malek.pos.utils.EventBus.REFRESH_DEBTORS, e -> loadData());
    }

    private void setupColumns() {
        colAccount.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAccountNo()));
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCustomerName()));
        colPhone.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPhone()));
        colBalance.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCurrentBalance()));
        colLimit.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCreditLimit()));
        colTier.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPriceTier()));
    }

    private void loadData() {
        debtorList.setAll(debtorDAO.getAllDebtors());
        tblDebtors.setItems(debtorList);
    }

    @FXML
    private void onSearch() {
        String q = txtSearch.getText().trim();
        if (!q.isEmpty()) {
            debtorList.setAll(debtorDAO.searchDebtors(q));
        } else {
            loadData();
        }
    }

    public void setSearch(String query) {
        txtSearch.setText(query);
        onSearch();
    }

    @FXML
    private void onViewStatement() {
        Debtor selected = tblDebtors.getSelectionModel().getSelectedItem();
        if (selected != null) {
            // Fetch Transactions
            com.malek.pos.database.TransactionDAO dao = new com.malek.pos.database.TransactionDAO();
            java.util.List<com.malek.pos.models.Transaction> txns = dao.getTransactionsByDebtor(selected.getDebtorId());

            StringBuilder sb = new StringBuilder();
            sb.append("STATEMENT OF ACCOUNT\n");
            sb.append("Customer: ").append(selected.getCustomerName()).append("\n");
            sb.append("Account:  ").append(selected.getAccountNo()).append("\n");
            sb.append("Date:     ").append(java.time.LocalDate.now()).append("\n");
            sb.append("------------------------------------------------\n");
            sb.append(String.format("%-12s %-10s %-12s %10s\n", "Date", "Type", "Ref", "Amount"));
            sb.append("------------------------------------------------\n");

            java.math.BigDecimal balance = java.math.BigDecimal.ZERO;
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (com.malek.pos.models.Transaction t : txns) {
                // For now, simple list. Real accounting needs strict debit/credit columns.
                sb.append(String.format("%-12s %-10s %-12s %10.2f\n",
                        t.getTransactionDate().format(dtf),
                        t.getTransactionType(),
                        (t.getCustomTransactionId() != null ? t.getCustomTransactionId() : t.getTransactionId()),
                        t.getGrandTotal()));
                // Verify if Refund subtracts? Usually Transaction Type determines sign in DB,
                // but let's assume raw total is positive.
                // Ideally we check type.
            }
            sb.append("------------------------------------------------\n");
            sb.append("Current Balance: ").append(selected.getCurrentBalance()).append("\n");

            // Print to Console / Alert for now (or use PrinterJob like Receipt)
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Statement");
            alert.setHeaderText("Statement: " + selected.getCustomerName());
            javafx.scene.control.TextArea area = new javafx.scene.control.TextArea(sb.toString());
            area.setEditable(false);
            area.setWrapText(true);
            area.setMaxWidth(Double.MAX_VALUE);
            area.setMaxHeight(Double.MAX_VALUE);
            alert.getDialogPane().setContent(area);
            alert.showAndWait();

        } else {
            showAlert("Select a debtor first.");
        }
    }

    private java.util.function.Consumer<Debtor> onDebtorSelected;

    public void setOnDebtorSelected(java.util.function.Consumer<Debtor> onDebtorSelected) {
        this.onDebtorSelected = onDebtorSelected;
    }

    @FXML
    private void onSelectForSale() {
        Debtor selected = tblDebtors.getSelectionModel().getSelectedItem();
        if (selected != null) {
            if (onDebtorSelected != null) {
                onDebtorSelected.accept(selected);
            }
            // Close window
            txtSearch.getScene().getWindow().hide();
        } else {
            showAlert("Select a debtor first.");
        }
    }

    @FXML
    private void onAddDebtor() {
        showDebtorDialog(null);
    }

    @FXML
    private void onEditDebtor() {
        Debtor selected = tblDebtors.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showDebtorDialog(selected);
        } else {
            showAlert("Please select a debtor to edit.");
        }
    }

    @FXML
    private void onPayment() {
        Debtor selected = tblDebtors.getSelectionModel().getSelectedItem();
        if (selected != null) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Receive Payment");
            dialog.setHeaderText("Process Payment for " + selected.getCustomerName());
            dialog.setContentText("Enter Amount:");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(amountStr -> {
                try {
                    BigDecimal amount = new BigDecimal(amountStr);
                    // Update balance (Payment reduces balance)
                    BigDecimal newBalance = selected.getCurrentBalance().subtract(amount);
                    if (debtorDAO.updateBalance(selected.getDebtorId(), newBalance)) {
                        // Ideally create a TRANSACTION record here too for audit
                        // For now we just update balance as requested
                        loadData();
                        showAlert("Payment processed successfully. New Balance: " + newBalance);
                    } else {
                        showAlert("Failed to update balance.");
                    }
                } catch (NumberFormatException e) {
                    showAlert("Invalid amount.");
                }
            });
        } else {
            showAlert("Please select a debtor.");
        }
    }

    @FXML
    @SuppressWarnings("unchecked")
    private void onHistory() {
        Debtor selected = tblDebtors.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Transaction History");
            dialog.setHeaderText("History for " + selected.getCustomerName());

            TableView<Transaction> table = new TableView<>();
            TableColumn<Transaction, String> coiId = new TableColumn<>("ID");
            coiId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getTransactionId())));

            TableColumn<Transaction, String> colDate = new TableColumn<>("Date");
            colDate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTransactionDate().toString()));

            TableColumn<Transaction, String> colType = new TableColumn<>("Type");
            colType.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTransactionType()));

            TableColumn<Transaction, BigDecimal> colTotal = new TableColumn<>("Total");
            colTotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getGrandTotal()));

            table.getColumns().addAll(coiId, colDate, colType, colTotal);
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

            table.getItems().setAll(transactionDAO.getTransactionsByDebtor(selected.getDebtorId()));

            dialog.getDialogPane().setContent(table);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.showAndWait();
        } else {
            showAlert("Please select a debtor.");
        }
    }

    private void showDebtorDialog(Debtor debtor) {
        Dialog<Debtor> dialog = new Dialog<>();
        dialog.setTitle(debtor == null ? "Add Debtor" : "Edit Debtor");
        dialog.setHeaderText(
                debtor == null ? "Create New Debtor Account" : "Edit Details for " + debtor.getCustomerName());

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField account = new TextField();
        TextField name = new TextField();
        TextField phone = new TextField();
        TextField email = new TextField();
        TextField limit = new TextField("0.00");
        ComboBox<String> tier = new ComboBox<>(FXCollections.observableArrayList("RETAIL", "TRADE"));
        tier.getSelectionModel().selectFirst();

        if (debtor != null) {
            account.setText(debtor.getAccountNo());
            name.setText(debtor.getCustomerName());
            phone.setText(debtor.getPhone());
            email.setText(debtor.getEmail());
            limit.setText(debtor.getCreditLimit() != null ? debtor.getCreditLimit().toString() : "0.00");
            tier.getSelectionModel().select(debtor.getPriceTier());
        }

        grid.add(new Label("Account No:"), 0, 0);
        grid.add(account, 1, 0);
        grid.add(new Label("Name:"), 0, 1);
        grid.add(name, 1, 1);
        grid.add(new Label("Phone:"), 0, 2);
        grid.add(phone, 1, 2);
        grid.add(new Label("Email:"), 0, 3);
        grid.add(email, 1, 3);
        grid.add(new Label("Credit Limit:"), 0, 4);
        grid.add(limit, 1, 4);
        grid.add(new Label("Price Tier:"), 0, 5);
        grid.add(tier, 1, 5);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                Debtor d = debtor == null ? new Debtor() : debtor;
                d.setAccountNo(account.getText());
                d.setCustomerName(name.getText());
                d.setPhone(phone.getText());
                d.setEmail(email.getText());
                try {
                    d.setCreditLimit(new BigDecimal(limit.getText()));
                } catch (Exception e) {
                    d.setCreditLimit(BigDecimal.ZERO);
                }
                d.setPriceTier(tier.getValue());
                return d;
            }
            return null;
        });

        Optional<Debtor> result = dialog.showAndWait();
        result.ifPresent(d -> {
            boolean success;
            if (debtor == null) {
                success = debtorDAO.addDebtor(d);
            } else {
                success = debtorDAO.updateDebtor(d);
            }
            if (success) {
                loadData();
                showAlert("Saved successfully.");
            } else {
                showAlert("Error saving debtor.");
            }
        });
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(msg);
        alert.show();
    }

    @FXML
    private void onClose() {
        txtSearch.getScene().getWindow().hide();
    }
}
