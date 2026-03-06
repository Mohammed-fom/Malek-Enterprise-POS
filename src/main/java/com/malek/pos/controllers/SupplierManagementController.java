package com.malek.pos.controllers;

import com.malek.pos.database.ProductDAO;
import com.malek.pos.database.SupplierDAO;
import com.malek.pos.models.Product;
import com.malek.pos.models.Purchase;
import com.malek.pos.models.PurchaseItem;
import com.malek.pos.models.Supplier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.GridPane;

public class SupplierManagementController {

    // DAOs
    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final ProductDAO productDAO = new ProductDAO();

    // Tab 1: List
    @FXML
    private TableView<Supplier> tblSuppliers;
    @FXML
    private TableColumn<Supplier, Integer> colId;
    @FXML
    private TableColumn<Supplier, String> colCompany;
    @FXML
    private TableColumn<Supplier, String> colContact;
    @FXML
    private TableColumn<Supplier, String> colPhone;
    @FXML
    private TableColumn<Supplier, BigDecimal> colBalance;

    // Edit/Delete
    @FXML
    private MenuItem mnEdit;
    @FXML
    private MenuItem mnDelete;

    // Tab 2: GRN
    @FXML
    private ComboBox<Supplier> cmbSupplier;
    @FXML
    private TextField txtInvoice;
    @FXML
    private TextField txtProductSearch;
    @FXML
    private TextField txtQuantity;
    @FXML
    private TextField txtCost;
    @FXML
    private Label lblTotalCost;
    @FXML
    private TextField txtRetail;
    @FXML
    private TableView<GRNItem> tblPurchaseItems;
    @FXML
    private TableColumn<GRNItem, Integer> colPiId;
    @FXML
    private TableColumn<GRNItem, String> colPiDesc;
    @FXML
    private TableColumn<GRNItem, BigDecimal> colPiQty;
    @FXML
    private TableColumn<GRNItem, BigDecimal> colPiCost;
    @FXML
    private TableColumn<GRNItem, BigDecimal> colPiRetail;
    @FXML
    private TableColumn<GRNItem, BigDecimal> colPiTotal;

    private final ObservableList<GRNItem> grnItems = FXCollections.observableArrayList();

    // Tab 3: Payment
    @FXML
    private ComboBox<Supplier> cmbPaySupplier;
    @FXML
    private Label lblPayBalance;
    @FXML
    private TextField txtPayAmount;

    @FXML
    public void initialize() {
        setupTables();
        refreshSuppliers();

        cmbSupplier.setItems(tblSuppliers.getItems());
        cmbPaySupplier.setItems(tblSuppliers.getItems());

        cmbPaySupplier.setOnAction(e -> {
            Supplier s = cmbPaySupplier.getValue();
            if (s != null)
                lblPayBalance.setText(String.format("%.2f", s.getCurrentBalance()));
        });

        tblPurchaseItems.setItems(grnItems);

        com.malek.pos.utils.EventBus.subscribe(com.malek.pos.utils.EventBus.REFRESH_SUPPLIERS, e -> refreshSuppliers());
    }

