package com.malek.pos.controllers;

import com.malek.pos.database.ProductDAO;
import com.malek.pos.models.Product;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import java.math.BigDecimal;
import java.util.Optional;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileInputStream;
import java.util.prefs.Preferences;
import javafx.event.ActionEvent;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;

public class StockController {

    @FXML
    private TextField txtSearch;
    @FXML
    private TableView<Product> tblProducts;
    @FXML
    private TableColumn<Product, Integer> colId;
    @FXML
    private TableColumn<Product, String> colProductCode;
    @FXML
    private TableColumn<Product, String> colDescription;
    @FXML
    private TableColumn<Product, String> colCategory;
    @FXML
    private TableColumn<Product, BigDecimal> colCost;
    @FXML
    private TableColumn<Product, BigDecimal> colCostExcl;
    @FXML
    private TableColumn<Product, BigDecimal> colRetail;
    @FXML
    private TableColumn<Product, BigDecimal> colRetailExcl;
    @FXML
    private TableColumn<Product, BigDecimal> colTrade;
    @FXML
    private TableColumn<Product, BigDecimal> colStock;
    @FXML
    private TableColumn<Product, Boolean> colService;

    @FXML
    private Label lblTotalCostValue;
    @FXML
    private Label lblTotalRetailValue;

    private final ProductDAO productDAO = new ProductDAO();
    private final com.malek.pos.database.SupplierDAO supplierDAO = new com.malek.pos.database.SupplierDAO();
    private final ObservableList<Product> productList = FXCollections.observableArrayList();
    private final Preferences prefs = Preferences.userNodeForPackage(StockController.class);

