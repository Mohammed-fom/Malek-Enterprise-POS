package com.malek.pos.controllers;

import com.malek.pos.database.ConfigManager;
import com.malek.pos.database.DatabaseManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public class SettingsController {

    @FXML
    private TextField txtShopName;
    @FXML
    private TextField txtAddress1;
    @FXML
    private TextField txtAddress2;
    @FXML
    private TextField txtPhone;
    @FXML
    private TextField txtEmail;
    @FXML
    private TextField txtTaxId;

    @FXML
    private TextArea txtFooter;

    // --- Advanced Printing Config ---
    @FXML
    private ComboBox<String> cmbFmtSale, cmbFmtRefund, cmbFmtQuote, cmbFmtLayby;
    @FXML
    private ComboBox<String> cmbPtrSale, cmbPtrRefund, cmbPtrQuote, cmbPtrLayby;
    @FXML
    private ComboBox<String> cmbPtrReport, cmbPtrKick;

    private final ConfigManager config = ConfigManager.getInstance();

    @FXML
    public void initialize() {
        populatePrinters();
        populateFormats();
        loadSettings();
    }

    private void populatePrinters() {
        java.util.Set<javafx.print.Printer> printers = javafx.print.Printer.getAllPrinters();
        java.util.List<String> printerNames = new java.util.ArrayList<>();
        printerNames.add("Default / None");
        for (javafx.print.Printer p : printers) {
            printerNames.add(p.getName());
        }

        setupPrinterCombo(cmbPtrSale, printerNames);
        setupPrinterCombo(cmbPtrRefund, printerNames);
        setupPrinterCombo(cmbPtrQuote, printerNames);
        setupPrinterCombo(cmbPtrLayby, printerNames);
        setupPrinterCombo(cmbPtrReport, printerNames);
        setupPrinterCombo(cmbPtrKick, printerNames);
    }

    private void setupPrinterCombo(ComboBox<String> cmb, java.util.List<String> names) {
        cmb.getItems().setAll(names);
    }

    private void populateFormats() {
        java.util.List<String> formats = java.util.Arrays.asList("THERMAL", "A4", "A5");
        cmbFmtSale.getItems().setAll(formats);
        cmbFmtRefund.getItems().setAll(formats);
        cmbFmtQuote.getItems().setAll(formats);
        cmbFmtLayby.getItems().setAll(formats);
    }

    private void loadSettings() {
        txtShopName.setText(config.getString("company_name", "Malek Enterprise"));
        txtAddress1.setText(config.getString("company_address_1", ""));
        txtAddress2.setText(config.getString("company_address_2", ""));
        txtPhone.setText(config.getString("company_phone", ""));
        txtEmail.setText(config.getString("company_email", ""));
        txtTaxId.setText(config.getString("company_tax_id", ""));
        txtFooter.setText(config.getString("receipt_footer", "Thank you for your support!"));

        // Load Printing Config
        loadPrintConfig("SALE", cmbFmtSale, cmbPtrSale, "THERMAL");
        loadPrintConfig("REFUND", cmbFmtRefund, cmbPtrRefund, "THERMAL");
        loadPrintConfig("QUOTE", cmbFmtQuote, cmbPtrQuote, "A4");
        loadPrintConfig("LAYBY", cmbFmtLayby, cmbPtrLayby, "THERMAL");

        cmbPtrReport.getSelectionModel().select(config.getString("print_ptr_REPORT", "Default / None"));
        cmbPtrKick.getSelectionModel().select(config.getString("print_ptr_KICK", "Default / None"));
    }

    private void loadPrintConfig(String type, ComboBox<String> fmt, ComboBox<String> ptr, String defaultFmt) {
        fmt.getSelectionModel().select(config.getString("print_fmt_" + type, defaultFmt));
        ptr.getSelectionModel().select(config.getString("print_ptr_" + type, "Default / None"));
    }

    @FXML
    private void onImportDatabase() {
        Alert warning = new Alert(Alert.AlertType.WARNING,
                "This will OVERWRITE the current database with the selected backup.\n" +
                        "All current sales, stock, and settings will be replaced.\n\n" +
                        "Are you sure you want to proceed?",
                ButtonType.YES, ButtonType.NO);
        warning.setTitle("Database Import Warning");
        warning.setHeaderText("Overwrite Current Database?");

        Optional<ButtonType> result = warning.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Database Backup");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Database", "*.db"));
            File selectedFile = fileChooser.showOpenDialog(txtShopName.getScene().getWindow());

            if (selectedFile != null) {
                try {
                    // Close current connection
                    DatabaseManager.getInstance().closeConnection();

                    // Copy file
                    File currentDb = new File("pos_enterprise.db");
                    Files.copy(selectedFile.toPath(), currentDb.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    Alert success = new Alert(Alert.AlertType.INFORMATION,
                            "Database imported successfully.\n" +
                                    "The application must now restart to apply changes.");
                    success.showAndWait();

                    System.exit(0);

                } catch (IOException e) {
                    e.printStackTrace();
                    new Alert(Alert.AlertType.ERROR, "Failed to import database: " + e.getMessage()).show();
                }
            }
        }
    }

    @FXML
    private void onSave() {
        config.updateSetting("company_name", txtShopName.getText().trim());
        config.updateSetting("company_address_1", txtAddress1.getText().trim());
        config.updateSetting("company_address_2", txtAddress2.getText().trim());
        config.updateSetting("company_phone", txtPhone.getText().trim());
        config.updateSetting("company_email", txtEmail.getText().trim());
        config.updateSetting("company_tax_id", txtTaxId.getText().trim());
        config.updateSetting("receipt_footer", txtFooter.getText().trim());

        // Save Printing Config
        savePrintConfig("SALE", cmbFmtSale, cmbPtrSale);
        savePrintConfig("REFUND", cmbFmtRefund, cmbPtrRefund);
        savePrintConfig("QUOTE", cmbFmtQuote, cmbPtrQuote);
        savePrintConfig("LAYBY", cmbFmtLayby, cmbPtrLayby);

        config.updateSetting("print_ptr_REPORT", cmbPtrReport.getValue());
        config.updateSetting("print_ptr_KICK", cmbPtrKick.getValue());

        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Settings saved successfully.");
        alert.showAndWait();

        onClose();
    }

    private void savePrintConfig(String type, ComboBox<String> fmt, ComboBox<String> ptr) {
        if (fmt.getValue() != null)
            config.updateSetting("print_fmt_" + type, fmt.getValue());
        if (ptr.getValue() != null)
            config.updateSetting("print_ptr_" + type, ptr.getValue());
    }

    @FXML
    private void onClose() {
        ((Stage) txtShopName.getScene().getWindow()).close();
    }
}
