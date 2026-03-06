package com.malek.pos.controllers;

import com.malek.pos.models.reporting.ReportingDTOs.*;
import com.malek.pos.services.ReportingService;
import com.malek.pos.utils.EventBus;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart; // Keep if utilized or remove unused
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ReportsController {

    @FXML
    private TabPane tabPane;

    // --- Dashboard ---
    @FXML
    private Label lblTodaySales, lblTodayTxCount, lblAvgBasket, lblTodayProfit;
    @FXML
    private TableView<com.malek.pos.models.Product> tblDeadStock;
    @FXML
    private TableColumn<com.malek.pos.models.Product, String> colDeadDesc;
    @FXML
    private TableColumn<com.malek.pos.models.Product, BigDecimal> colDeadStock;

    @FXML
    private TableView<ProductPerformance> tblTopProducts;
    @FXML
    private TableColumn<ProductPerformance, String> colTopDesc;
    @FXML
    private TableColumn<ProductPerformance, BigDecimal> colTopQty;
    @FXML
    private TableColumn<ProductPerformance, BigDecimal> colTopRev;

    // --- Sales Report ---
    @FXML
    private DatePicker dpStart, dpEnd;
    @FXML
    private Label lblReportRevenue, lblReportTax, lblReportProfit;
    @FXML
    private TableView<PaymentMethodSummary> tblSalesBreakdown;
    @FXML
    private TableColumn<PaymentMethodSummary, String> colMethod;
    @FXML
    private TableColumn<PaymentMethodSummary, Integer> colCount;
    @FXML
    private TableColumn<PaymentMethodSummary, BigDecimal> colAmount;

    // --- Layby Reports ---
    @FXML
    private DatePicker dpLaybyStart, dpLaybyEnd;
    @FXML
    private Label lblActiveLaybys, lblValueLocked, lblOutstandingBalance;
    @FXML
    private Label lblNewLaybys, lblCompletedLaybys, lblCancelledLaybys, lblLaybyRevenue;
    @FXML
    private TableView<LaybySummary> tblExpiringLaybys;
    @FXML
    private TableColumn<LaybySummary, String> colRiskId;
    @FXML
    private TableColumn<LaybySummary, String> colRiskCustomer;
    @FXML
    private TableColumn<LaybySummary, LocalDate> colRiskStart;
    @FXML
    private TableColumn<LaybySummary, LocalDate> colRiskExpiry;
    @FXML
    private TableColumn<LaybySummary, BigDecimal> colRiskBalance;

    private final ReportingService service = new ReportingService();

    @FXML
    public void initialize() {
        // Create explicit method for initial dashboard refresh to avoid confusion
        // ... (existing code)

        // Init Defaults
        dpStart.setValue(LocalDate.now());
        dpEnd.setValue(LocalDate.now());
        dpLaybyStart.setValue(LocalDate.now().minusDays(30)); // Default 30 days for layby analysis
        dpLaybyEnd.setValue(LocalDate.now());

        // Setup Tables
        setupDashboardTable();
        setupLaybyTable();

        // Load Initial Data
        refreshDashboard();
        // Don't auto-load filters unless requested, but dashboard is key.
        // Let's load Layby Snapshot on startup too?
        onFilterLayby();

        // Subscribe to Events
        EventBus.subscribe(EventBus.REFRESH_REPORTS, e -> {
            System.out.println("Received Real-Time Report Update...");
            Platform.runLater(() -> {
                refreshDashboard();
                onFilterLayby(); // Auto-refresh Layby too? Yes.
            });
        });
    }

    private void setupLaybyTable() {
        colRiskId.setCellValueFactory(new PropertyValueFactory<>("customId"));
        colRiskCustomer.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        colRiskStart.setCellValueFactory(new PropertyValueFactory<>("startDate"));
        colRiskExpiry.setCellValueFactory(new PropertyValueFactory<>("expiryDate"));
        colRiskBalance.setCellValueFactory(new PropertyValueFactory<>("balance"));

        // Date formatters
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        colRiskStart.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null)
                    setText(null);
                else
                    setText(dtf.format(item));
            }
        });
        colRiskExpiry.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null)
                    setText(null);
                else {
                    setText(dtf.format(item));
                    if (item.isBefore(LocalDate.now().plusDays(3))) {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
    }

    private void setupDashboardTable() {
        // Top Products
        colTopDesc.setCellValueFactory(new PropertyValueFactory<>("description"));
        colTopQty.setCellValueFactory(new PropertyValueFactory<>("quantitySold"));
        colTopRev.setCellValueFactory(new PropertyValueFactory<>("totalRevenue"));

        // Dead Stock
        colDeadDesc.setCellValueFactory(new PropertyValueFactory<>("description"));
        colDeadStock.setCellValueFactory(new PropertyValueFactory<>("stockOnHand"));

        // Sales Report Table
        colMethod.setCellValueFactory(new PropertyValueFactory<>("method"));
        colCount.setCellValueFactory(new PropertyValueFactory<>("count"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("totalAmount"));
    }

    // --- Dashboard Logic ---
    private void refreshDashboard() {
        service.fetchDashboardData(data -> {
            Platform.runLater(() -> {
                // KPIs
                if (data.summary() != null) {
                    lblTodaySales.setText(formatCurrency(data.summary().getTotalSales()));
                    lblTodayTxCount.setText(String.valueOf(data.summary().getTransactionCount()));
                    lblAvgBasket.setText(formatCurrency(data.summary().getAverageBasketValue()));
                    lblTodayProfit.setText(formatCurrency(data.summary().getNetProfit()));
                }

                // Chart Removed - Dead Stock
                tblDeadStock.setItems(FXCollections.observableArrayList(data.deadStock()));

                // Top Products
                tblTopProducts.setItems(FXCollections.observableArrayList(data.topProducts()));
            });
        });
    }

    // --- Sales Report Logic ---
    @FXML
    private void onFilterSales() {
        LocalDate start = dpStart.getValue();
        LocalDate end = dpEnd.getValue();

        if (start == null || end == null) {
            new Alert(Alert.AlertType.ERROR, "Please select both start and end dates.").show();
            return;
        }

        service.fetchSalesSummary(start, end, summary -> {
            Platform.runLater(() -> {
                if (summary != null) {
                    lblReportRevenue.setText(formatCurrency(summary.getTotalSales()));
                    lblReportTax.setText(formatCurrency(summary.getTotalTaxCollected()));
                    lblReportProfit.setText(formatCurrency(summary.getNetProfit()));
                }
            });
        });

        service.fetchPaymentBreakdown(start, end, list -> {
            Platform.runLater(() -> {
                tblSalesBreakdown.setItems(FXCollections.observableArrayList(list));
            });
        });
    }

    @FXML
    private void onRefresh() {
        refreshDashboard();
        onFilterSales();
    }

    @FXML
    private void onExportPDF() {
        LocalDate date = dpStart.getValue();
        if (date == null)
            date = LocalDate.now();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Z-Read Report PDF");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialFileName("ZRead_Report_" + date + ".pdf");

        File file = fileChooser.showSaveDialog(tabPane.getScene().getWindow());

        if (file != null) {
            service.fetchZReadReport(date, report -> {
                Platform.runLater(() -> {
                    com.malek.pos.utils.ReportExporter.exportZReadReportPDF(report, file.getAbsolutePath());
                    new Alert(Alert.AlertType.INFORMATION, "Z-Read Report saved to:\n" + file.getAbsolutePath()).show();
                });
            });
        }
    }

    @FXML
    private void onClose() {
        ((Stage) tabPane.getScene().getWindow()).close();
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null)
            return "R0.00";
        return String.format("R%.2f", amount);
    }

    @FXML
    private void onFilterLayby() {
        LocalDate start = dpLaybyStart.getValue();
        LocalDate end = dpLaybyEnd.getValue();
        if (start == null || end == null)
            return;

        service.fetchDetailedLaybyReport(start, end, report -> {
            Platform.runLater(() -> {
                if (report == null)
                    return;

                // Snapshot
                lblActiveLaybys.setText(String.valueOf(report.getActiveLaybysCount()));
                lblValueLocked.setText(formatCurrency(report.getTotalValueLocked()));
                lblOutstandingBalance.setText(formatCurrency(report.getTotalBalanceOutstanding()));

                // Activity
                lblNewLaybys.setText(String.valueOf(report.getNewLaybysOpened()));
                lblCompletedLaybys.setText(String.valueOf(report.getLaybysCompleted()));
                lblCancelledLaybys.setText(String.valueOf(report.getLaybysCancelled()));

                // Financial
                BigDecimal totalCol = report.getTotalDeposits().add(report.getTotalInstallments());
                lblLaybyRevenue.setText(formatCurrency(totalCol));

                // Watchlist
                tblExpiringLaybys.setItems(FXCollections.observableArrayList(report.getExpiringSoonList()));
            });
        });
    }
}
