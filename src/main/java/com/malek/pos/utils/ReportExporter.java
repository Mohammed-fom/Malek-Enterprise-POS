package com.malek.pos.utils;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.malek.pos.models.reporting.ReportingDTOs.SalesSummary;
import com.malek.pos.models.reporting.ReportingDTOs.HourlySales;
import com.malek.pos.models.reporting.ReportingDTOs.ProductPerformance;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ReportExporter {

    public static void exportDailyReportPDF(SalesSummary summary, List<HourlySales> hourly,
            List<ProductPerformance> topProducts, String filePath) {
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, new FileOutputStream(filePath));
            document.open();

            // Header
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph("Daily Sales Report", headerFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph subTitle = new Paragraph(
                    "Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    FontFactory.getFont(FontFactory.HELVETICA, 12));
            subTitle.setAlignment(Element.ALIGN_CENTER);
            document.add(subTitle);
            document.add(new Paragraph(" ")); // Spacer

            // 1. Summary
            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);

            addCell(summaryTable, "Total Revenue:", true);
            addCell(summaryTable, "R" + (summary.getTotalSales() != null ? summary.getTotalSales() : BigDecimal.ZERO),
                    false);

            addCell(summaryTable, "Transactions:", true);
            addCell(summaryTable, String.valueOf(summary.getTransactionCount()), false);

            addCell(summaryTable, "Net Profit:", true);
            addCell(summaryTable, "R" + (summary.getNetProfit() != null ? summary.getNetProfit() : BigDecimal.ZERO),
                    false);

            document.add(summaryTable);
            document.add(new Paragraph(" "));

            // 2. Top Products
            document.add(new Paragraph("Top Performing Products", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            document.add(new Paragraph(" "));

            PdfPTable productTable = new PdfPTable(3);
            productTable.setWidthPercentage(100);
            productTable.setWidths(new int[] { 4, 1, 2 }); // Relative widths

            addCell(productTable, "Product", true);
            addCell(productTable, "Qty", true);
            addCell(productTable, "Revenue", true);

            for (ProductPerformance p : topProducts) {
                addCell(productTable, p.getDescription(), false);
                addCell(productTable, String.valueOf(p.getQuantitySold()), false);
                addCell(productTable, "R" + p.getTotalRevenue(), false);
            }

            document.add(productTable);

            document.close();
            System.out.println("PDF Created: " + filePath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void exportZReadReportPDF(com.malek.pos.models.reporting.ReportingDTOs.ZReadReportDTO report,
            String filePath) {
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, new FileOutputStream(filePath));
            document.open();

            // Header
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph("Z-Read Report", headerFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph subTitle = new Paragraph(
                    "Date: " + report.getReportDate() + "\nGenerated: "
                            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    FontFactory.getFont(FontFactory.HELVETICA, 12));
            subTitle.setAlignment(Element.ALIGN_CENTER);
            document.add(subTitle);
            document.add(new Paragraph(" ")); // Spacer

            // 1. Sales Breakdown
            document.add(new Paragraph("Sales Breakdown", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            PdfPTable salesTable = new PdfPTable(3);
            salesTable.setWidthPercentage(100);
            salesTable.setWidths(new int[] { 3, 1, 2 });

            addCell(salesTable, "Method", true);
            addCell(salesTable, "Count", true);
            addCell(salesTable, "Amount", true);

            addCell(salesTable, "Cash", false);
            addCell(salesTable, String.valueOf(report.getSalesCashCount()), false);
            addCell(salesTable, "R" + report.getSalesCash(), false);

            addCell(salesTable, "Card", false);
            addCell(salesTable, String.valueOf(report.getSalesCardCount()), false);
            addCell(salesTable, "R" + report.getSalesCard(), false);

            addCell(salesTable, "Account", false);
            addCell(salesTable, String.valueOf(report.getSalesAccountCount()), false);
            addCell(salesTable, "R" + report.getSalesAccount(), false);

            // Total Sales Row
            addCell(salesTable, "Total Sales", true);
            addCell(salesTable, String.valueOf(report.getSalesTotalCount()), true);
            addCell(salesTable, "R" + report.getSalesTotal(), true);

            document.add(salesTable);
            document.add(new Paragraph(" "));

            // 2. Refunds Breakdown
            document.add(new Paragraph("Refunds Breakdown", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            PdfPTable refundsTable = new PdfPTable(3);
            refundsTable.setWidthPercentage(100);
            refundsTable.setWidths(new int[] { 3, 1, 2 });

            addCell(refundsTable, "Method", true);
            addCell(refundsTable, "Count", true);
            addCell(refundsTable, "Amount", true);

            addCell(refundsTable, "Cash", false);
            addCell(refundsTable, String.valueOf(report.getRefundsCashCount()), false);
            addCell(refundsTable, "R" + report.getRefundsCash(), false);

            addCell(refundsTable, "Card", false);
            addCell(refundsTable, String.valueOf(report.getRefundsCardCount()), false);
            addCell(refundsTable, "R" + report.getRefundsCard(), false);

            addCell(refundsTable, "Account", false);
            addCell(refundsTable, String.valueOf(report.getRefundsAccountCount()), false);
            addCell(refundsTable, "R" + report.getRefundsAccount(), false);

            // Total Refunds Row
            addCell(refundsTable, "Total Refunds", true);
            addCell(refundsTable, String.valueOf(report.getRefundsTotalCount()), true);
            addCell(refundsTable, "R" + report.getRefundsTotal(), true);

            document.add(refundsTable);
            document.add(new Paragraph(" "));

            // 3. Summary
            document.add(new Paragraph("Financial Summary", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);

            addCell(summaryTable, "Net Sales (Excl Refunds):", true);
            addCell(summaryTable, "R" + report.getNetSales(), false);

            addCell(summaryTable, "Total Tax (" + report.getTaxRate() + "%):", true);
            addCell(summaryTable, "R" + report.getTaxAmount(), false);

            addCell(summaryTable, "Estimated GP:", true);
            addCell(summaryTable, "R" + report.getEstimatedGP(), false);

            addCell(summaryTable, "Cash in Drawer (Theoretical):", true);
            addCell(summaryTable, "R" + report.getCashInDrawer(), false);

            document.add(summaryTable);
            document.add(new Paragraph(" "));

            // Footer Info
            PdfPTable footerTable = new PdfPTable(2);
            footerTable.setWidthPercentage(100);
            addCell(footerTable,
                    "First Sale ID: " + (report.getFirstSaleId() != null ? report.getFirstSaleId() : "N/A"), false);
            addCell(footerTable, "Last Sale ID: " + (report.getLastSaleId() != null ? report.getLastSaleId() : "N/A"),
                    false);
            document.add(footerTable);

            document.close();
            System.out.println("Z-Read PDF Created: " + filePath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void addCell(PdfPTable table, String text, boolean isHeader) {
        PdfPCell cell = new PdfPCell(new Phrase(text, isHeader ? FontFactory.getFont(FontFactory.HELVETICA_BOLD)
                : FontFactory.getFont(FontFactory.HELVETICA)));
        cell.setPadding(5);
        if (isHeader)
            cell.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
        table.addCell(cell);
    }
}
