package com.malek.pos.controllers;

import com.malek.pos.database.TransactionDAO;
import com.malek.pos.database.ProductDAO;
import com.malek.pos.database.DebtorDAO;
import com.malek.pos.models.Debtor;
import com.malek.pos.models.Product;
import com.malek.pos.models.Transaction;
import com.malek.pos.models.TransactionItem;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.BigDecimalStringConverter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.List;

public class RefundsController {

    @FXML
    private TextField txtSearchId;
    @FXML
    private Label lblOriginalDetails;
    @FXML
    private Label lblTotalRefund;

    @FXML
    private TableView<RefundItemViewModel> tblRefundItems;
    @FXML
    private TableColumn<RefundItemViewModel, String> colDesc;
    @FXML
    private TableColumn<RefundItemViewModel, BigDecimal> colSoldQty;
    @FXML
    private TableColumn<RefundItemViewModel, BigDecimal> colPrice;
    @FXML
    private TableColumn<RefundItemViewModel, BigDecimal> colReturnQty;
    @FXML
    private TableColumn<RefundItemViewModel, BigDecimal> colTotalRefund;

    @FXML
    private TextField txtAddItem;
    @FXML
    private ComboBox<String> cmbReason;
    @FXML
    private CheckBox chkRestock;
    @FXML
    private Button btnAccountRefund;

    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final DebtorDAO debtorDAO = new DebtorDAO();
    private final ObservableList<RefundItemViewModel> items = FXCollections.observableArrayList();
    private Transaction originalTransaction;
    private Integer selectedDebtorId = null;

    @FXML
    public void initialize() {
        cmbReason.setItems(FXCollections.observableArrayList(
                "Damaged / Defective",
                "Expired",
                "Customer Change of Mind",
                "Wrong Item Bought",
                "Other"));
        cmbReason.getSelectionModel().select(2); // Default: Change of Mind

        setupTable();
    }