    @FXML
    public void initialize() {
        setupColumns();
        loadColumnPreferences();
        loadData();

        txtSearch.textProperty().addListener((obs, o, n) -> {
            if (n.isEmpty()) {
                loadData();
            }
        });

        // Subscribe to Realtime Updates
        com.malek.pos.utils.EventBus.subscribe(com.malek.pos.utils.EventBus.REFRESH_STOCK, e -> loadData());

        // Handle ENTER key to edit
        tblProducts.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                onEditProduct();
            }
        });
    }

    private void setupColumns() {
        colId.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getProductId()));
        colId.setVisible(false); // Hidden by default as per request
        colProductCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductCode()));
        colDescription.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescription()));
        colCategory.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategory()));
        colCost.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCostPriceIncl()));
        colCostExcl.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCostPrice()));
        colRetail.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPriceRetailIncl()));
        colRetailExcl.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPriceRetail()));
        colTrade.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPriceTrade()));
        colStock.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getStockOnHand()));
        colService.setCellValueFactory(c -> new SimpleBooleanProperty(c.getValue().isServiceItem()));

        // Low Stock Highlight & Double Click to Edit
        tblProducts.setRowFactory(tv -> {
            TableRow<Product> row = new TableRow<>() {
                @Override
                protected void updateItem(Product item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item == null || empty) {
                        setStyle("");
                    } else {
                        BigDecimal stock = item.getStockOnHand() != null ? item.getStockOnHand() : BigDecimal.ZERO;
                        BigDecimal threshold = item.getLowStockThreshold() != null ? item.getLowStockThreshold()
                                : BigDecimal.ZERO;
                        if (stock.compareTo(threshold) <= 0 && !item.isServiceItem()) {
                            setStyle("-fx-background-color: #ffcccc; -fx-text-fill: #c0392b;");
                        } else {
                            setStyle("");
                        }
                    }
                }
            };

            // Double Click Listener
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    onEditProduct();
                }
            });

            return row;
        });
    }

    private void loadData() {
        productList.setAll(productDAO.getAllProducts());
        tblProducts.setItems(productList);
        updateInventoryTotals();
    }

    @FXML
    private void onRefresh() {
        txtSearch.clear();
        loadData();
    }

    @FXML
    private void onColumnFilter(ActionEvent event) {
        ContextMenu contextMenu = new ContextMenu();

        for (TableColumn<Product, ?> col : tblProducts.getColumns()) {
            CheckMenuItem item = new CheckMenuItem(col.getText());
            item.setSelected(col.isVisible());

            // Bind check state to column visibility
            item.selectedProperty().addListener((obs, oldVal, newVal) -> {
                col.setVisible(newVal);
                saveColumnPreference(col.getId(), newVal);
            });

            contextMenu.getItems().add(item);
        }

        // Show the menu below the source button
        if (event.getSource() instanceof Button) {
            Button btn = (Button) event.getSource();
            contextMenu.show(btn, javafx.geometry.Side.BOTTOM, 0, 0);
        }
    }

    private void loadColumnPreferences() {
        for (TableColumn<Product, ?> col : tblProducts.getColumns()) {
            if (col.getId() != null) {
                boolean isVisible = prefs.getBoolean("stock_col_vis_" + col.getId(), true);
                col.setVisible(isVisible);
            }
        }
    }

    private void saveColumnPreference(String colId, boolean isVisible) {
        if (colId != null) {
            prefs.putBoolean("stock_col_vis_" + colId, isVisible);
        }
    }

    @FXML
    private void onSearch() {
        String q = txtSearch.getText().trim();
        if (!q.isEmpty()) {
            productList.setAll(productDAO.searchByDescription(q));
            // If empty, maybe search by barcode
            if (productList.isEmpty()) {
                Product p = productDAO.findByBarcodeOrCode(q);
                if (p != null) {
                    productList.setAll(p);
                }
            }
            updateInventoryTotals();
        } else {
            loadData();
        }
    }

    public void setSearch(String query) {
        txtSearch.setText(query);
        onSearch();
    }

    @FXML
    private void onAddProduct() {
        showProductDialog(null);
    }

    @FXML
    private void onEditProduct() {
        Product selected = tblProducts.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showProductDialog(selected);
        } else {
            showAlert("Please select a product to edit.");
        }
    }

    @FXML
    private void onDeleteProduct() {
        Product selected = tblProducts.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Delete Product");
            alert.setHeaderText("Delete " + selected.getDescription() + "?");
            alert.setContentText("Are you sure? This cannot be undone.");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                if (productDAO.deleteProduct(selected.getProductId())) {
                    loadData();
                    showAlert("Product deleted.");
                } else {
                    showAlert("Could not delete. Check if it's used in transactions.");
                }
            }
        } else {
            showAlert("Please select a product.");
        }
    }

    @FXML
    private void onStockAdjustment() {
        Product selected = tblProducts.getSelectionModel().getSelectedItem();
        if (selected != null) {
            TextInputDialog dialog = new TextInputDialog("0");
            dialog.setTitle("Stock Adjustment");
            dialog.setHeaderText("Adjust Stock for: " + selected.getDescription());
            dialog.setContentText("New Stock Quantity:");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(qtyStr -> {
                try {
                    BigDecimal newQty = new BigDecimal(qtyStr);
                    selected.setStockOnHand(newQty);
                    // We reuse updateProduct for now, but a dedicated audit log adjustment would be
                    // better
                    if (productDAO.updateProduct(selected)) {
                        loadData();
                        showAlert("Stock updated.");
                    } else {
                        showAlert("Failed to update stock.");
                    }
                } catch (NumberFormatException e) {
                    showAlert("Invalid quantity.");
                }
            });
        } else {
            showAlert("Please select a product.");
        }
    }

    @FXML
    private void onReceiveStock() {
        Product selected = tblProducts.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Please select a product to receive stock for.");
            return;
        }

        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Receive Stock: " + selected.getDescription());
        dialog.setHeaderText("Enter Purchase Details");

        ButtonType confirmType = new ButtonType("Receive", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        ComboBox<com.malek.pos.models.Supplier> cmbSupplier = new ComboBox<>();
        cmbSupplier.setItems(FXCollections.observableArrayList(supplierDAO.getAllSuppliers()));
        // Select current supplier default
        if (selected.getSupplierId() > 0) {
            for (com.malek.pos.models.Supplier s : cmbSupplier.getItems()) {
                if (s.getSupplierId() == selected.getSupplierId()) {
                    cmbSupplier.setValue(s);
                    break;
                }
            }
        }

        // Render supplier names correctly
        cmbSupplier.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(com.malek.pos.models.Supplier item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getCompanyName());
            }
        });
        cmbSupplier.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(com.malek.pos.models.Supplier item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getCompanyName());
            }
        });

        TextField txtQty = new TextField();
        TextField txtCost = new TextField(
                selected.getCostPriceIncl() != null ? selected.getCostPriceIncl().toString() : "0.00");
        TextField txtInvoice = new TextField();

        grid.add(new Label("Supplier:"), 0, 0);
        grid.add(cmbSupplier, 1, 0);
        grid.add(new Label("Invoice No:"), 0, 1);
        grid.add(txtInvoice, 1, 1);
        grid.add(new Label("Quantity In:"), 0, 2);
        grid.add(txtQty, 1, 2);
        grid.add(new Label("Unit Cost (Incl VAT):"), 0, 3);
        grid.add(txtCost, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(ButtonType -> {
            if (ButtonType == confirmType) {
                try {
                    BigDecimal qty = new BigDecimal(txtQty.getText());
                    BigDecimal costIncl = new BigDecimal(txtCost.getText());
                    String inv = txtInvoice.getText();
                    if (inv.isEmpty())
                        inv = "N/A";
                    int supId = cmbSupplier.getValue() != null ? cmbSupplier.getValue().getSupplierId() : 1;

                    // Convert Incl Cost to Excl Cost
                    BigDecimal vatRate = selected.getTaxRate() != null ? selected.getTaxRate()
                            : new BigDecimal("15.00");
                    BigDecimal divisor = BigDecimal.ONE.add(vatRate.divide(new BigDecimal("100")));
                    BigDecimal costExcl = costIncl.divide(divisor, 2, java.math.RoundingMode.HALF_UP);

                    if (productDAO.recordPurchase(selected.getProductId(), supId, qty, costExcl, inv)) {
                        return true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return false;
        });

        // Show
        dialog.showAndWait().ifPresent(success -> {
            if (Boolean.TRUE.equals(success)) {
                loadData();
                showAlert("Stock Received Successfully.");
            } else {
                showAlert("Failed to record purchase.");
            }
        });
    }

    @FXML
    private void onViewHistory() {
        Product selected = tblProducts.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Please select a product.");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("History: " + selected.getDescription());
        dialog.setHeaderText("Purchase History");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TableView<ProductDAO.PurchaseHistoryDTO> table = new TableView<>();

        TableColumn<ProductDAO.PurchaseHistoryDTO, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(Data -> new SimpleStringProperty(
                Data.getValue().date().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy HH:mm"))));

        TableColumn<ProductDAO.PurchaseHistoryDTO, String> colSup = new TableColumn<>("Supplier");
        colSup.setCellValueFactory(Data -> new SimpleStringProperty(Data.getValue().supplier()));

        TableColumn<ProductDAO.PurchaseHistoryDTO, BigDecimal> colQty = new TableColumn<>("Qty");
        colQty.setCellValueFactory(Data -> new SimpleObjectProperty<>(Data.getValue().qty()));

        TableColumn<ProductDAO.PurchaseHistoryDTO, BigDecimal> colCost = new TableColumn<>("Cost");
        colCost.setCellValueFactory(Data -> new SimpleObjectProperty<>(Data.getValue().cost()));

        TableColumn<ProductDAO.PurchaseHistoryDTO, String> colInv = new TableColumn<>("Invoice");
        colInv.setCellValueFactory(Data -> new SimpleStringProperty(Data.getValue().invoice()));

        @SuppressWarnings("unchecked")
        TableColumn<ProductDAO.PurchaseHistoryDTO, ?>[] columns = new TableColumn[] { colDate, colSup, colQty, colCost,
                colInv };
        table.getColumns().addAll(columns);
        table.setItems(FXCollections.observableArrayList(productDAO.getPurchaseHistory(selected.getProductId())));
        table.setPrefWidth(600);

        dialog.getDialogPane().setContent(table);
        dialog.showAndWait();
    }

    @FXML
    private void onClose() {
        txtSearch.getScene().getWindow().hide();
    }

    private void showProductDialog(Product product) {
        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle(product == null ? "Add Product" : "Edit Product");
        dialog.setHeaderText(product == null ? "New Product Details" : "Edit " + product.getDescription());

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField description = new TextField();
        TextField productCode = new TextField();
        TextField barcode = new TextField();
        TextField category = new TextField();
        ComboBox<com.malek.pos.models.Supplier> cmbSupplier = new ComboBox<>();
        cmbSupplier.setItems(FXCollections.observableArrayList(supplierDAO.getAllSuppliers()));
        // Setup converter for Supplier if needed, but toString() usually prints object
        // ref unless overridden.
        // Supplier needs a toString() or a cell factory. Let's assume
        // Supplier.toString() returns name or handle it.
        // Usually better to set cell factory for cleaner display.
        cmbSupplier.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(com.malek.pos.models.Supplier item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getCompanyName());
            }
        });
        cmbSupplier.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(com.malek.pos.models.Supplier item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getCompanyName());
            }
        });

        // Prices
        TextField costExcl = new TextField("0.00");
        TextField costIncl = new TextField("0.00");
        TextField retailExcl = new TextField("0.00");
        TextField retailIncl = new TextField("0.00");
        TextField trade = new TextField("0.00"); // Kept as is for now

        TextField tax = new TextField("15.00");
        TextField stock = new TextField("0");
        CheckBox isService = new CheckBox("Service Item");

        // Helper to calc tax
        Runnable recalculatePrices = () -> {
            try {
                BigDecimal taxRate = new BigDecimal(tax.getText());
                BigDecimal multiplier = BigDecimal.ONE.add(taxRate.divide(new BigDecimal("100")));

                // We rely on the "focused" property or just simple order.
                // However, bi-directional binding is tricky.
                // Simplified approach: Listeners added below.
            } catch (Exception e) {
            }
        };

        if (product != null) {
            description.setText(product.getDescription());
            productCode.setText(product.getProductCode() != null ? product.getProductCode() : "");
            barcode.setText(product.getBarcode() != null ? product.getBarcode() : "");
            barcode.setEditable(false); // Make it read-only if it already exists? User said "create a barcode system",
                                        // maybe they want to see it but not edit it easily or auto-generated.
            // Let's keep it editable for flexibility but pre-fill for new.
            category.setText(product.getCategory());

            BigDecimal tRate = product.getTaxRate() != null ? product.getTaxRate() : new BigDecimal("15.00");
            tax.setText(tRate.toString());
            BigDecimal mult = BigDecimal.ONE.add(tRate.divide(new BigDecimal("100")));

            costExcl.setText(product.getCostPrice().toString());
            costIncl.setText(
                    product.getCostPrice().multiply(mult).setScale(2, java.math.RoundingMode.HALF_UP).toString());

            retailExcl.setText(product.getPriceRetail().toString());
            retailIncl.setText(
                    product.getPriceRetail().multiply(mult).setScale(2, java.math.RoundingMode.HALF_UP).toString());

            trade.setText(product.getPriceTrade().toString());
            stock.setText(product.getStockOnHand() != null ? product.getStockOnHand().toString() : "0");
            isService.setSelected(product.isServiceItem());

            // Set Supplier
            if (product.getSupplierId() > 0) {
                for (com.malek.pos.models.Supplier s : cmbSupplier.getItems()) {
                    if (s.getSupplierId() == product.getSupplierId()) {
                        cmbSupplier.setValue(s);
                        break;
                    }
                }
            }
        } else {
            // New Product - Auto Generate Barcode
            String autoBarcode = generateUniqueBarcode();
            barcode.setText(autoBarcode);
            // Optional: Hide ID or focus on Barcode
        }

        // Auto-Calc Logic
        java.util.concurrent.atomic.AtomicBoolean isUpdating = new java.util.concurrent.atomic.AtomicBoolean(false);

        // Helper to parse tax safely
        java.util.function.Supplier<BigDecimal> getTaxRate = () -> {
            try {
                return new BigDecimal(tax.getText());
            } catch (Exception e) {
                return new BigDecimal("15.00");
            }
        };

        // COST LISTENERS
        costExcl.textProperty().addListener((obs, oldVal, newVal) -> {
            if (isUpdating.get())
                return;
            isUpdating.set(true);
            try {
                if (newVal.isEmpty()) {
                    costIncl.setText("");
                } else {
                    BigDecimal val = new BigDecimal(newVal);
                    BigDecimal rate = getTaxRate.get().divide(new BigDecimal("100"));
                    BigDecimal incl = val.multiply(BigDecimal.ONE.add(rate)).setScale(2,
                            java.math.RoundingMode.HALF_UP);
                    costIncl.setText(incl.toString());
                }
            } catch (Exception e) {
                // Ignore parse errors while typing
            } finally {
                isUpdating.set(false);
            }
        });

        costIncl.textProperty().addListener((obs, oldVal, newVal) -> {
            if (isUpdating.get())
                return;
            isUpdating.set(true);
            try {
                if (newVal.isEmpty()) {
                    costExcl.setText("");
                } else {
                    BigDecimal val = new BigDecimal(newVal);
                    BigDecimal rate = getTaxRate.get().divide(new BigDecimal("100"));
                    BigDecimal div = BigDecimal.ONE.add(rate);
                    if (div.compareTo(BigDecimal.ZERO) != 0) {
                        BigDecimal excl = val.divide(div, 2, java.math.RoundingMode.HALF_UP);
                        costExcl.setText(excl.toString());
                    }
                }
            } catch (Exception e) {
            } finally {
                isUpdating.set(false);
            }
        });

        // RETAIL LISTENERS
        retailExcl.textProperty().addListener((obs, oldVal, newVal) -> {
            if (isUpdating.get())
                return;
            isUpdating.set(true);
            try {
                if (newVal.isEmpty()) {
                    retailIncl.setText("");
                } else {
                    BigDecimal val = new BigDecimal(newVal);
                    BigDecimal rate = getTaxRate.get().divide(new BigDecimal("100"));
                    BigDecimal incl = val.multiply(BigDecimal.ONE.add(rate)).setScale(2,
                            java.math.RoundingMode.HALF_UP);
                    retailIncl.setText(incl.toString());
                }
            } catch (Exception e) {
            } finally {
                isUpdating.set(false);
            }
        });

        retailIncl.textProperty().addListener((obs, oldVal, newVal) -> {
            if (isUpdating.get())
                return;
            isUpdating.set(true);
            try {
                if (newVal.isEmpty()) {
                    retailExcl.setText("");
                } else {
                    BigDecimal val = new BigDecimal(newVal);
                    BigDecimal rate = getTaxRate.get().divide(new BigDecimal("100"));
                    BigDecimal div = BigDecimal.ONE.add(rate);
                    if (div.compareTo(BigDecimal.ZERO) != 0) {
                        BigDecimal excl = val.divide(div, 2, java.math.RoundingMode.HALF_UP);
                        retailExcl.setText(excl.toString());
                    }
                }
            } catch (Exception e) {
            } finally {
                isUpdating.set(false);
            }
        });

        grid.add(new Label("Description:"), 0, 0);
        grid.add(description, 1, 0);

        grid.add(new Label("Product Code:"), 0, 1);
        grid.add(productCode, 1, 1);
        grid.add(new Label("Barcode:"), 2, 1);
        grid.add(barcode, 3, 1);
        grid.add(new Label("Category:"), 0, 2);
        grid.add(category, 1, 2);

        grid.add(new Label("Tax Rate (%):"), 0, 3);
        grid.add(tax, 1, 3);

        grid.add(new Label("Cost (Excl):"), 0, 4);
        grid.add(costExcl, 1, 4);
        grid.add(new Label("Cost (Incl):"), 2, 4); // New
                                                   // Column
        grid.add(costIncl, 3, 4);

        grid.add(new Label("Retail (Excl):"), 0, 5);
        grid.add(retailExcl, 1, 5);
        grid.add(new Label("Retail (Incl):"), 2, 5); // New
                                                     // Column
        grid.add(retailIncl, 3, 5);

        grid.add(new Label("Trade Price:"), 0, 6);
        grid.add(trade, 1, 6);

        grid.add(new Label("Stock On Hand:"), 0, 7);
        grid.add(stock, 1, 7);
        grid.add(new Label("Supplier:"), 0, 8);
        grid.add(cmbSupplier, 1, 8);
        grid.add(isService, 1, 9);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                Product p = product == null ? new Product()
                        : product;
                p.setDescription(description.getText());
                p.setProductCode(productCode.getText().trim());
                p.setBarcode(barcode.getText().trim());
                p.setCategory(category.getText());
                try {
                    // We save the EXCL values to the DB
                    p.setCostPrice(new BigDecimal(costExcl.getText()));
                    p.setPriceRetail(new BigDecimal(retailExcl.getText()));
                    p.setPriceTrade(new BigDecimal(trade.getText()));
                    p.setTaxRate(new BigDecimal(tax.getText()));
                    p.setStockOnHand(new BigDecimal(stock.getText()));
                } catch (Exception e) {
                    // Fallback to zeros on error
                }
                p.setServiceItem(isService.isSelected());
                if (p.getLowStockThreshold() == null)
                    p.setLowStockThreshold(BigDecimal.ZERO);
                if (p.getLowStockThreshold() == null)
                    p.setLowStockThreshold(BigDecimal.ZERO);

                if (cmbSupplier.getValue() != null) {
                    p.setSupplierId(cmbSupplier.getValue().getSupplierId());
                } else {
                    if (p.getSupplierId() == 0)
                        p.setSupplierId(1); // Default
                                            // if
                                            // none
                                            // selected
                }
                return p;
            }
            return null;
        });

        Optional<Product> result = dialog.showAndWait();
        result.ifPresent(p -> {
            boolean success;
            if (product == null) {
                success = productDAO.addProduct(p);
            } else {
                success = productDAO.updateProduct(p);
            }

            if (success) {
                loadData();
                showAlert("Saved successfully.");
            } else {
                showAlert("Error saving product.");
            }
        });
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(msg);
        alert.show();
    }

    private void updateInventoryTotals() {
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalRetail = BigDecimal.ZERO;

        for (Product p : productList) {
            BigDecimal stock = p.getStockOnHand() != null ? p.getStockOnHand() : BigDecimal.ZERO;
            if (stock.compareTo(BigDecimal.ZERO) > 0) { // Only count positive stock? Actually value is all stock.
                BigDecimal cost = p.getCostPrice() != null ? p.getCostPrice() : BigDecimal.ZERO;
                BigDecimal retail = p.getPriceRetail() != null ? p.getPriceRetail() : BigDecimal.ZERO;

                totalCost = totalCost.add(cost.multiply(stock));
                totalRetail = totalRetail.add(retail.multiply(stock));
            }
        }

        if (lblTotalCostValue != null)
            lblTotalCostValue.setText("Cost: R" + totalCost.setScale(2, java.math.RoundingMode.HALF_UP));
        if (lblTotalRetailValue != null)
            lblTotalRetailValue.setText("Retail: R" + totalRetail.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @FXML
    private void onImportExcel() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Import Stock from Excel");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File file = fileChooser.showOpenDialog(txtSearch.getScene().getWindow());
        if (file != null) {
            importStockFromFile(file);
        }
    }

    @FXML
    private void onPrintLabel() {
        Product selected = tblProducts.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Please select a product to print label.");
            return;
        }

        // Ensure barcode exists for QR
        if (selected.getBarcode() == null || selected.getBarcode().isEmpty()) {
            showAlert("Selected product has no barcode.");
            return;
        }

        java.util.Optional<com.malek.pos.utils.LabelConfigDialog.LabelConfig> config = com.malek.pos.utils.LabelConfigDialog
                .showAndWait();
        config.ifPresent(c -> {
            com.malek.pos.utils.LabelService.printLabel(selected, c);
        });
    }

    private void importStockFromFile(File file) {
        int imported = 0;
        int errors = 0;
        try (FileInputStream fis = new FileInputStream(file);
                org.apache.poi.ss.usermodel.Workbook workbook = new XSSFWorkbook(fis)) {

            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
            boolean firstRow = true;
            BigDecimal vatRate = new BigDecimal("1.15"); // 15% VAT

            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                if (firstRow) {
                    firstRow = false;
                    continue; // Skip header
                }

                try {
                    String code = getCellValue(row.getCell(0));
                    String desc = getCellValue(row.getCell(1));
                    String costInclStr = getCellValue(row.getCell(2));
                    String retailInclStr = getCellValue(row.getCell(3));
                    String onHandStr = getCellValue(row.getCell(4));

                    if (code.isEmpty() && desc.isEmpty())
                        continue; // Skip empty rows

                    BigDecimal costIncl = parseCurrency(costInclStr);
                    BigDecimal retailIncl = parseCurrency(retailInclStr);
                    BigDecimal stock = parseCurrency(onHandStr);

                    // Calculate Excl
                    BigDecimal costExcl = costIncl.divide(vatRate, 2, java.math.RoundingMode.HALF_UP);
                    BigDecimal retailExcl = retailIncl.divide(vatRate, 2, java.math.RoundingMode.HALF_UP);

                    // Upsert
                    Product p = productDAO.findByBarcodeOrCode(code);
                    boolean isNew = false;

                    if (p == null) {
                        p = new Product();
                        isNew = true;
                        p.setProductCode(code);
                        p.setDescription(desc.isEmpty() ? "Unknown Product" : desc);
                        p.setCategory("General");
                        p.setServiceItem(false);
                        p.setSupplierId(1);
                        p.setTaxRate(new BigDecimal("15.00"));
                        p.setLowStockThreshold(BigDecimal.ZERO);
                    } else {
                        if (!desc.isEmpty())
                            p.setDescription(desc);
                    }

                    p.setCostPrice(costExcl);
                    p.setPriceRetail(retailExcl);
                    p.setPriceTrade(retailExcl); // Default trade
                    p.setStockOnHand(stock);

                    if (isNew) {
                        productDAO.addProduct(p);
                    } else {
                        productDAO.updateProduct(p);
                    }
                    imported++;
                } catch (Exception e) {
                    errors++;
                    e.printStackTrace();
                }
            }
            loadData();
            showAlert("Import Complete.\nImported/Updated: " + imported + "\nErrors: " + errors);

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Failed to import: " + e.getMessage());
        }
    }

    private String getCellValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null)
            return "";
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    return String.valueOf(cell.getNumericCellValue());
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private BigDecimal parseCurrency(String val) {
        try {
            if (val == null || val.isEmpty())
                return BigDecimal.ZERO;
            val = val.replace("R", "").trim();
            if (val.contains(","))
                val = val.replace(",", ".");
            return new BigDecimal(val);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private String generateUniqueBarcode() {
        java.util.Random random = new java.util.Random();
        String candidate = "";
        boolean unique = false;
        while (!unique) {
            // Generate 6 random digits
            int num = random.nextInt(900000) + 100000; // 100000 to 999999
            candidate = String.valueOf(num);
            if (!productDAO.isBarcodeExists(candidate)) {
                unique = true;
            }
        }
        return candidate;
    }
}
