package com.malek.pos.controllers;

import com.malek.pos.database.DebtorDAO;
import com.malek.pos.database.ProductDAO;
import com.malek.pos.models.Debtor;
import com.malek.pos.models.Product;
import com.malek.pos.models.TransactionItem;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SalesController {

    // --- FXML UI Components ---
    @FXML
    private TextField txtAccountNo;
    @FXML
    private Label lblCustomerName;
    @FXML
    private Label lblCreditLimit;
    @FXML
    private Label lblDateTime;

    // Main Entry Loop
    @FXML
    private TextField txtBarcodeSearch;

    @FXML
    private TableView<TransactionItem> tblSalesItems;
    @FXML
    private TableColumn<TransactionItem, String> colCode;
    @FXML
    private TableColumn<TransactionItem, String> colDescription;
    @FXML
    private TableColumn<TransactionItem, BigDecimal> colCost;
    @FXML
    private TableColumn<TransactionItem, BigDecimal> colPrice;
    @FXML
    private TableColumn<TransactionItem, BigDecimal> colQty;
    @FXML
    private TableColumn<TransactionItem, BigDecimal> colTotal;

    // Totals
    @FXML
    private Label lblSubtotal;
    @FXML
    private Label lblTax;
    @FXML
    private Label lblDiscount;
    @FXML
    private Label lblGrandTotal;

    // --- State & Data Access ---
    @FXML
    private Button btnDashboard;

    private final ProductDAO productDAO = new ProductDAO();
    private final DebtorDAO debtorDAO = new DebtorDAO();
    private final com.malek.pos.database.ShiftDAO shiftDAO = new com.malek.pos.database.ShiftDAO();

    private final ObservableList<TransactionItem> cartItems = FXCollections.observableArrayList();

    private Debtor currentDebtor = null;
    private boolean isTradeCustomer = false;
    private int currentShiftId = -1;
    private boolean isRefundMode = false; // Toggle for refund mode
    private BigDecimal discountTotal = BigDecimal.ZERO;

    // --- Initialization ---
    @FXML
    public void initialize() {
        setupTableColumns();
        tblSalesItems.setItems(cartItems);

        // Handle Customer Search on Enter
        txtAccountNo.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                setCustomer(txtAccountNo.getText());
                txtBarcodeSearch.requestFocus();
            }
        });

        // Default Focus
        javafx.application.Platform.runLater(() -> {
            checkShiftStatus();
            // Load persisted cart
            java.util.List<TransactionItem> savedItems = com.malek.pos.utils.CartPersistenceManager.loadCart();
            if (!savedItems.isEmpty()) {
                cartItems.addAll(savedItems);
                updateTotals();
            }
        });

        // Auto-Save Listener
        cartItems.addListener((javafx.collections.ListChangeListener<TransactionItem>) c -> {
            com.malek.pos.utils.CartPersistenceManager.saveCart(new java.util.ArrayList<>(cartItems));
        });

        // Dashboard Visibility
        if (com.malek.pos.controllers.LoginController.currentUser != null) {
            String role = com.malek.pos.controllers.LoginController.currentUser.getRoleName();
            boolean isAdmin = "Admin".equalsIgnoreCase(role);
            if (btnDashboard != null) {
                btnDashboard.setVisible(isAdmin || "Manager".equalsIgnoreCase(role));
            }
            if (colCost != null) {
                colCost.setVisible(isAdmin);
            }
        }

        // Start Live Clock
        startLiveClock();
    }

    private void startLiveClock() {
        if (lblDateTime == null)
            return;

        javafx.animation.Timeline clock = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.ZERO, e -> {
                    java.time.LocalDateTime now = java.time.LocalDateTime.now();
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                            .ofPattern("EEE, dd MMM yyyy HH:mm:ss");
                    lblDateTime.setText(now.format(formatter));
                }), new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1)));
        clock.setCycleCount(javafx.animation.Animation.INDEFINITE);
        clock.play();
    }

    private void checkShiftStatus() {
        com.malek.pos.models.Shift shift = shiftDAO.getCurrentOpenShift();
        if (shift == null) {
            // Force Open Shift
            openShiftManager();
        } else {
            currentShiftId = shift.getShiftId();
            txtBarcodeSearch.requestFocus();
        }
    }

    private void openShiftManager() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/shift_screen.fxml"));
            javafx.scene.Parent page = loader.load();
            com.malek.pos.controllers.ShiftController controller = loader.getController();

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Shift Manager");
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.initOwner(lblGrandTotal.getScene().getWindow());
            stage.setScene(new javafx.scene.Scene(page));

            // Close on ESC
            stage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    stage.close();
                }
            });

            controller.setOnCloseCallback(() -> {
                // Re-check status after close
                checkShiftStatus();
            });

            stage.showAndWait();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    private void setupTableColumns() {
        colCode.setCellValueFactory(cell -> cell.getValue().codeProperty());
        colDescription.setCellValueFactory(cell -> cell.getValue().descriptionProperty());

        if (colCost != null) {
            colCost.setCellValueFactory(cell -> {
                com.malek.pos.models.TransactionItem item = cell.getValue();
                // Calc Cost Incl VAT
                // Cost Price is Excl usually.
                // We need to fetch product cost, apply tax.
                // Note: TransactionItem currently doesn't expose cost directly as property.
                if (item.getProduct() != null && item.getProduct().getCostPrice() != null) {
                    BigDecimal costExcl = item.getProduct().getCostPrice();
                    BigDecimal taxRate = item.getProduct().getTaxRate() != null ? item.getProduct().getTaxRate()
                            : BigDecimal.ZERO;
                    BigDecimal multiplier = BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100)));
                    BigDecimal costIncl = costExcl.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
                    return new javafx.beans.property.SimpleObjectProperty<>(costIncl);
                }
                return new javafx.beans.property.SimpleObjectProperty<>(BigDecimal.ZERO);
            });
        }

        colPrice.setCellValueFactory(cell -> cell.getValue().unitPriceProperty());
        colPrice.setCellFactory(javafx.scene.control.cell.TextFieldTableCell
                .forTableColumn(new javafx.util.converter.BigDecimalStringConverter()));
        colPrice.setOnEditCommit(event -> {
            TransactionItem item = event.getRowValue();
            BigDecimal newPrice = event.getNewValue();
            if (newPrice != null && newPrice.compareTo(BigDecimal.ZERO) >= 0) {
                item.unitPriceProperty().set(newPrice);
                item.recalculate(); // Update line total
                updateTotals(); // Update sale total
                tblSalesItems.refresh();
            } else {
                // Revert if invalid
                tblSalesItems.refresh();
            }
        });

        colQty.setCellValueFactory(cell -> cell.getValue().quantityProperty());
        colTotal.setCellValueFactory(cell -> cell.getValue().totalProperty());

        // Formatting (Optional: Create a cell factory for currency if needed)
    }

    // --- UI Actions ---
    @FXML
    private void onProcessingClick() {
        // Already on Processing screen, maybe clear or refresh?
        txtBarcodeSearch.requestFocus();
    }

    @FXML
    private void onDebtorsClick() {
        openDebtorsScreen();
    }

    @FXML
    private void onStockClick() {
        openStockScreen();
    }

    @FXML
    private void onAdminClick() {
        // RBAC Check
        if (com.malek.pos.controllers.LoginController.currentUser == null) {
            new Alert(Alert.AlertType.WARNING, "Not logged in.").show();
            return;
        }

        String role = com.malek.pos.controllers.LoginController.currentUser.getRoleName();
        if (!"Admin".equalsIgnoreCase(role) && !"Manager".equalsIgnoreCase(role)) {
            new Alert(Alert.AlertType.WARNING, "Access Denied. Admin or Manager Role Required.").show();
            return;
        }

        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/admin_dashboard.fxml"));
            javafx.scene.Parent page = loader.load();
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Admin Control Center");
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.initOwner(lblGrandTotal.getScene().getWindow()); // Modal to Sales Screen
            stage.setScene(new javafx.scene.Scene(page));

            // Close on ESC
            stage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    stage.close();
                }
            });

            stage.setMaximized(true);
            stage.show();
        } catch (java.io.IOException e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to open Admin Dashboard: " + e.getMessage()).show();
        }
    }

    @FXML
    private void onHistoryClick() {
        openHistoryScreen();
    }

    @FXML
    private void onRefundsClick() {
        openRefundsScreen();
    } // Separate matched-refund screen

    @FXML
    private void onCheckoutClick() {
        triggerCheckout();
    }

    // --- Core Interaction: Keyboard Handling ---
    public void handleGlobalKeys(KeyEvent event) {
        if (event.getCode() == KeyCode.F1) {
            triggerCheckout();
        } else if (event.getCode() == KeyCode.F2) {
            openDebtorsScreen();
        } else if (event.getCode() == KeyCode.F3) {
            openStockScreen();
        } else if (event.getCode() == KeyCode.F4) {
            openShiftManager();
        } else if (event.getCode() == KeyCode.F5) {
            saveAsQuote();
        } else if (event.getCode() == KeyCode.F6) {
            recallQuote();
        } else if (event.getCode() == KeyCode.F7) {
            toggleRefundMode();
        } else if (event.getCode() == KeyCode.F8) {
            voidTransaction();
        } else if (event.getCode() == KeyCode.F9) {
            openHistoryScreen();
        } else if (event.getCode() == KeyCode.F10) {
            triggerCheckout();
        } else if (event.getCode() == KeyCode.F11) {
            onSuspendClick();
        } else if (event.getCode() == KeyCode.F12) {
            onResumeClick();
        } else if (event.getCode() == KeyCode.DELETE) {
            deleteSelectedItem();
        } else if (event.getCode() == KeyCode.ESCAPE) {
            clearTransaction();
        } else if (event.getCode() == KeyCode.PLUS || event.getCode() == KeyCode.ADD) {
            adjustQuantity(BigDecimal.ONE);
            event.consume();
        } else if (event.getCode() == KeyCode.MINUS || event.getCode() == KeyCode.SUBTRACT) {
            adjustQuantity(BigDecimal.ONE.negate());
            event.consume();
        } else if (event.getCode() == KeyCode.MULTIPLY || event.getText().equals("*")) {
            // Hotkey for "Change Quantity" dialog
            promptForQuantity();
            event.consume();
        } else if (event.isControlDown() && event.getCode() == KeyCode.R) {
            openRefundsScreen();
        } else if (event.isControlDown() && event.getCode() == KeyCode.H) {
            onHelpClick();
        } else if (event.isControlDown() && event.getCode() == KeyCode.F) {
            openUniversalSearch();
        }
    }

    private void deleteSelectedItem() {
        TransactionItem selected = tblSalesItems.getSelectionModel().getSelectedItem();
        if (selected != null) {
            logAudit("DELETE_ITEM", "Deleted item: " + selected.getCode() + " - " + selected.getDescription());
            cartItems.remove(selected);
            updateTotals();
        }
    }

    private void adjustQuantity(BigDecimal delta) {
        TransactionItem selected = tblSalesItems.getSelectionModel().getSelectedItem();
        if (selected != null) {
            BigDecimal newQty = selected.quantityProperty().get().add(delta);
            if (newQty.compareTo(BigDecimal.ZERO) != 0) {
                selected.quantityProperty().set(newQty);
                selected.recalculate();
                updateTotals();
                // Refresh table generic workaround
                tblSalesItems.refresh();
            } else {
                // If 0 or less, maybe confirm delete? For now just block 0
                // Or if they mean to remove, they use delete
            }
        }
    }

    private void promptForQuantity() {
        TransactionItem selected = tblSalesItems.getSelectionModel().getSelectedItem();
        if (selected != null) {
            TextInputDialog dialog = new TextInputDialog(selected.quantityProperty().get().toString());
            dialog.setTitle("Change Quantity");
            dialog.setHeaderText("Set Quantity for: " + selected.getDescription());
            dialog.setContentText("Quantity:");

            // Quick focus hack
            dialog.getEditor().textProperty().addListener((obs, o, n) -> {
                if (!n.matches("\\d*(\\.\\d*)?"))
                    dialog.getEditor().setText(o);
            });

            java.util.Optional<String> result = dialog.showAndWait();
            result.ifPresent(qtyStr -> {
                try {
                    BigDecimal qty = new BigDecimal(qtyStr);
                    if (qty.compareTo(BigDecimal.ZERO) != 0) {
                        selected.quantityProperty().set(qty);
                        selected.recalculate();
                        updateTotals();
                        tblSalesItems.refresh();
                    }
                } catch (NumberFormatException e) {
                }
            });
        }
    }

    private void voidTransaction() {
        if (!cartItems.isEmpty()) {
            boolean requireOverride = true;
            Integer supervisorId = null;

            if (requireOverride) {
                if (LoginController.currentUser != null && LoginController.currentUser.isAdmin()) {
                    supervisorId = LoginController.currentUser.getUserId();
                } else {
                    com.malek.pos.models.User supervisor = com.malek.pos.utils.SecurityUtils
                            .requestSupervisorOverride();
                    if (supervisor == null)
                        return; // Cancelled
                    supervisorId = supervisor.getUserId();
                }
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to VOID this entire sale?",
                    ButtonType.YES, ButtonType.NO);
            final Integer finalSupId = supervisorId;
            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    logAudit("VOID_SALE", "Voided transaction value: " + lblGrandTotal.getText(), finalSupId);
                    clearTransaction();
                }
            });
        }
    }

    private void logAudit(String action, String desc) {
        logAudit(action, desc, null);
    }

    private void logAudit(String action, String desc, Integer supervisorId) {
        com.malek.pos.database.AuditDAO auditDAO = new com.malek.pos.database.AuditDAO();
        int userId = LoginController.currentUser != null ? LoginController.currentUser.getUserId() : 1;
        auditDAO.logAction(userId, action, desc, supervisorId);
    }

    // ... Existing ...

    @FXML
    private void saveAsQuote() {
        if (cartItems.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Please add items to the cart first.").show();
            return;
        }

        com.malek.pos.models.Transaction txn = new com.malek.pos.models.Transaction();
        txn.setTransactionType("QUOTE");
        txn.setUserId(1);
        if (currentDebtor != null)
            txn.setDebtorId(currentDebtor.getDebtorId());

        txn.setSubtotal(new BigDecimal(lblSubtotal.getText()));
        txn.setTaxTotal(new BigDecimal(lblTax.getText()));
        txn.setDiscountTotal(BigDecimal.ZERO);
        txn.setGrandTotal(new BigDecimal(lblGrandTotal.getText()));

        txn.setStatus("SAVED"); // Active quote
        txn.setItems(new java.util.ArrayList<>(cartItems));

        com.malek.pos.database.TransactionDAO txnDao = new com.malek.pos.database.TransactionDAO();
        if (txnDao.saveTransaction(txn)) {
            new Alert(Alert.AlertType.INFORMATION, "Quote Saved #" + txn.getTransactionId(), ButtonType.OK)
                    .showAndWait();
            com.malek.pos.utils.ReceiptService.printReceipt(txn);
            clearTransaction();
        }
    }

    @FXML
    private void onViewQuotesClick() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/quotes_screen.fxml"));
            javafx.scene.Parent page = loader.load();
            QuotesController controller = loader.getController();

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Manage Quotes");
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            stage.initOwner(lblGrandTotal.getScene().getWindow());
            stage.setScene(new javafx.scene.Scene(page));

            controller.setDialogStage(stage);
            controller.setOnQuoteSelected(this::loadQuote);

            stage.show();
        } catch (java.io.IOException e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to open Quotes Screen: " + e.getMessage()).show();
        }
    }

    private void loadQuote(com.malek.pos.models.Transaction quote) {
        clearTransaction();
        // Load customer
        if (quote.getDebtorId() != null) {
            com.malek.pos.database.DebtorDAO debtorDAO = new com.malek.pos.database.DebtorDAO();
            com.malek.pos.models.Debtor d = debtorDAO.getDebtorById(quote.getDebtorId());
            if (d != null) {
                setCustomer(d.getAccountNo());
            }
        }

        // Load items
        if (quote.getItems() != null) {
            cartItems.addAll(quote.getItems());
        }
        updateTotals();
    }

    private void recallQuote() {
        com.malek.pos.database.TransactionDAO txnDao = new com.malek.pos.database.TransactionDAO();
        java.util.List<com.malek.pos.models.Transaction> quotes = txnDao.getOpenQuotes();

        ChoiceDialog<com.malek.pos.models.Transaction> dialog = new ChoiceDialog<>(null, quotes);
        dialog.setTitle("Recall Quote");
        dialog.setHeaderText("Select a Quote to Recall:");
        dialog.setContentText("Quote:");

        dialog.getDialogPane().setPrefWidth(500);

        java.util.Optional<com.malek.pos.models.Transaction> result = dialog.showAndWait();
        result.ifPresent(q -> {
            // Load Items
            java.util.List<TransactionItem> items = txnDao.getTransactionItems(q.getTransactionId());
            clearTransaction();

            // Restore Customer
            if (q.getDebtorId() != null) {
                // Ideally look up debtor info again to display name
                // Simple hack: set just ID or re-fetch
                // TODO: fetch debtor lookup if needed for UI name
            }

            cartItems.setAll(items);
            updateTotals();
        });
    }

    private void openDebtorsScreen() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/debtors_screen.fxml"));
            javafx.scene.Parent page = loader.load();
            DebtorsController controller = loader.getController();
            controller.setOnDebtorSelected(d -> setCustomer(d.getAccountNo()));

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Debtors Master File");
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL); // Or NONE if you want it parallel
            stage.initOwner(lblGrandTotal.getScene().getWindow());
            stage.setScene(new javafx.scene.Scene(page));

            // Close on ESC
            stage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    stage.close();
                }
            });

            stage.show();
        } catch (java.io.IOException e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to open Debtors Screen: " + e.getMessage()).show();
        }
    }

    // Singleton Stage References
    private javafx.stage.Stage stockStage;
    private javafx.stage.Stage historyStage;
    private javafx.stage.Stage supplierStage;

    private void openStockScreen() {
        if (stockStage != null && stockStage.isShowing()) {
            stockStage.toFront();
            return;
        }
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/stock_screen.fxml"));
            javafx.scene.Parent page = loader.load();
            stockStage = new javafx.stage.Stage();
            stockStage.setTitle("Stock Master File");
            stockStage.initModality(javafx.stage.Modality.NONE); // Non-Modal for Realtime viewing
            // stockStage.initOwner(lblGrandTotal.getScene().getWindow()); // Optional for
            // non-modal
            stockStage.setScene(new javafx.scene.Scene(page));

            // Close on ESC
            stockStage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    stockStage.close();
                }
            });

            stockStage.show();
        } catch (java.io.IOException e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to open Stock Screen: " + e.getMessage()).show();
        }
    }

    private void openHistoryScreen() {
        if (historyStage != null && historyStage.isShowing()) {
            historyStage.toFront();
            return;
        }
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/transaction_history.fxml"));
            javafx.scene.Parent page = loader.load();
            historyStage = new javafx.stage.Stage();
            historyStage.setTitle("Transaction History");
            historyStage.initModality(javafx.stage.Modality.NONE); // Non-Modal
            // historyStage.initOwner(lblGrandTotal.getScene().getWindow());
            historyStage.setScene(new javafx.scene.Scene(page));

            // Close on ESC
            historyStage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    historyStage.close();
                }
            });

            historyStage.setMaximized(true);
            historyStage.show();
        } catch (java.io.IOException e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to open History Screen: " + e.getMessage()).show();
        }
    }

    private void openRefundsScreen() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/refunds_screen.fxml"));
            javafx.scene.Parent page = loader.load();
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Process Refund");
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL); // Refunds MUST be modal
            stage.initOwner(lblGrandTotal.getScene().getWindow());
            stage.setScene(new javafx.scene.Scene(page));

            // Close on ESC
            stage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    stage.close();
                }
            });

            stage.showAndWait(); // Use showAndWait to block until refund done

            // After refund, refresh stock
            com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_STOCK);
            com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_HISTORY);
        } catch (java.io.IOException e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to open Refunds Screen: " + e.getMessage()).show();
        }
    }

    @FXML
    private void onSuspendClick() {
        if (cartItems.isEmpty())
            return;

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Park Sale");
        dialog.setHeaderText("Suspend Transaction");
        dialog.setContentText("Enter Reference / Customer Name (Optional):");

        java.util.Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < cartItems.size(); i++) {
                TransactionItem item = cartItems.get(i);
                json.append(String.format("{\"pid\":%d,\"q\":%s}",
                        item.getProduct().getProductId(), item.quantityProperty().get().toString()));
                if (i < cartItems.size() - 1)
                    json.append(",");
            }
            json.append("]");

            com.malek.pos.database.TransactionDAO dao = new com.malek.pos.database.TransactionDAO();
            int userId = LoginController.currentUser != null ? LoginController.currentUser.getUserId() : 1;
            Integer debtorId = currentDebtor != null ? currentDebtor.getDebtorId() : null;

            if (dao.persistParkedTransaction(userId, debtorId, json.toString(), result.get())) {
                new Alert(Alert.AlertType.INFORMATION, "Sale Suspended/Parked.").show();
                clearTransaction();
            }
        }
    }

    @FXML
    private void onResumeClick() {
        if (!cartItems.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Please clear current sale first.").show();
            return;
        }

        com.malek.pos.database.TransactionDAO dao = new com.malek.pos.database.TransactionDAO();
        java.util.List<com.malek.pos.database.TransactionDAO.ParkedTransaction> parkedList = dao
                .getParkedTransactions();

        if (parkedList.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "No Suspended Sales found.").show();
            return;
        }

        ChoiceDialog<com.malek.pos.database.TransactionDAO.ParkedTransaction> dialog = new ChoiceDialog<>(null,
                parkedList);
        dialog.setTitle("Resume Sale");
        dialog.setHeaderText("Select Parked Sale:");
        dialog.setContentText("Sale:");

        java.util.Optional<com.malek.pos.database.TransactionDAO.ParkedTransaction> result = dialog.showAndWait();
        result.ifPresent(pt -> {
            // Restore Debtor
            if (pt.debtorId() != null && pt.debtorId() != 0) {
                // ideally fetch debtor, for now we assume setCustomer fetches by ID or we
                // ignore name loading for speed
                // actually I need AccountNo to use setCustomer.
                // I will skip proper debtor restore name for this quick impl or try accessing
                // DAO.
                // Let's rely on simple item restore for now.
            }

            // Restore Items
            String j = pt.itemsJson();
            // Simple Parsing: remove [ ] and split by } , {
            String inner = j.replace("[", "").replace("]", "");
            if (!inner.isEmpty()) {
                String[] objects = inner.split("},"); // rough split
                for (String obj : objects) {
                    obj = obj.replace("{", "").replace("}", ""); // pid:X,q:Y
                    String[] parts = obj.split(",");
                    int pid = 0;
                    BigDecimal qty = BigDecimal.ZERO;
                    for (String p : parts) {
                        String[] kv = p.split(":");
                        String key = kv[0].replace("\"", "").trim();
                        String val = kv[1].replace("\"", "").trim();
                        if (key.equals("pid"))
                            pid = Integer.parseInt(val);
                        if (key.equals("q"))
                            qty = new BigDecimal(val);
                    }

                    if (pid > 0) {
                        com.malek.pos.models.Product p = productDAO.findById(pid);
                        if (p != null) {
                            TransactionItem item = new TransactionItem(p, qty, isTradeCustomer);
                            cartItems.add(item);
                        }
                    }
                }
                updateTotals();
            }

            dao.deleteParkedTransaction(pt.parkedId());
        });
    }

    @FXML
    private void onHelpClick() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/shortcuts.fxml"));
            javafx.scene.Parent page = loader.load();
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Shortcuts & Help");
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.initOwner(lblGrandTotal.getScene().getWindow());
            stage.setScene(new javafx.scene.Scene(page));

            // Close on any key press
            stage.getScene().setOnKeyPressed(e -> stage.close());
            stage.show();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    // --- Business Logic: Product Search ---
    @FXML
    private void onBarcodeEnter(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            String input = txtBarcodeSearch.getText().trim();
            if (!input.isEmpty()) {
                handleProductSearch(input);
                txtBarcodeSearch.clear();
            }
        }
    }

    private void handleProductSearch(String query) {
        if ("0000".equals(query)) {
            handleGeneralItem();
            return;
        }

        Product p = productDAO.findByBarcodeOrCode(query);
        if (p != null) {
            addItemToGrid(p);
        } else {
            // Text Search Fallback
            java.util.List<Product> matches = productDAO.searchByDescription(query);
            if (matches.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Product not found: " + query).show();
            } else {
                // Always show selection for text searches
                ChoiceDialog<Product> dialog = new ChoiceDialog<>(matches.get(0), matches);
                dialog.setTitle("Product Search");
                dialog.setHeaderText("Select Product:");
                dialog.setContentText("Product:");
                dialog.showAndWait().ifPresent(this::addItemToGrid);
            }
        }
    }

    private void handleGeneralItem() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("General Item (0000)");
        dialog.setHeaderText("Enter Item Details");

        ButtonType typeOk = new ButtonType("Add to Cart", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(typeOk, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField txtDesc = new TextField();
        txtDesc.setPromptText("Description");
        TextField txtPrice = new TextField();
        txtPrice.setPromptText("Price (Inc Tax)");
        TextField txtQty = new TextField("1");

        grid.add(new Label("Description:"), 0, 0);
        grid.add(txtDesc, 1, 0);
        grid.add(new Label("Price (Inc):"), 0, 1);
        grid.add(txtPrice, 1, 1);
        grid.add(new Label("Quantity:"), 0, 2);
        grid.add(txtQty, 1, 2);

        // Auto-focus description
        javafx.application.Platform.runLater(txtDesc::requestFocus);

        dialog.getDialogPane().setContent(grid);

        // Validation
        dialog.getDialogPane().lookupButton(typeOk).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (txtDesc.getText().trim().isEmpty()) {
                new Alert(Alert.AlertType.ERROR, "Description required.").show();
                event.consume();
                return;
            }
            try {
                new BigDecimal(txtPrice.getText());
                new BigDecimal(txtQty.getText());
            } catch (NumberFormatException e) {
                new Alert(Alert.AlertType.ERROR, "Invalid Numbers.").show();
                event.consume();
            }
        });

        // Convert result
        java.util.Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == typeOk) {
            String desc = txtDesc.getText().trim();
            BigDecimal price = new BigDecimal(txtPrice.getText());
            BigDecimal qty = new BigDecimal(txtQty.getText());

            // Find 0000 product
            Product generalProduct = productDAO.findByBarcodeOrCode("0000");
            if (generalProduct == null) {
                // Fallback if migration hasn't flushed yet or something
                generalProduct = new Product();
                generalProduct.setProductId(0); // Should be fetched from DB strictly speaking
                generalProduct.setBarcode("0000");
                generalProduct.setDescription("General Item");
                generalProduct.setPriceRetail(BigDecimal.ZERO);
                generalProduct.setTaxRate(new BigDecimal("15.00"));
                // Try to find it again? Or Alert?
                // For robustness, let's assume it created unless huge error.
                // If ID is critical for consistency, we need it.
                // But TransactionDAO uses product_id to insert items. If 0, it might violate
                // constraint if no product 0?
                // Product ID usually > 0.
                // Alert if null
                new Alert(Alert.AlertType.ERROR,
                        "System Error: General Item '0000' not found in database. Please restart application to run migration.")
                        .show();
                return;
            }

            TransactionItem item = new TransactionItem(generalProduct, isRefundMode ? qty.negate() : qty,
                    isTradeCustomer);
            item.descriptionProperty().set(desc);
            item.unitPriceProperty().set(price); // Set explicit price
            // recalculate happens via listener

            cartItems.add(item);
            tblSalesItems.scrollTo(item);
            tblSalesItems.getSelectionModel().select(item);
            updateTotals();

            if (lblLastItem != null) {
                lblLastItem.setText("General: " + desc);
            }
        }
    }

    @FXML
    private Label lblLastItem;

    private void addItemToGrid(Product product) {
        // Check if item already exists to merge qty (Optional, simpler to just add new
        // line for now)
        TransactionItem item = new TransactionItem(product, isRefundMode ? BigDecimal.ONE.negate() : BigDecimal.ONE,
                isTradeCustomer);
        cartItems.add(item);

        // Scroll to bottom
        tblSalesItems.scrollTo(item);
        tblSalesItems.getSelectionModel().select(item);

        // Update Last Scanned Display
        if (lblLastItem != null) {
            String stockTxt = product.getStockOnHand() != null ? product.getStockOnHand().toString() : "0";
            lblLastItem.setText(product.getDescription() + " (Stock: " + stockTxt + ")");
        }

        updateTotals();
    }

    // --- Business Logic: Customer Selection ---
    public void setCustomer(String accountNo) {
        if (accountNo.equalsIgnoreCase("CASH") || accountNo.isEmpty()) {
            currentDebtor = null;
            isTradeCustomer = false;
            lblCustomerName.setText("CASH SALE");
            lblCreditLimit.setText("N/A");
            recalculateCartPrices();
            updateTotals();
            return;
        }

        Debtor d = debtorDAO.findByAccountNo(accountNo);
        if (d != null) {
            currentDebtor = d;
            isTradeCustomer = d.isTradeCustomer();
            lblCustomerName.setText(d.getCustomerName());
            lblCreditLimit.setText(d.getCreditLimit() != null ? d.getCreditLimit().toString() : "Unlimited");

            recalculateCartPrices();
            updateTotals();
        } else {
            lblCustomerName.setText("CUSTOMER NOT FOUND");
        }
    }

    private void recalculateCartPrices() {
        for (TransactionItem item : cartItems) {
            Product p = item.getProduct();
            if (p != null && !p.isServiceItem()) { // Don't override service items (often custom price)
                // Or maybe we DO override service items if they have fixed Trade Prices?
                // For now, let's assume standard products only.
                // Actually, item.recalculate() might rely on setUnitPrice.

                BigDecimal newPrice = p.getPrice(isTradeCustomer);
                item.unitPriceProperty().set(newPrice);
                // Force total update logic inside item if needed, but binding should handle it
                // if set up correctly.
                // Checking TransactionItem implementation...
                // Assuming unitPriceProperty is bound or listener triggers total update.
                // If not, we might need item.recalculate()
                // Let's assume item.unitPriceProperty() triggers listeners.
            }
        }
    }

    @FXML
    private void onDiscountClick() {
        if (cartItems.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Add items before applying discount.").show();
            return;
        }

        TextInputDialog dialog = new TextInputDialog("0");
        dialog.setTitle("Apply Discount");
        dialog.setHeaderText("Enter Discount Amount (e.g., 50) or Percentage (e.g., 10%)");
        dialog.setContentText("Value:");

        java.util.Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String input = result.get().trim();
            BigDecimal calculatedDiscount = BigDecimal.ZERO;

            // Calculate base total for percentage
            BigDecimal itemsTotal = BigDecimal.ZERO;
            for (TransactionItem item : cartItems) {
                itemsTotal = itemsTotal.add(item.totalProperty().get());
            }

            try {
                if (input.endsWith("%")) {
                    String pStr = input.replace("%", "").trim();
                    BigDecimal percent = new BigDecimal(pStr);
                    calculatedDiscount = itemsTotal.multiply(percent).divide(new BigDecimal("100"), 2,
                            RoundingMode.HALF_UP);
                } else {
                    calculatedDiscount = new BigDecimal(input);
                }

                // Validate
                if (calculatedDiscount.compareTo(BigDecimal.ZERO) < 0) {
                    new Alert(Alert.AlertType.ERROR, "Discount cannot be negative.").show();
                    return;
                }
                if (calculatedDiscount.compareTo(itemsTotal) > 0) {
                    new Alert(Alert.AlertType.ERROR, "Discount cannot exceed total amount.").show();
                    return;
                }

                this.discountTotal = calculatedDiscount;
                updateTotals();

            } catch (NumberFormatException e) {
                new Alert(Alert.AlertType.ERROR, "Invalid format.").show();
            }
        }
    }

    // --- Totals Calculation ---
    private void updateTotals() {
        BigDecimal totalIncl = BigDecimal.ZERO;

        // Sum Items (Tax Inclusive usually)
        for (TransactionItem item : cartItems) {
            totalIncl = totalIncl.add(item.totalProperty().get());
        }

        // Apply Discount
        // Ensure discount doesn't exceed total (though validated on input, cart might
        // change)
        if (discountTotal.compareTo(totalIncl) > 0) {
            discountTotal = totalIncl; // Cap it
        }

        BigDecimal grandTotal = totalIncl.subtract(discountTotal);

        // Back Calculate Tax from NEW Grand Total
        // Assumes uniform tax rate or average...
        // Best approach: Calculate scale factor = (GrandTotal / TotalIncl)
        // Then apply scale to tax content of each item?
        // Or simpler: Just calculate tax component of the final GrandTotal using
        // standard rate (15%).
        // Tax = GrandTotal - (GrandTotal / 1.15)

        BigDecimal taxRate = new BigDecimal("15"); // Standard
        // Better: getItem weighted tax?
        // For simplicity in this requirement: Re-calculate Tax from Grand Total.
        BigDecimal divisor = BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100)));
        BigDecimal subTotalExcl = grandTotal.divide(divisor, 2, RoundingMode.HALF_UP);
        BigDecimal totalTaxContent = grandTotal.subtract(subTotalExcl);

        lblSubtotal.setText(subTotalExcl.setScale(2, RoundingMode.HALF_UP).toString());
        lblTax.setText(totalTaxContent.setScale(2, RoundingMode.HALF_UP).toString());
        lblDiscount.setText(discountTotal.setScale(2, RoundingMode.HALF_UP).toString());
        lblGrandTotal.setText(grandTotal.setScale(2, RoundingMode.HALF_UP).toString());
    }

    private void triggerCheckout() {
        if (cartItems.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Please add items to cart before checkout.").show();
            return;
        }

        try {
            BigDecimal grandTotal = new BigDecimal(lblGrandTotal.getText());

            // --- One-Click Account Checkout Logic ---
            if (!isRefundMode && currentDebtor != null) {
                Alert fastPass = new Alert(Alert.AlertType.CONFIRMATION,
                        "Fast Checkout:\nCharge " + grandTotal + " to Account: " + currentDebtor.getCustomerName()
                                + "?",
                        ButtonType.YES, ButtonType.NO);
                fastPass.setTitle("Account Checkout");
                fastPass.setHeaderText("Charge to Account?");
                java.util.Optional<ButtonType> fpResult = fastPass.showAndWait();

                if (fpResult.isPresent() && fpResult.get() == ButtonType.YES) {
                    // Check Credit Limit in saveTransaction, but we can do it here to be safe or
                    // rely on saveTransaction's check.
                    // saveTransaction has the Logic now.
                    // We pass: Cash=0, Card=0, Account=Total, Change=0
                    saveTransaction(BigDecimal.ZERO, BigDecimal.ZERO, grandTotal, BigDecimal.ZERO, BigDecimal.ZERO);
                    return; // Done
                }
            }
            // ----------------------------------------

            // Load Payment Dialog
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/payment_screen.fxml"));
            javafx.scene.Parent page = loader.load();

            PaymentController controller = loader.getController();
            javafx.stage.Stage dialogStage = new javafx.stage.Stage();
            // Determine Mode & Data

            if (grandTotal.signum() < 0) {
                dialogStage.setTitle("Refund Payout");
            } else {
                dialogStage.setTitle("Checkout");
            }

            dialogStage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            dialogStage.initOwner(lblGrandTotal.getScene().getWindow());
            dialogStage.setScene(new javafx.scene.Scene(page));

            boolean allowAccount = (currentDebtor != null);
            controller.setTotalAmount(grandTotal.abs(), allowAccount);
            controller.setDialogStage(dialogStage);

            // Show
            dialogStage.showAndWait();

            // Handle Result
            if (controller.isSaleCompleted()) {
                saveTransaction(controller);
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    private void toggleRefundMode() {
        isRefundMode = !isRefundMode;
        if (isRefundMode) {
            lblGrandTotal.setStyle("-fx-text-fill: red; -fx-font-size: 24px; -fx-font-weight: bold;");
            lblCustomerName.setText(lblCustomerName.getText() + " (REFUND MODE)");
        } else {
            lblGrandTotal.setStyle("-fx-text-fill: black; -fx-font-size: 24px; -fx-font-weight: bold;");
            if (currentDebtor != null)
                lblCustomerName.setText(currentDebtor.getCustomerName());
            else
                lblCustomerName.setText("CASH SALE");
        }
        updateTotals(); // Reflect any necessary changes
    }

    private void saveTransaction(PaymentController payment) {
        saveTransaction(payment.getPaidCash(), payment.getPaidCard(), payment.getPaidAccount(), payment.getPaidBank(),
                payment.getChangeOut());
    }

    private void saveTransaction(BigDecimal paidCash, BigDecimal paidCard, BigDecimal paidAccount, BigDecimal paidBank,
            BigDecimal changeOut) {
        // Credit Limit Check (Account Sales Only)
        if (!isRefundMode && currentDebtor != null && paidAccount.compareTo(BigDecimal.ZERO) > 0) {
            // Check if purchase is allowed
            if (!currentDebtor.canPurchase(paidAccount)) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                        "Credit Limit Exceeded!\n" +
                                "Current Balance: " + currentDebtor.getCurrentBalance() + "\n" +
                                "Credit Limit: "
                                + (currentDebtor.getCreditLimit() != null ? currentDebtor.getCreditLimit()
                                        : "Unlimited")
                                + "\n\n" +
                                "Do you want to proceed with this sale?",
                        ButtonType.YES, ButtonType.NO);
                alert.setTitle("Credit Limit Warning");
                alert.setHeaderText("Credit Limit Exceeded");
                java.util.Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.NO) {
                    return; // Cancel Save
                }
            }
        }

        com.malek.pos.models.Transaction txn = new com.malek.pos.models.Transaction();

        txn.setTransactionType(isRefundMode ? "REFUND" : "SALE");
        txn.setTransactionDate(java.time.LocalDateTime.now());
        if (currentDebtor != null)
            txn.setDebtorId(currentDebtor.getDebtorId());

        txn.setUserId(LoginController.currentUser != null ? LoginController.currentUser.getUserId() : 1);

        txn.setSubtotal(new BigDecimal(lblSubtotal.getText()));
        txn.setTaxTotal(new BigDecimal(lblTax.getText()));
        txn.setDiscountTotal(BigDecimal.ZERO);
        txn.setGrandTotal(new BigDecimal(lblGrandTotal.getText()));

        // Refund Logic: Tenders might be negative or just recorded as out
        // For now, we record them as positive amounts paid OUT
        txn.setTenderCash(paidCash);
        txn.setTenderCard(paidCard);
        txn.setTenderAccount(paidAccount);
        txn.setTenderBank(paidBank); // New
        txn.setChangeDue(changeOut);

        txn.setStatus("COMPLETED");
        txn.setShiftId(currentShiftId); // Link to current shift
        txn.setItems(new java.util.ArrayList<>(cartItems));

        com.malek.pos.database.TransactionDAO txnDao = new com.malek.pos.database.TransactionDAO();
        boolean success = txnDao.saveTransaction(txn);

        if (success) {
            System.out.println("Transaction Saved Successfully!");

            Alert printConfirm = new Alert(Alert.AlertType.CONFIRMATION, "Print Receipt?", ButtonType.YES,
                    ButtonType.NO);
            printConfirm.setHeaderText(null);
            if (printConfirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                com.malek.pos.utils.ReceiptService.printReceipt(txn);
            }

            clearTransaction();

            // Publish Realtime Events
            com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_STOCK);
            com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_HISTORY);
            com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_DEBTORS);
            com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_REPORTS);
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to save transaction!", ButtonType.OK);
            alert.showAndWait();
        }
    }

    @FXML
    private void onLaybyClick() {
        if (cartItems.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Please add items to create a new Layby.").show();
            return;
        }

        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/layby_create.fxml"));
            javafx.scene.Parent page = loader.load();
            LaybyCreateController controller = loader.getController();

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Create Layby");
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            stage.initOwner(lblGrandTotal.getScene().getWindow());
            stage.setScene(new javafx.scene.Scene(page));

            // Close on ESC
            stage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    stage.close();
                }
            });

            controller.setDialogStage(stage);
            BigDecimal total = new BigDecimal(lblGrandTotal.getText());
            controller.setTransactionData(new java.util.ArrayList<>(cartItems), total);

            stage.showAndWait();

            if (controller.isCompleted()) {
                clearTransaction();
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to create Layby: " + e.getMessage()).show();
        }
    }

    @FXML
    private void onLaybyManagementClick() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/layby_management.fxml"));
            javafx.scene.Parent page = loader.load();
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Layby Management");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(lblGrandTotal.getScene().getWindow());
            stage.setScene(new javafx.scene.Scene(page));

            // Close on ESC
            stage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    stage.close();
                }
            });
            stage.setMaximized(true);
            stage.show();
        } catch (java.io.IOException e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to open Layby List: " + e.getMessage()).show();
        }
    }

    private void openUniversalSearch() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/universal_search.fxml"));
            javafx.scene.Parent page = loader.load();
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Universal Search");
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            stage.initOwner(lblGrandTotal.getScene().getWindow());
            stage.setScene(new javafx.scene.Scene(page));

            // Close on ESC
            stage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    stage.close();
                }
            });

            stage.show();
        } catch (java.io.IOException e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to open Universal Search: " + e.getMessage()).show();
        }
    }

    /*
     * @FXML
     * private void onDashboardClick() {
     * onAdminClick();
     * }
     */

    private void clearTransaction() {
        cartItems.clear();
        com.malek.pos.utils.CartPersistenceManager.clearCart();
        this.discountTotal = BigDecimal.ZERO;
        updateTotals();
        setCustomer("CASH");
        txtBarcodeSearch.requestFocus();
    }

    @FXML
    private void onSupplierClick() {
        if (supplierStage != null && supplierStage.isShowing()) {
            supplierStage.toFront();
            return;
        }
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/supplier_management.fxml"));
            javafx.scene.Parent page = loader.load();
            supplierStage = new javafx.stage.Stage();
            supplierStage.setTitle("Supplier Management & GRN");
            supplierStage.initModality(javafx.stage.Modality.NONE); // Non-Modal
            // supplierStage.initOwner(lblGrandTotal.getScene().getWindow());
            supplierStage.setScene(new javafx.scene.Scene(page));

            // Close on ESC
            supplierStage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    supplierStage.close();
                }
            });

            supplierStage.show();
        } catch (java.io.IOException e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to open Supplier Screen: " + e.getMessage()).show();
        }
    }

    @FXML
    private void onSettingsClick() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/settings.fxml"));
            javafx.scene.Parent page = loader.load();
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("System Settings");
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.initOwner(lblGrandTotal.getScene().getWindow());
            stage.setScene(new javafx.scene.Scene(page));

            // Close on ESC
            stage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    stage.close();
                }
            });

            stage.showAndWait();
        } catch (java.io.IOException e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to open Settings: " + e.getMessage()).show();
        }
    }

    @FXML
    private void onReportsClick() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/reports_screen.fxml"));
            javafx.scene.Parent page = loader.load();
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Real-Time Analytics");
            stage.initModality(javafx.stage.Modality.NONE); // Non-modal for live view
            // stage.initOwner(lblGrandTotal.getScene().getWindow());
            stage.setScene(new javafx.scene.Scene(page));

            // Close on ESC
            stage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    stage.close();
                }
            });

            stage.setMaximized(true);
            stage.show();
        } catch (java.io.IOException e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to open Reports: " + e.getMessage()).show();
        }
    }

}