    private void setupTables() {
        colId.setCellValueFactory(c -> c.getValue().supplierIdProperty().asObject());
        colCompany.setCellValueFactory(c -> c.getValue().companyNameProperty());
        colContact.setCellValueFactory(c -> c.getValue().contactPersonProperty());
        colPhone.setCellValueFactory(c -> c.getValue().phoneProperty());
        colBalance.setCellValueFactory(c -> c.getValue().currentBalanceProperty());

        colBalance.setCellFactory(column -> new TableCell<Supplier, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format("%.2f", item));
                    if (item.compareTo(BigDecimal.ZERO) > 0) {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: green;");
                    }
                }
            }
        });

        colPiId.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getProduct().getProductId()));
        colPiDesc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProduct().getDescription()));
        colPiQty.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getQuantity()));
        colPiCost.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCostIncl()));
        colPiRetail.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getRetailIncl()));
        colPiTotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getTotal()));

        // Context Menu for Edit/Delete
        ContextMenu cm = new ContextMenu();
        MenuItem edit = new MenuItem("Edit Supplier");
        MenuItem delete = new MenuItem("Delete Supplier");

        edit.setOnAction(e -> onEditSupplier());
        delete.setOnAction(e -> onDeleteSupplier());

        cm.getItems().addAll(edit, delete);
        tblSuppliers.setContextMenu(cm);
    }

    // --- Add / Edit Logic ---

    @FXML
    private void onEditSupplier() {
        Supplier s = tblSuppliers.getSelectionModel().getSelectedItem();
        if (s == null)
            return;
        showSupplierDialog(s);
    }

    @FXML
    private void onAddSupplier() {
        showSupplierDialog(null);
    }

    private void showSupplierDialog(Supplier existing) {
        Dialog<Supplier> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "New Supplier" : "Edit Supplier");
        dialog.setHeaderText(existing == null ? "Add New Supplier Details" : "Edit " + existing.getCompanyName());

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField txtName = new TextField();
        TextField txtContact = new TextField();
        TextField txtPhone = new TextField();
        TextField txtEmail = new TextField();
        TextField txtAddress = new TextField();

        if (existing != null) {
            txtName.setText(existing.getCompanyName());
            txtContact.setText(existing.getContactPerson());
            txtPhone.setText(existing.getPhone());
            txtEmail.setText(existing.getEmail());
            txtAddress.setText(existing.getAddress());
        }

        grid.add(new Label("Company Name:"), 0, 0);
        grid.add(txtName, 1, 0);
        grid.add(new Label("Contact Person:"), 0, 1);
        grid.add(txtContact, 1, 1);
        grid.add(new Label("Phone Number:"), 0, 2);
        grid.add(txtPhone, 1, 2);
        grid.add(new Label("Email:"), 0, 3);
        grid.add(txtEmail, 1, 3);
        grid.add(new Label("Address:"), 0, 4);
        grid.add(txtAddress, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveType) {
                Supplier s = existing != null ? existing : new Supplier();
                s.setCompanyName(txtName.getText().trim());
                s.setContactPerson(txtContact.getText().trim());
                s.setPhone(txtPhone.getText().trim());
                s.setEmail(txtEmail.getText().trim());
                s.setAddress(txtAddress.getText().trim());
                if (s.getContactPerson().isEmpty())
                    s.setContactPerson("N/A");
                return s;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(s -> {
            try {
                if (existing == null) {
                    if (!s.getCompanyName().isEmpty()) {
                        supplierDAO.createSupplier(s);
                        new Alert(AlertType.INFORMATION, "Supplier Added").show();
                    }
                } else {
                    supplierDAO.updateSupplier(s);
                    new Alert(AlertType.INFORMATION, "Supplier Updated").show();
                }
                refreshSuppliers();
                com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_SUPPLIERS);
            } catch (Exception e) {
                new Alert(AlertType.ERROR, "Error: " + e.getMessage()).show();
                e.printStackTrace();
            }
        });
    }

    @FXML
    @SuppressWarnings("unchecked")
    private void onOpenProductSearch() {
        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Product Search");
        dialog.setHeaderText("Search by Name or Barcode");

        ButtonType selectType = new ButtonType("Select", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(selectType, ButtonType.CANCEL);

        javafx.scene.layout.BorderPane content = new javafx.scene.layout.BorderPane();
        TextField txtSearch = new TextField();
        txtSearch.setPromptText("Type to search...");

        TableView<Product> tblResults = new TableView<>();
        TableColumn<Product, String> colName = new TableColumn<>("Description");
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescription()));
        colName.setPrefWidth(300);

        TableColumn<Product, String> colBarcode = new TableColumn<>("Barcode");
        colBarcode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBarcode()));

        TableColumn<Product, BigDecimal> colCost = new TableColumn<>("Cost");
        colCost.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCostPrice()));

        tblResults.getColumns().addAll(colName, colBarcode, colCost);

        content.setTop(txtSearch);
        content.setCenter(tblResults);
        content.setPadding(new javafx.geometry.Insets(10));
        javafx.scene.layout.BorderPane.setMargin(txtSearch, new javafx.geometry.Insets(0, 0, 10, 0));

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(600, 400);

        txtSearch.textProperty().addListener((obs, old, val) -> {
            if (val == null || val.trim().isEmpty()) {
                tblResults.setItems(FXCollections.observableArrayList());
                return;
            }
            List<Product> results = productDAO.searchByDescription(val);
            tblResults.setItems(FXCollections.observableArrayList(results));
        });

        // Double click to select
        tblResults.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && tblResults.getSelectionModel().getSelectedItem() != null) {
                // Determine which button is the Result button (Select)
                Button btn = (Button) dialog.getDialogPane().lookupButton(selectType);
                btn.fire();
            }
        });

        dialog.setResultConverter(btn -> {
            if (btn == selectType) {
                return tblResults.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        javafx.application.Platform.runLater(txtSearch::requestFocus);

        dialog.showAndWait().ifPresent(p -> {
            txtProductSearch.setText(p.getBarcode() != null && !p.getBarcode().isEmpty() ? p.getBarcode()
                    : String.valueOf(p.getProductId()));
            onAddProduct();
        });
    }

    // --- GRN Logic ---
    @FXML
    private void onAddProduct() {
        // Search product
        // Not fully implemented search -> just assumes ID search for now or EXACT
        // helper
        String q = txtProductSearch.getText();
        // Assuming findById for simplicity or we need a robust search dialog.
        // I'll assume standard barcode search
        Product p = productDAO.findByBarcode(q);
        if (p == null) {
            try {
                int id = Integer.parseInt(q);
                p = productDAO.getProductById(id);
            } catch (Exception e) {
            }
        }

        if (p != null) {
            // Auto-fill cost and retail (INCL VAT)
            txtCost.setText(p.getCostPriceIncl().toString());
            try {
                // If Retail Incl method exists in product model, utilize it, else derive
                txtRetail.setText(p.getPriceRetailIncl().toString());
            } catch (Exception e) {
                txtRetail.setText("0.00");
            }
            txtQuantity.requestFocus();
        } else {
            new Alert(AlertType.ERROR, "Product not found").show();
        }
    }

    @FXML
    private void onAddItem() {
        String q = txtProductSearch.getText();
        Product p = productDAO.findByBarcode(q);
        if (p == null) {
            try {
                p = productDAO.getProductById(Integer.parseInt(q));
            } catch (Exception e) {
            }
        }
        if (p == null)
            return;

        try {
            BigDecimal qty = new BigDecimal(txtQuantity.getText());
            BigDecimal costIncl = new BigDecimal(txtCost.getText());
            BigDecimal retailIncl = new BigDecimal(txtRetail.getText());

            // Calculate Excl values for storage/logic using Product Tax Rate
            BigDecimal taxRate = (p.getTaxRate() != null) ? p.getTaxRate() : new BigDecimal("15.00");
            BigDecimal divisor = BigDecimal.ONE.add(taxRate.divide(new BigDecimal("100")));

            BigDecimal costExcl = costIncl.divide(divisor, 2, java.math.RoundingMode.HALF_UP);
            BigDecimal retailExcl = retailIncl.divide(divisor, 2, java.math.RoundingMode.HALF_UP);

            grnItems.add(new GRNItem(p, qty, costExcl, costIncl, retailExcl, retailIncl));
            updateTotal();
            txtProductSearch.clear();
            txtQuantity.setText("1");
            txtCost.clear();
            txtRetail.clear();
            txtProductSearch.requestFocus();
        } catch (Exception e) {
            new Alert(AlertType.ERROR, "Invalid Number").show();
        }
    }

    private void updateTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (GRNItem i : grnItems)
            total = total.add(i.getTotal());
        lblTotalCost.setText(total.setScale(2, java.math.RoundingMode.HALF_UP).toString());
    }

    @FXML
    private void onProcessGRN() {
        try {
            Supplier s = cmbSupplier.getValue();
            if (s == null || grnItems.isEmpty()) {
                new Alert(AlertType.WARNING, "Select Supplier and Add Items").show();
                return;
            }

            // Recalculate total from items to avoid label parsing issues
            BigDecimal total = BigDecimal.ZERO;
            List<PurchaseItem> items = new ArrayList<>();
            for (GRNItem i : grnItems) {
                total = total.add(i.getTotal());
                items.add(new PurchaseItem(i.getProduct().getProductId(), i.getQuantity(), i.getCostExcl()));

                // Update Product Retail Price (Cost Price is updated via WAC in SupplierDAO)
                Product p = i.getProduct();
                p.setPriceRetail(i.getRetailExcl());

                // Note: We do NOT update Cost Price here anymore, as SupplierDAO calculates
                // Weighted Average Cost.
                // We only update Retail Price and other attributes via productDAO.updateProduct
                // if needed.
                // Actually, if we just want to update Retail Price, we can call
                // productDAO.updateProduct(p)
                // but p still has the OLD cost price (because we didn't set it).
                // productDAO.updateProduct(p) updates ALL fields including Cost Price.
                // If p has old cost, and we save it, we are safe (restoring old cost).
                // BUT SupplierDAO runs AFTER this.
                // Sequence:
                // 1. productDAO.updateProduct(p) -> sets Cost = Old Cost, Retail = New Retail.
                // 2. supplierDAO.createPurchase -> sets Cost = WAC Cost using (Cost from DB *
                // Stock) ...
                // Wait, if step 1 sets Cost = Old Cost, DB has Old Cost.
                // Step 2 reads DB Cost (Old Cost). Calculation: (OldStock * OldCost + NewQty *
                // NewCost) / ...
                // This seems CORRECT.
                // Just ensuring we don't accidentally set `p.setCostPrice(NewCost)` here.

                productDAO.updateProduct(p);
            }

            Purchase purchase = new Purchase(s.getSupplierId(),
                    txtInvoice.getText() != null ? txtInvoice.getText().trim() : "");
            purchase.setTotalCost(total);

            String result = supplierDAO.createPurchase(purchase, items);

            if (result != null && result.startsWith("Error")) {
                new Alert(AlertType.ERROR, result).show();
            } else {
                if (result != null) {
                    Alert alert = new Alert(AlertType.INFORMATION, "Stock Received with Warnings:\n" + result);
                    alert.setHeaderText("Price Watchdog");
                    alert.showAndWait();
                } else {
                    new Alert(AlertType.INFORMATION, "Stock Received Successfully.").show();
                }

                // Success/Warning -> Clear
                grnItems.clear();
                lblTotalCost.setText("0.00");
                txtInvoice.clear();
                com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_SUPPLIERS);
                refreshSuppliers();
            }
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(AlertType.ERROR, "Process Failed: " + e.getMessage()).show();
        }
    }

    // --- Payment Logic ---
    @FXML
    private void onConfirmPayment() {
        Supplier s = cmbPaySupplier.getValue();
        if (s == null)
            return;

        try {
            BigDecimal amt = new BigDecimal(txtPayAmount.getText());
            supplierDAO.paySupplier(s.getSupplierId(), amt);
            new Alert(AlertType.INFORMATION, "Payment Recorded").show();
            txtPayAmount.clear();
            com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_SUPPLIERS);
            refreshSuppliers();
            lblPayBalance.setText(s.getCurrentBalance().subtract(amt).toString()); // rough update
        } catch (Exception e) {
            new Alert(AlertType.ERROR, "Invalid Amount").show();
        }
    }

    private void onDeleteSupplier() {
        Supplier s = tblSuppliers.getSelectionModel().getSelectedItem();
        if (s == null)
            return;

        Alert alert = new Alert(AlertType.CONFIRMATION,
                "Delete supplier " + s.getCompanyName() + "?\nThis cannot be undone.");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    supplierDAO.deleteSupplier(s.getSupplierId());
                    refreshSuppliers();
                    new Alert(AlertType.INFORMATION, "Supplier deleted.").show();
                } catch (Exception ex) {
                    new Alert(AlertType.ERROR, "Cannot delete supplier.\nThey may be linked to existing purchases.")
                            .show();
                    ex.printStackTrace(); // Log detailed sql ex
                }
            }
        });
    }

    @FXML
    private void onRefreshSuppliers() {
        refreshSuppliers();
    }

    private void refreshSuppliers() {
        ObservableList<Supplier> list = FXCollections.observableArrayList(supplierDAO.getAllSuppliers());
        tblSuppliers.setItems(list);
        cmbSupplier.setItems(list);
        cmbPaySupplier.setItems(list);
    }

    // Helper Class
    public static class GRNItem {
        private Product product;
        private BigDecimal quantity;
        private BigDecimal costExcl;
        private BigDecimal costIncl;
        private BigDecimal retailExcl;
        private BigDecimal retailIncl;

        public GRNItem(Product p, BigDecimal q, BigDecimal cExcl, BigDecimal cIncl, BigDecimal rExcl,
                BigDecimal rIncl) {
            this.product = p;
            this.quantity = q;
            this.costExcl = cExcl;
            this.costIncl = cIncl;
            this.retailExcl = rExcl;
            this.retailIncl = rIncl;
        }

        public Product getProduct() {
            return product;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public BigDecimal getCostExcl() {
            return costExcl;
        }

        public BigDecimal getCostIncl() {
            return costIncl;
        }

        public BigDecimal getRetailExcl() {
            return retailExcl;
        }

        public BigDecimal getRetailIncl() {
            return retailIncl;
        }

        public BigDecimal getTotal() {
            return costIncl.multiply(quantity);
        }
    }
}
