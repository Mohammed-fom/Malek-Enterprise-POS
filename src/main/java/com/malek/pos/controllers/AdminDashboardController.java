package com.malek.pos.controllers;

import com.malek.pos.database.*;
import com.malek.pos.models.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.TabPane;

import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class AdminDashboardController {

    @FXML
    private javafx.scene.control.TabPane tabPane;

    // --- Tab 1: Dashboard is now handled by DashboardController via fx:include ---

    // --- Tab 2: Users ---
    @FXML
    private TextField txtUsername, txtFullName, txtPin;
    @FXML
    private PasswordField txtPassword;
    @FXML
    private ComboBox<String> cmbRole;
    @FXML
    private CheckBox chkActive;
    @FXML
    private TableView<User> tblUsers;
    @FXML
    private TableColumn<User, Integer> colUid;
    @FXML
    private TableColumn<User, String> colUUser, colUName, colURole;
    @FXML
    private TableColumn<User, Boolean> colUActive;

    // --- Tab 3: Financials ---
    @FXML
    private TextField txtExpDesc, txtExpAmount, txtTaxRate;
    @FXML
    private ComboBox<String> cmbExpCategory;
    @FXML
    private TableView<Expense> tblExpenses;
    @FXML
    private TableColumn<Expense, LocalDateTime> colExpDate;
    @FXML
    private TableColumn<Expense, String> colExpDesc, colExpCat;
    @FXML
    private TableColumn<Expense, BigDecimal> colExpAmt;

    // DAOs
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final UserDAO userDAO = new UserDAO();
    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final ConfigManager configManager = ConfigManager.getInstance();

    private User selectedUser = null;

    @FXML
    private Tab tabUsers, tabFinancials, tabMaintenance;

    @FXML
    public void initialize() {
        // Role Based Access Control
        boolean isAdmin = false;
        if (com.malek.pos.controllers.LoginController.currentUser != null) {
            isAdmin = com.malek.pos.controllers.LoginController.currentUser.isAdmin();
        }

        if (!isAdmin) {
            // Remove User Management tab for non-admins (Managers)
            if (tabUsers != null && tabPane.getTabs().contains(tabUsers)) {
                tabPane.getTabs().remove(tabUsers);
            }
        } else {
            // Users (Only loaded and setup for admins)
            setupUserTable();
            loadUsers();
            cmbRole.setItems(FXCollections.observableArrayList(userDAO.getAllRoles()));
        }

        // Financials
        setupExpenseTable();
        loadExpenses();
        cmbExpCategory.setItems(
                FXCollections.observableArrayList("PETTY_CASH", "UTILITIES", "SUPPLIES", "MAINTENANCE", "OTHER"));
        txtTaxRate.setText(configManager.getString("vat_rate", "15.00"));
    }

    // --- Dashboard Logic: REMOVED ---

    // --- User Logic ---
    private void setupUserTable() {
        colUid.setCellValueFactory(new PropertyValueFactory<>("userId"));
        colUUser.setCellValueFactory(new PropertyValueFactory<>("username"));
        colUName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colURole.setCellValueFactory(new PropertyValueFactory<>("roleName"));
        colUActive.setCellValueFactory(new PropertyValueFactory<>("active"));

        tblUsers.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                selectedUser = newVal;
                txtUsername.setText(newVal.getUsername());
                txtFullName.setText(newVal.getFullName());
                txtPin.setText(newVal.getPinCode());
                cmbRole.setValue(newVal.getRoleName());
                chkActive.setSelected(newVal.isActive());
                txtPassword.clear(); // Don't show hash
            }
        });
    }

    private void loadUsers() {
        tblUsers.setItems(FXCollections.observableArrayList(userDAO.getAllUsers()));
    }

    @FXML
    private void onSaveUser() {
        try {
            if (txtUsername.getText().isEmpty())
                return;

            User u = selectedUser != null ? selectedUser : new User();
            u.setUsername(txtUsername.getText());
            u.setFullName(txtFullName.getText());
            u.setPinCode(txtPin.getText());
            u.setActive(chkActive.isSelected());

            // Password only if changed or new
            if (!txtPassword.getText().isEmpty()) {
                u.setPasswordHash(txtPassword.getText());
            } else if (selectedUser == null) {
                new Alert(Alert.AlertType.ERROR, "Password required for new user").show();
                return;
            }

            // Role ID lookup
            String rName = cmbRole.getValue();
            if (rName != null) {
                u.setRoleId(userDAO.getRoleId(rName));
            } else {
                u.setRoleId(2); // Default
            }

            if (selectedUser == null) {
                userDAO.createUser(u);
            } else {
                userDAO.updateUser(u);
            }
            loadUsers();
            onClearUser();
            new Alert(Alert.AlertType.INFORMATION, "User Saved").show();
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Error: " + e.getMessage()).show();
        }
    }

    @FXML
    private void onClearUser() {
        selectedUser = null;
        txtUsername.clear();
        txtFullName.clear();
        txtPin.clear();
        txtPassword.clear();
        chkActive.setSelected(true);
        tblUsers.getSelectionModel().clearSelection();
    }

    @FXML
    private void onDeleteUser() {
        if (selectedUser == null)
            return;
        try {
            userDAO.deleteUser(selectedUser.getUserId());
            loadUsers();
            onClearUser();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Cannot delete user (likely has linked records)").show();
        }
    }

    // --- Financials Logic ---
    private void setupExpenseTable() {
        colExpDate.setCellValueFactory(new PropertyValueFactory<>("expenseDate"));
        colExpDesc.setCellValueFactory(new PropertyValueFactory<>("description"));
        colExpCat.setCellValueFactory(new PropertyValueFactory<>("category"));
        colExpAmt.setCellValueFactory(new PropertyValueFactory<>("amount"));
    }

    private void loadExpenses() {
        tblExpenses.setItems(FXCollections.observableArrayList(expenseDAO.getAllExpenses()));
    }

    @FXML
    private void onRecordExpense() {
        try {
            BigDecimal amt = new BigDecimal(txtExpAmount.getText());
            String desc = txtExpDesc.getText();
            String cat = cmbExpCategory.getValue();
            if (desc.isEmpty() || cat == null)
                throw new IllegalArgumentException("Missing fields");

            Expense e = new Expense();
            e.setDescription(desc);
            e.setAmount(amt);
            e.setCategory(cat);
            e.setUserId(LoginController.currentUser != null ? LoginController.currentUser.getUserId() : 1);

            expenseDAO.addExpense(e);
            loadExpenses();
            txtExpDesc.clear();
            txtExpAmount.clear();
            new Alert(Alert.AlertType.INFORMATION, "Expense Recorded").show();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Invalid Input").show();
        }
    }

    @FXML
    private void onUpdateTax() {
        configManager.updateSetting("vat_rate", txtTaxRate.getText());
        new Alert(Alert.AlertType.INFORMATION, "Tax Rate Updated. Restart may be required for some modules.").show();
    }

    // --- Maintenance ---
    @FXML
    private void onBackupDatabase() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Backup Directory");
        File selectedDir = dirChooser.showDialog(tabPane.getScene().getWindow());

        if (selectedDir != null) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String backupFileName = "pos_backup_" + timestamp + ".db";
            File sourceFile = new File("pos_enterprise.db");
            File destFile = new File(selectedDir, backupFileName);

            try {
                if (sourceFile.exists()) {
                    Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    new Alert(Alert.AlertType.INFORMATION, "Backup Success!\nSaved to: " + destFile.getAbsolutePath())
                            .show();
                } else {
                    new Alert(Alert.AlertType.ERROR, "Database source file not found!").show();
                }
            } catch (Exception e) {
                e.printStackTrace();
                new Alert(Alert.AlertType.ERROR, "Backup Failed: " + e.getMessage()).show();
            }
        }
    }

    @FXML
    private void onRestoreDatabase() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "WARNING: restoration will OVERWRITE all current data.\n\n" +
                        "A safety backup of the current data will be created automatically before proceeding.\n\n" +
                        "Are you sure you want to continue?",
                ButtonType.YES, ButtonType.CANCEL);
        alert.setHeaderText("restore Database Backup");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.YES) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Backup File (.db)");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Database", "*.db"));
            fileChooser.setInitialDirectory(new File("."));

            File backupFile = fileChooser.showOpenDialog(tabPane.getScene().getWindow());
            if (backupFile != null) {
                boolean success = DatabaseManager.getInstance().restoreBackup(backupFile);
                if (success) {
                    new Alert(Alert.AlertType.INFORMATION,
                            "Restoration Successful!\n\nThe application will now close to ensure data integrity.\nPlease restart manually.",
                            ButtonType.OK).showAndWait();
                    System.exit(0);
                } else {
                    new Alert(Alert.AlertType.ERROR, "Restoration Failed. Check logs.").show();
                }
            }
        }
    }

    @FXML
    private void onExportSales() {
        new Alert(Alert.AlertType.INFORMATION, "CSV Export feature coming soon.").show();
    }

    @FXML
    private void onWastageLog() {
        TextInputDialog idDialog = new TextInputDialog();
        idDialog.setTitle("Log Wastage");
        idDialog.setHeaderText("Enter Product ID or Barcode");
        idDialog.showAndWait().ifPresent(idStr -> {
            // Basic lookup logic needed here or specialized dialog
            // For MVP, assume separate detailed tool or just simple ID entry
            new Alert(Alert.AlertType.INFORMATION, "Wastage logic linked to Stock Screen in future update.").show();
        });
    }

    @FXML
    private void onClose() {
        if (tabPane != null && tabPane.getScene() != null) {
            tabPane.getScene().getWindow().hide();
        }
    }
}
