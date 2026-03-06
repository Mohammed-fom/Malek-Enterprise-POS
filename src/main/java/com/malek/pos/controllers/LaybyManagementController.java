package com.malek.pos.controllers;

import com.malek.pos.database.LaybyDAO;
import com.malek.pos.models.Layby;
import com.malek.pos.models.TransactionItem;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public class LaybyManagementController {

    @FXML
    private TextField txtSearch;
    @FXML
    private TableView<Layby> tblLaybys;
    @FXML
    private TableColumn<Layby, String> colId;
    @FXML
    private TableColumn<Layby, String> colCustomer;
    @FXML
    private TableColumn<Layby, BigDecimal> colTotal;
    @FXML
    private TableColumn<Layby, BigDecimal> colPaid;
    @FXML
    private TableColumn<Layby, BigDecimal> colBalance;
    @FXML
    private TableColumn<Layby, String> colStatus;
    @FXML
    private TableColumn<Layby, String> colExpiry;

    @FXML
    private TableView<TransactionItem> tblItems;
    @FXML
    private TableColumn<TransactionItem, String> colItemName;
    @FXML
    private TableColumn<TransactionItem, BigDecimal> colItemQty;

    private final LaybyDAO laybyDAO = new LaybyDAO();
    private ObservableList<Layby> masterList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupColumns();
        loadLaybys();

        // Listen for selection
        tblLaybys.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadItems(newVal);
            } else {
                tblItems.getItems().clear();
            }
        });
    }

    private void setupColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("customLaybyId"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("totalAmount"));
        colPaid.setCellValueFactory(new PropertyValueFactory<>("amountPaid"));
        // Custom cell for Balance
        colBalance.setCellValueFactory(cell -> {
            return new javafx.beans.property.SimpleObjectProperty<>(cell.getValue().getBalance());
        });
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colExpiry.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getExpiryDate() != null ? cell.getValue().getExpiryDate().toLocalDate().toString()
                        : ""));

        colItemName.setCellValueFactory(cell -> cell.getValue().descriptionProperty());
        colItemQty.setCellValueFactory(cell -> cell.getValue().quantityProperty());
    }

    private void loadLaybys() {
        List<Layby> list = laybyDAO.getAllLaybys();
        masterList.setAll(list);
        tblLaybys.setItems(masterList);
    }

    private void loadItems(Layby layby) {
        // DAO call to get items on demand
        List<TransactionItem> items = laybyDAO.getLaybyItems(layby.getLaybyId());
        layby.setItems(items); // Populate model for printing
        tblItems.setItems(FXCollections.observableArrayList(items));
    }

    @FXML
    private void onSearch() {
        String query = txtSearch.getText().toLowerCase();
        FilteredList<Layby> filtered = new FilteredList<>(masterList, p -> {
            if (query.isEmpty())
                return true;
            return p.getCustomLaybyId().toLowerCase().contains(query) ||
                    p.getCustomerName().toLowerCase().contains(query);
        });
        tblLaybys.setItems(filtered);
    }

    @FXML
    private void onViewContactDetails() {
        Layby selected = tblLaybys.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Contact Details");
        alert.setHeaderText("Customer Contact Information");
        alert.setContentText(
                "Name: " + selected.getCustomerName() + "\n" +
                        "Phone: " + selected.getCustomerPhone() + "\n" +
                        "Address: " + selected.getCustomerAddress());
        alert.show();
    }

    @FXML
    private void onPayment() {
        Layby selected = tblLaybys.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;
        if (!"ACTIVE".equals(selected.getStatus())) {
            new Alert(Alert.AlertType.WARNING, "Layby is " + selected.getStatus()).show();
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Layby Payment");
        dialog.setHeaderText("Enter Payment Amount");
        dialog.setContentText("Amount:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(amountStr -> {
            try {
                BigDecimal amount = new BigDecimal(amountStr);
                BigDecimal balance = selected.getBalance();

                if (amount.compareTo(BigDecimal.ZERO) <= 0)
                    throw new NumberFormatException();
                if (amount.compareTo(balance) > 0) {
                    new Alert(Alert.AlertType.WARNING, "Amount exceeds balance (" + balance + ")").show();
                    return;
                }

                // Add Payment
                int userId = LoginController.currentUser != null ? LoginController.currentUser.getUserId() : 1;
                if (laybyDAO.addPayment(selected.getLaybyId(), amount, "CASH", userId)) {
                    // Check if completed
                    BigDecimal newPaid = selected.getAmountPaid().add(amount);
                    selected.setAmountPaid(newPaid); // Update UI object model

                    // Auto Print Receipt for this payment
                    com.malek.pos.utils.ReceiptService.printLaybyReceipt(selected, amount);

                    // Real-time Update
                    com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_HISTORY);
                    com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_REPORTS);

                    if (selected.getBalance().compareTo(BigDecimal.ZERO) == 0) {
                        // Already calling closeLayby via DAO? Yes DAO call above was addPayment.
                        // Ideally checking balance 0 means next step is close.
                        laybyDAO.closeLayby(selected.getLaybyId());
                        selected.setStatus("COMPLETED");

                        // Stock moves when completed?
                        // Actually stock moves out on CREATION usually for Layby (reserved).
                        // If implementation releases stock only on complete, then refresh stock.
                        // But typically layby reserves stock.
                        // Let's refresh stock anyway to be safe or if status affects availability.
                        // Let's refresh stock anyway to be safe or if status affects availability.
                        com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_STOCK);

                        new Alert(Alert.AlertType.INFORMATION, "Layby Completed! Please handover items.").show();
                    } else {
                        new Alert(Alert.AlertType.INFORMATION, "Payment Recorded. Balance: " + selected.getBalance())
                                .show();
                    }
                    tblLaybys.refresh();
                }
            } catch (NumberFormatException e) {
                new Alert(Alert.AlertType.ERROR, "Invalid Amount").show();
            }
        });
    }

    @FXML
    private void onReprintReceipt() {
        Layby selected = tblLaybys.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "Select a Layby").show();
            return;
        }
        // Reprint shows current status. Amount paid "now" is 0 as we are just
        // reprinting status
        com.malek.pos.utils.ReceiptService.printLaybyReceipt(selected, BigDecimal.ZERO);
    }

    @FXML
    private void onCancelLayby() {
        Layby selected = tblLaybys.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "Select a Layby").show();
            return;
        }

        if (!"ACTIVE".equals(selected.getStatus())) {
            new Alert(Alert.AlertType.WARNING, "Cannot cancel. Status is " + selected.getStatus()).show();
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Cancel Layby");
        alert.setHeaderText("Cancel Layby " + selected.getCustomLaybyId() + "?");
        alert.setContentText("This will mark the layby as CANCELLED and return items to stock.\n\nAre you sure?");

        alert.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                int userId = com.malek.pos.controllers.LoginController.currentUser != null
                        ? com.malek.pos.controllers.LoginController.currentUser.getUserId()
                        : 1;
                laybyDAO.cancelLayby(selected.getLaybyId(), userId);
                new Alert(Alert.AlertType.INFORMATION, "Layby Cancelled. Stock restored.").show();
                loadLaybys();
                com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_STOCK);
                com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_REPORTS);
            }
        });
    }

    @FXML
    private void onClose() {
        tblLaybys.getScene().getWindow().hide();
    }
}
