package com.malek.pos.controllers;

import com.malek.pos.database.DocumentSearchDAO;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;

import javafx.stage.Stage;

public class UniversalSearchController {

    @FXML
    private TextField txtSearch;
    @FXML
    private TableView<DocumentSearchDAO.SearchResult> tblResults;

    @FXML
    private TableColumn<DocumentSearchDAO.SearchResult, String> colType;
    @FXML
    private TableColumn<DocumentSearchDAO.SearchResult, String> colId;
    @FXML
    private TableColumn<DocumentSearchDAO.SearchResult, String> colDesc;
    @FXML
    private TableColumn<DocumentSearchDAO.SearchResult, String> colDate;
    @FXML
    private TableColumn<DocumentSearchDAO.SearchResult, Double> colAmount;
    @FXML
    private TableColumn<DocumentSearchDAO.SearchResult, String> colStatus;

    private final DocumentSearchDAO dao = new DocumentSearchDAO();

    @FXML
    public void initialize() {
        colType.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().type()));
        colId.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().displayId()));
        colDesc.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().description()));
        colDate.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().date()));
        colAmount.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().amount()));
        colStatus.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().status()));

        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.length() > 2)
                doSearch(newVal);
        });

        // Double click
        tblResults.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && tblResults.getSelectionModel().getSelectedItem() != null) {
                onOpen();
            }
        });

        // Close on ESC
        txtSearch.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE)
                onClose();
            if (e.getCode() == KeyCode.DOWN)
                tblResults.requestFocus();
        });
        tblResults.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE)
                onClose();
            if (e.getCode() == KeyCode.ENTER)
                onOpen();
        });

        // Subscribe to Refresh
        com.malek.pos.utils.EventBus.subscribe(com.malek.pos.utils.EventBus.REFRESH_HISTORY, e -> {
            String q = txtSearch.getText();
            if (q != null && q.length() > 2) {
                doSearch(q);
            }
        });
    }

    private void doSearch(String query) {
        tblResults.setItems(FXCollections.observableArrayList(dao.search(query)));
    }

    @FXML
    private void onOpen() {
        DocumentSearchDAO.SearchResult selected = tblResults.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;

        try {
            if ("LAYBY".equals(selected.type())) {
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                        getClass().getResource("/layby_management.fxml"));
                javafx.scene.Parent page = loader.load();
                Stage stage = new Stage();
                stage.setTitle("Layby Management - " + selected.displayId());
                stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
                stage.initOwner(txtSearch.getScene().getWindow());
                stage.setScene(new javafx.scene.Scene(page));

                // Close on ESC
                stage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.ESCAPE)
                        stage.close();
                });

                stage.show();

            } else if ("PRODUCT".equals(selected.type())) {
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                        getClass().getResource("/stock_screen.fxml"));
                javafx.scene.Parent page = loader.load();
                StockController controller = loader.getController();

                Stage stage = new Stage();
                stage.setTitle("Stock Master File - " + selected.description());
                stage.initModality(javafx.stage.Modality.NONE);
                stage.setScene(new javafx.scene.Scene(page));

                // Close on ESC
                stage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.ESCAPE)
                        stage.close();
                });

                stage.show();
                // Set Search after show to ensure UI loaded, though not strictly required for
                // logic
                controller.setSearch(selected.displayId()); // Search by Code

            } else if ("DEBTOR".equals(selected.type())) {
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                        getClass().getResource("/debtors_screen.fxml"));
                javafx.scene.Parent page = loader.load();
                DebtorsController controller = loader.getController();

                Stage stage = new Stage();
                stage.setTitle("Debtors Master File - " + selected.description());
                stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
                stage.initOwner(txtSearch.getScene().getWindow());
                stage.setScene(new javafx.scene.Scene(page));

                // Close on ESC
                stage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.ESCAPE)
                        stage.close();
                });

                stage.show();
                controller.setSearch(selected.displayId()); // Search by Account No

            } else {
                // Open Transaction History filtered to this ID
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                        getClass().getResource("/transaction_history.fxml"));
                javafx.scene.Parent page = loader.load();

                TransactionHistoryController c = loader.getController();
                c.setSearch(selected.displayId());

                Stage stage = new Stage();
                stage.setTitle("Transaction Details - " + selected.displayId());
                stage.initModality(javafx.stage.Modality.NONE); // Non-modal
                stage.setScene(new javafx.scene.Scene(page));

                // Close on ESC
                stage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.ESCAPE)
                        stage.close();
                });

                stage.show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Error opening document details: " + e.getMessage()).show();
        }
    }

    @FXML
    private void onClose() {
        txtSearch.getScene().getWindow().hide();
    }
}