    @FXML
    private void onSelectCustomer() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Select Customer");
        dialog.setHeaderText("Search for Customer (Name or Phone)");
        dialog.setContentText("Query:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(query -> {
            List<Debtor> matches = debtorDAO.searchDebtors(query);
            if (matches.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "No customers found.").show();
            } else {
                Debtor selected = null;
                if (matches.size() == 1) {
                    selected = matches.get(0);
                } else {
                    ChoiceDialog<Debtor> choice = new ChoiceDialog<>(matches.get(0), matches);
                    choice.setTitle("Select Customer");
                    choice.setHeaderText("Multiple customers found:");
                    choice.setContentText("Customer:");
                    Optional<Debtor> cResult = choice.showAndWait();
                    if (cResult.isPresent())
                        selected = cResult.get();
                }

                if (selected != null) {
                    selectedDebtorId = selected.getDebtorId();
                    lblOriginalDetails.setText("Customer: " + selected.getCustomerName());
                    btnAccountRefund.setDisable(false); // Enable account refund
                }
            }
        });
    }

    private void setupTable() {
        tblRefundItems.setItems(items);
        tblRefundItems.setEditable(true);

        colDesc.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().getOriginalItem().getProduct().getDescription()));

        // Sold Quantity (Read Only - Shows Original Qty or '-' if manual)
        colSoldQty.setCellValueFactory(c -> {
            if (c.getValue().isManual())
                return new SimpleObjectProperty<>(BigDecimal.ZERO); // Or null representation
            return new SimpleObjectProperty<>(c.getValue().getOriginalItem().quantityProperty().get());
        });

        // Price (Editable if Manual)
        colPrice.setCellValueFactory(c -> c.getValue().getOriginalItem().unitPriceProperty());
        colPrice.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colPrice.setOnEditCommit(e -> {
            if (e.getRowValue().isManual()) {
                e.getRowValue().getOriginalItem().unitPriceProperty().set(e.getNewValue());
                updateTotal();
                tblRefundItems.refresh();
            } else {
                // Prevent editing linked items price
                e.getRowValue().getOriginalItem().unitPriceProperty().set(e.getOldValue());
                new Alert(Alert.AlertType.WARNING, "Cannot edit price of linked receipt item.").show();
            }
        });

        colReturnQty.setCellValueFactory(c -> c.getValue().returnQtyProperty());
        colReturnQty.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colReturnQty.setOnEditCommit(e -> {
            RefundItemViewModel row = e.getRowValue();
            BigDecimal newVal = e.getNewValue();
            BigDecimal max = row.isManual() ? new BigDecimal("9999") : row.getOriginalItem().quantityProperty().get();

            if (newVal.compareTo(BigDecimal.ZERO) < 0 || newVal.compareTo(max) > 0) {
                row.returnQtyProperty().set(e.getOldValue());
                Alert alert = new Alert(Alert.AlertType.WARNING, "Invalid Quantity. Must be between 0 and " + max);
                alert.showAndWait();
            } else {
                row.returnQtyProperty().set(newVal);
                updateTotal();
            }
            tblRefundItems.refresh();
        });

        colTotalRefund.setCellValueFactory(c -> {
            // Bind to updates? For now simple calculation on refresh
            BigDecimal price = c.getValue().getOriginalItem().unitPriceProperty().get();
            BigDecimal qty = c.getValue().returnQtyProperty().get();
            return new SimpleObjectProperty<>(price.multiply(qty));
        });
    }

    @FXML
    private void onSearch() {
        String query = txtSearchId.getText().trim();
        if (query.isEmpty())
            return;

        items.clear();
        lblOriginalDetails.setText("Searching...");
        selectedDebtorId = null; // Reset manual selection
        btnAccountRefund.setDisable(true); // Reset

        // 1. Try Exact Custom ID
        Transaction txn = transactionDAO.findByCustomTransactionId(query);

        // 2. Try Exact ID (Numeric)
        if (txn == null && query.matches("\\d+")) {
            // We assume getTransactionItems can handle fetching by ID, but we need the
            // Transaction object.
            // We don't have findById in DAO yet (except via getAll).
            // Let's rely on getAll and stream filter strictly or improve DAO if needed.
            // For now, let's assume Custom ID is the primary way, OR we use the new Search
            // Method.
        }

        // 3. Search by Product/Barcode (or fallback to partial ID)
        if (txn == null) {
            java.util.List<Transaction> candidates = transactionDAO.findTransactionsByProduct(query);

            if (candidates.isEmpty()) {
                lblOriginalDetails.setText("No receipts found.");
                return;
            } else if (candidates.size() == 1) {
                txn = candidates.get(0);
            } else {
                // Multiple candidates -> Show Dialog
                txn = showSelectionDialog(candidates);
                if (txn == null) {
                    lblOriginalDetails.setText("Selection cancelled.");
                    return;
                }
            }
        }

        if (txn == null) {
            lblOriginalDetails.setText("Receipt not found.");
            return;
        }

        // Load Items
        originalTransaction = txn;
        lblOriginalDetails.setText("Receipt: "
                + (originalTransaction.getCustomTransactionId() != null ? originalTransaction.getCustomTransactionId()
                        : originalTransaction.getTransactionId())
                +
                " | Date: " + originalTransaction.getTransactionDate()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        java.util.List<TransactionItem> dbItems = transactionDAO
                .getTransactionItems(originalTransaction.getTransactionId());

        for (TransactionItem ti : dbItems) {
            items.add(new RefundItemViewModel(ti));
        }
        updateTotal();
        checkAccountRefundEligibility();
    }

    @FXML
    private void onAddItem() {
        String query = txtAddItem.getText().trim();
        if (query.isEmpty())
            return;

        Product p = productDAO.findByBarcodeOrCode(query);
        if (p == null) {
            java.util.List<Product> matches = productDAO.searchByDescription(query);
            if (matches.size() == 1)
                p = matches.get(0);
            else if (matches.size() > 1) {
                ChoiceDialog<Product> dialog = new ChoiceDialog<>(matches.get(0), matches);
                dialog.setTitle("Select Product");
                dialog.setHeaderText("Multiple products found:");
                p = dialog.showAndWait().orElse(null);
            }
        }

        if (p != null) {
            // Manual Add
            TransactionItem dummyItem = new TransactionItem(p, BigDecimal.ONE, false);
            // Default Qty 1, Price = Current Price
            // We set default return qty to 1
            RefundItemViewModel vm = new RefundItemViewModel(dummyItem, true);
            vm.returnQtyProperty().set(BigDecimal.ONE);

            items.add(vm);
            txtAddItem.clear();
            updateTotal();
            checkAccountRefundEligibility();
        } else {
            new Alert(Alert.AlertType.WARNING, "Product not found.").show();
        }
    }

    private void checkAccountRefundEligibility() {
        // Enable Account Refund only if linked to a transaction with a debtor
        if (originalTransaction != null && originalTransaction.getDebtorId() != null
                && originalTransaction.getDebtorId() != 0) {
            btnAccountRefund.setDisable(false);
        } else {
            btnAccountRefund.setDisable(true);
        }
    }

    @SuppressWarnings("unchecked")
    private Transaction showSelectionDialog(java.util.List<Transaction> candidates) {
        Dialog<Transaction> dialog = new Dialog<>();
        dialog.setTitle("Select Transaction");
        dialog.setHeaderText("Multiple transactions found. Please select one.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TableView<Transaction> table = new TableView<>();
        TableColumn<Transaction, String> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCustomTransactionId()));

        TableColumn<Transaction, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTransactionDate().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))));

        TableColumn<Transaction, BigDecimal> colTotal = new TableColumn<>("Total");
        colTotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getGrandTotal()));

        table.getColumns().addAll(colId, colDate, colTotal);
        table.setItems(FXCollections.observableArrayList(candidates));
        table.setPrefWidth(500);
        table.setPrefHeight(300);

        dialog.getDialogPane().setContent(table);

        dialog.setResultConverter(ButtonType -> {
            if (ButtonType == ButtonType.OK) {
                return table.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }

    private void updateTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (RefundItemViewModel item : items) {
            BigDecimal price = item.getOriginalItem().unitPriceProperty().get();
            BigDecimal qty = item.returnQtyProperty().get();
            total = total.add(price.multiply(qty));
        }
        lblTotalRefund.setText(total.setScale(2, java.math.RoundingMode.HALF_UP).toString());
    }

    @FXML
    private void onRefundCash() {
        processRefund("CASH");
    }

    @FXML
    private void onRefundCard() {
        processRefund("CARD");
    }

    @FXML
    private void onRefundAccount() {
        processRefund("ACCOUNT");
    }

    private void processRefund(String type) {
        if (items.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "No items to refund.").show();
            return;
        }

        System.out.println("Processing Refund: Type=" + type);

        BigDecimal grandTotal = new BigDecimal(lblTotalRefund.getText());
        if (grandTotal.compareTo(BigDecimal.ZERO) <= 0) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Total Refund is 0. Please enter return quantities.");
            alert.showAndWait();
            return;
        }

        // Create Refund Transaction
        Transaction refundTxn = new Transaction();
        refundTxn.setTransactionType("REFUND");
        refundTxn.setUserId(LoginController.currentUser != null ? LoginController.currentUser.getUserId() : 1);
        refundTxn.setTransactionDate(java.time.LocalDateTime.now());

        // Debtor Logic
        Integer debtorId = selectedDebtorId;
        if (debtorId == null && originalTransaction != null) {
            debtorId = originalTransaction.getDebtorId();
        }

        // Sanitize Debtor ID (0 -> null)
        if (debtorId != null && debtorId == 0)
            debtorId = null;
        refundTxn.setDebtorId(debtorId);

        if ("ACCOUNT".equals(type) && refundTxn.getDebtorId() == null) {
            new Alert(Alert.AlertType.ERROR,
                    "Cannot process ACCOUNT Refund without a linked Customer. Please Select Customer first.").show();
            return;
        }

        refundTxn.setGrandTotal(grandTotal);
        // Tax Calc
        BigDecimal taxRate = new BigDecimal("15.00");
        BigDecimal divisor = BigDecimal.ONE.add(taxRate.divide(new BigDecimal("100")));
        BigDecimal net = grandTotal.divide(divisor, 2, RoundingMode.HALF_UP);
        BigDecimal tax = grandTotal.subtract(net);

        refundTxn.setSubtotal(net);
        refundTxn.setTaxTotal(tax);
        refundTxn.setDiscountTotal(BigDecimal.ZERO);

        // Tenders
        if ("CASH".equals(type))
            refundTxn.setTenderCash(grandTotal);
        if ("CARD".equals(type))
            refundTxn.setTenderCard(grandTotal);
        if ("ACCOUNT".equals(type))
            refundTxn.setTenderAccount(grandTotal);

        // Enhancements
        refundTxn.setReturnToStock(chkRestock.isSelected());
        String reason = cmbReason.getValue();
        refundTxn.setNotes("Ref Reason: " + (reason != null ? reason : "N/A"));

        // Link to Shift
        try {
            com.malek.pos.database.ShiftDAO shiftDAO = new com.malek.pos.database.ShiftDAO();
            com.malek.pos.models.Shift openShift = shiftDAO.getCurrentOpenShift();
            refundTxn.setShiftId(openShift != null ? openShift.getShiftId() : 0);
        } catch (Exception e) {
            refundTxn.setShiftId(0);
        }

        refundTxn.setStatus("COMPLETED");

        java.util.List<TransactionItem> refundItems = new java.util.ArrayList<>();
        for (RefundItemViewModel vm : items) {
            BigDecimal qty = vm.returnQtyProperty().get();
            if (qty.compareTo(BigDecimal.ZERO) > 0) {
                TransactionItem newItem = new TransactionItem(vm.getOriginalItem().getProduct(), qty, false);
                newItem.unitPriceProperty().set(vm.getOriginalItem().unitPriceProperty().get());
                newItem.recalculate();
                refundItems.add(newItem);
            }
        }
        refundTxn.setItems(refundItems);

        System.out.println("Saving Refund Transaction...");

        if (transactionDAO.saveTransaction(refundTxn)) {
            System.out.println("Refund Saved Successfully. ID: " + refundTxn.getCustomTransactionId());

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Refund Processed Successfully!\nID: " + refundTxn.getCustomTransactionId() + "\n\nPrint Receipt?",
                    ButtonType.YES, ButtonType.NO);
            if (alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                com.malek.pos.utils.ReceiptService.printReceipt(refundTxn);
            }

            // Real-time Update
            com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_HISTORY);
            com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_STOCK);
            com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_REPORTS);

            // Clear
            items.clear();
            lblTotalRefund.setText("0.00");
            txtSearchId.clear();
            txtAddItem.clear();
            originalTransaction = null;
            lblOriginalDetails.setText("No Receipt Loaded");
            checkAccountRefundEligibility();
        } else {
            System.err.println("Failed to save refund transaction.");
            new Alert(Alert.AlertType.ERROR, "Failed to process refund. Check logs.").show();
        }
    }

    @FXML
    private void onClose() {
        txtSearchId.getScene().getWindow().hide();
    }

    // Helper VM
    public static class RefundItemViewModel {
        private final TransactionItem originalItem;
        private final SimpleObjectProperty<BigDecimal> returnQty;
        private final boolean isManual;

        public RefundItemViewModel(TransactionItem original) {
            this(original, false);
        }

        public RefundItemViewModel(TransactionItem original, boolean manual) {
            this.originalItem = original;
            this.returnQty = new SimpleObjectProperty<>(BigDecimal.ZERO);
            this.isManual = manual;
        }

        public TransactionItem getOriginalItem() {
            return originalItem;
        }

        public SimpleObjectProperty<BigDecimal> returnQtyProperty() {
            return returnQty;
        }

        public boolean isManual() {
            return isManual;
        }
    }
}
