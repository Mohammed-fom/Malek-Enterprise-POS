package com.malek.pos.controllers;

import com.malek.pos.database.TransactionDAO;
import com.malek.pos.utils.ReceiptService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class ZReadPreviewController {

    @FXML
    private Label lblDateRange;

    @FXML
    private TextArea txtReportPreview;

    private TransactionDAO.DetailedZReadDTO reportData;
    private LocalDate startDate;
    private LocalDate endDate;

    /**
     * Sets the Z Read report data and populates the preview
     */
    public void setReportData(TransactionDAO.DetailedZReadDTO data, LocalDate start, LocalDate end) {
        this.reportData = data;
        this.startDate = start;
        this.endDate = end;

        // Update date range label
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
        lblDateRange.setText("Date Range: " + start.format(formatter) + " - " + end.format(formatter));

        // Format and display the report
        String formattedReport = ReceiptService.formatZReadReportText(data, start, end);
        txtReportPreview.setText(formattedReport);

        // Scroll to top
        txtReportPreview.setScrollTop(0);
    }

    /**
     * Handles the Print button click - sends the report to the printer
     */
    @FXML
    private void onPrint() {
        if (reportData != null && startDate != null && endDate != null) {
            ReceiptService.printDetailedZRead(reportData, startDate, endDate);

            // Close the preview dialog after printing
            onClose();
        }
    }

    /**
     * Handles the Close button click - closes the preview dialog
     */
    @FXML
    private void onClose() {
        Stage stage = (Stage) txtReportPreview.getScene().getWindow();
        stage.close();
    }
}
