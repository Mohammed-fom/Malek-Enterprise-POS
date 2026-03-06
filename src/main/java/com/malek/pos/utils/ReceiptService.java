package com.malek.pos.utils;

import com.malek.pos.database.ConfigManager;
import com.malek.pos.database.DatabaseManager;
import com.malek.pos.models.Transaction;
import com.malek.pos.models.TransactionItem;
import com.malek.pos.models.Debtor;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.control.Alert;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import java.math.BigDecimal;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.BorderWidths;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import javafx.geometry.Insets;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.DocPrintJob;
import javax.print.Doc;
import javax.print.SimpleDoc;
import javax.print.DocFlavor;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;

public class ReceiptService {

    public static void printReceipt(Transaction txn) {
        String type = txn.getTransactionType();
        // Map transaction type to config key suffix (SALE, REFUND, QUOTE)
        String configKey = type.toUpperCase();

        String format = ConfigManager.getInstance().getString("print_fmt_" + configKey, "THERMAL");
        String printerName = ConfigManager.getInstance().getString("print_ptr_" + configKey, "Default / None");

        // Cash Drawer Logic: Trigger if Cash Tendered > 0 AND it's a SALE or REFUND
        if (txn.getTenderCash().compareTo(java.math.BigDecimal.ZERO) > 0) {
            if ("SALE".equalsIgnoreCase(type) || "REFUND".equalsIgnoreCase(type)) {
                openCashDrawer();
            }
        }

        Printer printer = findPrinter(printerName);
        PrinterJob job = PrinterJob.createPrinterJob(printer);

        if (job == null) {
            new Alert(Alert.AlertType.WARNING, "No printer found or failed to create job.").show();
            return;
        }

        // If printer specific logic is needed (e.g. native commands), we rely on job.
        // But for JavaFX printing, we just set the printer.
        job.setPrinter(printer);

        if ("A4".equalsIgnoreCase(format)) {
            printA4Invoice(job, txn);
        } else if ("A5".equalsIgnoreCase(format)) {
            printA5Invoice(job, txn);
        } else {
            printThermalReceipt(job, txn);
        }
    }

    public static Node createReceiptPreview(Transaction txn) {
        String type = txn.getTransactionType();
        String configKey = type.toUpperCase();
        String format = ConfigManager.getInstance().getString("print_fmt_" + configKey, "THERMAL");

        if ("A4".equalsIgnoreCase(format)) {
            return createA4InvoiceNode(txn);
        } else if ("A5".equalsIgnoreCase(format)) {
            return createA5InvoiceNode(txn);
        } else {
            return createThermalReceiptNode(txn);
        }
    }

    private static Printer findPrinter(String name) {
        if (name == null || name.contains("Default")) {
            return Printer.getDefaultPrinter();
        }
        for (Printer p : Printer.getAllPrinters()) {
            if (p.getName().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return Printer.getDefaultPrinter(); // Fallback
    }

    public static VBox createA4InvoiceNode(Transaction txn) {
        // Simple A4 Layout
        VBox root = new VBox(0);
        root.setPrefWidth(500); // Standard A4 width reference for JavaFX (Reduced from 550 to prevent cutoff)
        root.setStyle("-fx-background-color: white; -fx-padding: 30;");

        // 1. Header
        BorderPane header = new BorderPane();
        header.setPadding(new Insets(0, 0, 20, 0));

        VBox brandBox = new VBox(2);
        Label lblBrand = new Label(
                ConfigManager.getInstance().getString("company_name", "MALEK ENTERPRISE").toUpperCase());
        lblBrand.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));

        String addr = ConfigManager.getInstance().getString("company_address_1", "23 Lisbon Street");
        String addr2 = ConfigManager.getInstance().getString("company_address_2", "");
        String tel = ConfigManager.getInstance().getString("company_phone", "072 605 4207");
        String email = ConfigManager.getInstance().getString("company_email", "");
        String vat = ConfigManager.getInstance().getString("company_tax_id", "");

        brandBox.getChildren().add(lblBrand);
        brandBox.getChildren().add(createClassicLabel(addr));
        if (!addr2.isEmpty())
            brandBox.getChildren().add(createClassicLabel(addr2));
        brandBox.getChildren().add(createClassicLabel("Tel: " + tel));
        if (!email.isEmpty())
            brandBox.getChildren().add(createClassicLabel("Email: " + email));
        if (!vat.isEmpty())
            brandBox.getChildren().add(createClassicLabel("VAT: " + vat));

        VBox invoiceDetails = new VBox(2);
        invoiceDetails.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        Label lblTitle = new Label(txn.getTransactionType() + " INVOICE");
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));

        invoiceDetails.getChildren().add(lblTitle);
        invoiceDetails.getChildren().add(createClassicLabel(
                "Date: " + txn.getTransactionDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        invoiceDetails.getChildren().add(createClassicLabel("Inv #: "
                + (txn.getCustomTransactionId() != null ? txn.getCustomTransactionId() : txn.getTransactionId())));
        invoiceDetails.getChildren().add(createClassicLabel("Cashier: " + getUserName(txn.getUserId())));

        header.setLeft(brandBox);
        header.setRight(invoiceDetails);
        root.getChildren().add(header);

        // 2. Customer Info (if exists)
        Debtor debtor = getDebtorDetails(txn.getDebtorId());
        if (debtor != null) {
            VBox custBox = new VBox(2);
            custBox.setPadding(new Insets(0, 0, 20, 0));
            Label lblBillTo = new Label("BILL TO:");
            lblBillTo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            custBox.getChildren().add(lblBillTo);
            custBox.getChildren().add(createClassicLabel(debtor.getCustomerName()));
            if (debtor.getAddress() != null && !debtor.getAddress().isEmpty())
                custBox.getChildren().add(createClassicLabel(debtor.getAddress()));
            if (debtor.getPhone() != null && !debtor.getPhone().isEmpty())
                custBox.getChildren().add(createClassicLabel("Tel: " + debtor.getPhone()));
            root.getChildren().add(custBox);
        }

        // 3. Items Table
        GridPane table = new GridPane();
        table.setPadding(new Insets(10, 0, 10, 0));
        table.setVgap(5);

        // Definitions
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50); // Desc
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(15); // Qty
        ColumnConstraints c3 = new ColumnConstraints();
        c3.setPercentWidth(15); // Price
        ColumnConstraints c4 = new ColumnConstraints();
        c4.setPercentWidth(20); // Total
        table.getColumnConstraints().addAll(c1, c2, c3, c4);

        // Header Row
        Label h1 = new Label("DESCRIPTION");
        h1.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        Label h2 = new Label("QTY");
        h2.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        GridPane.setHalignment(h2, javafx.geometry.HPos.CENTER);
        Label h3 = new Label("PRICE");
        h3.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        GridPane.setHalignment(h3, javafx.geometry.HPos.RIGHT);
        Label h4 = new Label("TOTAL");
        h4.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        GridPane.setHalignment(h4, javafx.geometry.HPos.RIGHT);

        table.add(h1, 0, 0);
        table.add(h2, 1, 0);
        table.add(h3, 2, 0);
        table.add(h4, 3, 0);
        table.add(createSolidLine(500), 0, 1, 4, 1);

        int r = 2;
        for (TransactionItem item : txn.getItems()) {
            Label d = new Label(item.getDescription());
            d.setFont(Font.font("Segoe UI", 10));
            Label q = new Label(String.valueOf(item.getQuantity().intValue()));
            q.setFont(Font.font("Segoe UI", 10));
            GridPane.setHalignment(q, javafx.geometry.HPos.CENTER);
            Label p = new Label(String.format("%.2f", item.unitPriceProperty().get()));
            p.setFont(Font.font("Segoe UI", 10));
            GridPane.setHalignment(p, javafx.geometry.HPos.RIGHT);
            Label t = new Label(String.format("%.2f", item.getTotal()));
            t.setFont(Font.font("Segoe UI", 10));
            GridPane.setHalignment(t, javafx.geometry.HPos.RIGHT);

            table.add(d, 0, r);
            table.add(q, 1, r);
            table.add(p, 2, r);
            table.add(t, 3, r);
            r++;
        }
        table.add(createSolidLine(500), 0, r, 4, 1);
        root.getChildren().add(table);

        // 4. Totals
        GridPane totals = new GridPane();
        totals.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        totals.setHgap(20);
        totals.setVgap(2);

        int tr = 0;
        addTotalRow(totals, "Subtotal", String.format("%.2f", txn.getSubtotal()), tr++, false);
        addTotalRow(totals, "VAT (15%)", String.format("%.2f", txn.getTaxTotal()), tr++, false);
        addTotalRow(totals, "TOTAL", String.format("R %,.2f", txn.getGrandTotal()), tr++, true);

        root.getChildren().add(totals);

        // 5. Footer
        VBox footer = new VBox(5);
        footer.setAlignment(javafx.geometry.Pos.CENTER);
        footer.setPadding(new Insets(30, 0, 0, 0));

        Label lblMsg = new Label(
                ConfigManager.getInstance().getString("receipt_footer", "Thank you for your business!"));
        lblMsg.setFont(Font.font("Segoe UI", javafx.scene.text.FontPosture.ITALIC, 10));

        VBox bankBox = new VBox(2);
        bankBox.setAlignment(javafx.geometry.Pos.CENTER);
        bankBox.getChildren().add(new Label("Banking Details:"));
        bankBox.getChildren().add(new Label("Bank: FNB | Acc: 000000000 | Branch: 250655"));
        bankBox.getChildren().forEach(n -> ((Label) n).setFont(Font.font("Segoe UI", 9)));

        footer.getChildren().addAll(lblMsg, bankBox);
        root.getChildren().add(footer);

        return root;
    }

    private static void printA4Invoice(PrinterJob job, Transaction txn) {
        VBox root = createA4InvoiceNode(txn);
        PageLayout pageLayout = job.getPrinter().createPageLayout(Paper.A4, PageOrientation.PORTRAIT,
                Printer.MarginType.HARDWARE_MINIMUM);
        double scale = (pageLayout.getPrintableWidth() - 40) / root.getPrefWidth();
        if (scale > 1.0)
            scale = 1.0;

        root.getTransforms().add(new Scale(scale, scale));

        double xOffset = (pageLayout.getPrintableWidth() - (root.getPrefWidth() * scale)) / 2;
        root.getTransforms().add(new Translate(xOffset, 0));

        if (job.printPage(pageLayout, root)) {
            job.endJob();
        } else {
            new Alert(Alert.AlertType.ERROR, "Printing Failed").show();
        }
    }

    // Helper methods for Modern Design
    private static void addInfoRow(GridPane grid, String label, String value, int col, int row) {
        VBox box = new VBox(2);
        Label l = new Label(label);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 8));
        l.setTextFill(Color.web("#95a5a6"));

        Label v = new Label(value);
        v.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        v.setTextFill(Color.web("#2c3e50"));

        box.getChildren().addAll(l, v);
        grid.add(box, col, row);
    }

    private static Label styledTableHeader(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        l.setTextFill(Color.web("#7f8c8d"));
        return l;
    }

    private static void addTotalRow(GridPane grid, String label, String value, int row, boolean isGrandTotal) {
        Label l = new Label(label);
        Label v = new Label(value);

        if (isGrandTotal) {
            l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
            v.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
            // Add a line above total
            // grid.add(new Line(0, 0, 100, 0), 1, row); // Removed per user request
        } else {
            l.setFont(Font.font("Segoe UI", 10));
            l.setTextFill(Color.web("#7f8c8d"));
            v.setFont(Font.font("Segoe UI", 10));
        }

        grid.add(l, 0, row);
        grid.add(v, 1, row);
    }

    public static VBox createThermalReceiptNode(Transaction txn) {
        // Simple Thermal Receipt (~80mm)
        double width = 260;
        VBox root = new VBox(0);
        root.setPrefWidth(width);
        root.setStyle("-fx-background-color: white; -fx-padding: 0;");

        // 1. Header
        VBox header = new VBox(2);
        header.setAlignment(javafx.geometry.Pos.CENTER);
        header.setPadding(new Insets(10, 0, 5, 0));

        Label lblShopName = new Label(
                ConfigManager.getInstance().getString("company_name", "MALEK ENTERPRISE").toUpperCase());
        lblShopName.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        lblShopName.setWrapText(true);
        lblShopName.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        String addr = ConfigManager.getInstance().getString("company_address_1", "23 Lisbon Street");
        String addr2 = ConfigManager.getInstance().getString("company_address_2", "");
        String tel = ConfigManager.getInstance().getString("company_phone", "072 605 4207");
        String email = ConfigManager.getInstance().getString("company_email", "");
        String vat = ConfigManager.getInstance().getString("company_tax_id", "");

        header.getChildren().add(lblShopName);
        header.getChildren().add(createClassicLabel(addr));
        if (!addr2.isEmpty())
            header.getChildren().add(createClassicLabel(addr2));
        header.getChildren().add(createClassicLabel("Tel: " + tel));
        if (!email.isEmpty())
            header.getChildren().add(createClassicLabel("Email: " + email));
        if (!vat.isEmpty())
            header.getChildren().add(createClassicLabel("VAT: " + vat));

        root.getChildren().add(header);
        root.getChildren().add(createSolidLine(width));

        // 2. Meta
        GridPane meta = new GridPane();
        meta.setPadding(new Insets(5));
        meta.setHgap(5);
        meta.setVgap(2);

        addModernMetaRow(meta, "Date:", txn.getTransactionDate().format(DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")),
                0);
        addModernMetaRow(meta, "Ref:",
                (txn.getCustomTransactionId() != null ? txn.getCustomTransactionId() : "INV" + txn.getTransactionId()),
                1);
        addModernMetaRow(meta, "Cashier:", getUserName(txn.getUserId()), 2);

        root.getChildren().add(meta);
        root.getChildren().add(createSolidLine(width));

        // 3. Items
        VBox itemsBox = new VBox(4);
        itemsBox.setPadding(new Insets(5));

        for (TransactionItem item : txn.getItems()) {
            VBox row = new VBox(0);
            Label lblDesc = new Label(item.getDescription());
            lblDesc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
            lblDesc.setWrapText(true);

            HBox details = new HBox();
            Label lblQtyPrice = new Label(
                    String.format("%d x %.2f", item.getQuantity().intValue(), item.unitPriceProperty().get()));
            lblQtyPrice.setFont(Font.font("Segoe UI", 9));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label lblTotal = new Label(String.format("%.2f", item.getTotal()));
            lblTotal.setFont(Font.font("Segoe UI", 9));

            details.getChildren().addAll(lblQtyPrice, spacer, lblTotal);
            row.getChildren().addAll(lblDesc, details);
            itemsBox.getChildren().add(row);
        }
        root.getChildren().add(itemsBox);
        root.getChildren().add(createSolidLine(width));

        // 4. Totals
        GridPane totals = new GridPane();
        totals.setPadding(new Insets(5));
        totals.setHgap(10);
        totals.setVgap(2);
        totals.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        int r = 0;
        addModernTotalRow(totals, "Subtotal:", String.format("%.2f", txn.getSubtotal()), r++, false);
        if (txn.getTaxTotal().compareTo(BigDecimal.ZERO) > 0) {
            addModernTotalRow(totals, "VAT (15%):", String.format("%.2f", txn.getTaxTotal()), r++, false);
        }
        addModernTotalRow(totals, "TOTAL:", String.format("%.2f", txn.getGrandTotal()), r++, true);

        if (txn.getChangeDue().compareTo(BigDecimal.ZERO) > 0) {
            addModernTotalRow(totals, "Change:", String.format("%.2f", txn.getChangeDue()), r++, false);
        }

        root.getChildren().add(totals);
        root.getChildren().add(createSolidLine(width));

        // 5. Footer
        VBox footer = new VBox(2);
        footer.setAlignment(javafx.geometry.Pos.CENTER);
        footer.setPadding(new Insets(10, 0, 10, 0));

        Label lblThx = new Label("Thank you for your support!");
        lblThx.setFont(Font.font("Segoe UI", 9));
        Label lblMsg = new Label(ConfigManager.getInstance().getString("receipt_footer", "See you again!"));
        lblMsg.setFont(Font.font("Segoe UI", 9));

        footer.getChildren().addAll(lblThx, lblMsg);
        root.getChildren().add(footer);

        return root;
    }

    // Helper: Solid Line (width)
    private static Node createSolidLine(double width) {
        Line line = new Line(0, 0, width, 0);
        line.setStroke(Color.BLACK);
        line.setStrokeWidth(0.8); // Thin solid line
        return line;
    }

    // Helper: Meta Row
    private static void addModernMetaRow(GridPane grid, String label, String value, int row) {
        Label l = new Label(label);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10)); // Bold Label
        l.setTextFill(Color.BLACK);

        Label v = new Label(value);
        v.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 10)); // Normal Value
        v.setTextFill(Color.BLACK);

        grid.add(l, 0, row);
        grid.add(v, 1, row);
    }

    // Helper: Header Label
    private static Label styledModernHeader(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10)); // Bold Header
        return l;
    }

    // Helper: Total Row
    private static void addModernTotalRow(GridPane grid, String label, String value, int row, boolean isTotal) {
        Label l = new Label(label);
        Label v = new Label(value);

        if (isTotal) {
            // TOTAL: 1450.00
            l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            v.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        } else {
            // Subtotal: ...
            l.setFont(Font.font("Segoe UI_Regular", 10)); // Regular
            v.setFont(Font.font("Segoe UI_Regular", 10));
        }

        grid.add(l, 0, row);
        grid.add(v, 1, row);
    }

    private static void printThermalReceipt(PrinterJob job, Transaction txn) {
        VBox root = createThermalReceiptNode(txn);
        double width = root.getPrefWidth();

        Printer printer = job.getPrinter();
        PageLayout pageLayout = printer.getDefaultPageLayout();
        double printableWidth = pageLayout.getPrintableWidth();

        double scale = printableWidth / width;
        if (scale > 1.0)
            scale = 1.0; // Don't upscale basically

        if (printableWidth < width) {
            root.getTransforms().add(new Scale(scale, scale));
        }

        // Center
        double finalWidth = width * scale;
        if (printableWidth > finalWidth) {
            root.getTransforms().add(new Translate((printableWidth - finalWidth) / 2, 0));
        }

        if (job.printPage(pageLayout, root)) {
            job.endJob();
        } else {
            new Alert(Alert.AlertType.ERROR, "Printing Failed").show();
        }
    }

    private static void center(StringBuilder sb, String text) {
        int width = 32;
        if (text.length() >= width) {
            sb.append(text).append("\n");
            return;
        }
        int padding = (width - text.length()) / 2;
        sb.append(" ".repeat(padding)).append(text).append("\n");
    }

    // Classic Style Helpers
    private static Node createDashedLine(double width) {
        Line line = new Line(0, 0, width, 0);
        line.getStrokeDashArray().addAll(4d, 4d);
        return line;
    }

    private static void addClassicMetaRow(GridPane grid, String label, String value, int row) {
        Label l = new Label(label);
        l.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 8)); // 8px

        Label v = new Label(value);
        v.setFont(Font.font("Segoe UI", FontWeight.BOLD, 8)); // 8px
        v.setWrapText(true);

        grid.add(l, 0, row);
        grid.add(v, 1, row);
    }

    private static Label styledClassicHeader(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 8)); // 8px
        l.setUnderline(true);
        return l;
    }

    private static void addClassicTotalRow(GridPane grid, String label, String value, int row, boolean bold) {
        Label l = new Label(label);
        l.setFont(Font.font("Segoe UI", bold ? FontWeight.BOLD : FontWeight.NORMAL, 8));

        Label v = new Label(value);
        v.setFont(Font.font("Segoe UI", bold ? FontWeight.BOLD : FontWeight.NORMAL, 8));

        grid.add(l, 0, row);
        grid.add(v, 1, row);
    }

    private static void addA4GridCell(GridPane grid, String text, int col, int row, boolean header,
            javafx.geometry.Pos alignment) {
        Label label = new Label(text);
        label.setFont(Font.font("Segoe UI", header ? FontWeight.BOLD : FontWeight.NORMAL, 8)); // 8px
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(alignment);
        label.setPadding(new Insets(3)); // Reduced Padding

        StackPane pane = new StackPane(label);
        pane.setStyle("-fx-border-color: black; -fx-border-width: 0.5;");
        pane.setAlignment(alignment);

        grid.add(pane, col, row);
    }

    private static Label createClassicLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", 8)); // Increased to 8px per user request
        return l;
    }

    public static void printLaybyReceipt(com.malek.pos.models.Layby layby, java.math.BigDecimal amountPaidNow) {
        String format = ConfigManager.getInstance().getString("print_fmt_LAYBY", "THERMAL");
        String printerName = ConfigManager.getInstance().getString("print_ptr_LAYBY", "Default / None");

        Printer printer = findPrinter(printerName);
        PrinterJob job = PrinterJob.createPrinterJob(printer);

        if (job == null) {
            new Alert(Alert.AlertType.WARNING, "No printer found.").show();
            return;
        }
        job.setPrinter(printer);

        if ("A4".equalsIgnoreCase(format)) {
            // Re-using thermal layout for A4 as fallback or implement distinct A4 layby if
            // needed.
            // For now, mapping A4 preference to Thermal Layout scaled unless A4 specific
            // exists.
            // Let's assume A4 Layby uses same layout but A4 paper?
            // Existing logic had printThermalLayby for A4 too:
            // if ("A4".equalsIgnoreCase(type)) { printThermalLaybyReceipt... }

            // Let's allow Basic A4 Layby or Thermal style on A4
            printThermalLaybyReceipt(job, layby, amountPaidNow);
        } else if ("A5".equalsIgnoreCase(format)) {
            printA5LaybyReceipt(job, layby, amountPaidNow);
        } else {
            printThermalLaybyReceipt(job, layby, amountPaidNow);
        }
    }

    private static void printThermalLaybyReceipt(PrinterJob job, com.malek.pos.models.Layby layby,
            java.math.BigDecimal amountPaidNow) {
        // Redesigned Thermal Layby Receipt (Matches 'Wozani' style + Width Fix)
        double width = 240; // Reduced from 280 to prevent cutting

        VBox root = new VBox(0);
        root.setPrefWidth(width);
        root.setStyle("-fx-background-color: white; -fx-padding: 0;");

        // --- 1. Header (Same as Standard Receipt) ---
        VBox header = new VBox(2);
        header.setAlignment(javafx.geometry.Pos.CENTER);
        header.setPadding(new Insets(10, 5, 5, 5));

        Label lblShopName = new Label(
                ConfigManager.getInstance().getString("company_name", "MALEK ENTERPRISE").toUpperCase());
        lblShopName.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        lblShopName.setWrapText(true);
        lblShopName.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        String addressStr = ConfigManager.getInstance().getString("company_address_1", "23 LISBON STREET");
        String address2 = ConfigManager.getInstance().getString("company_address_2", "");
        String tel = ConfigManager.getInstance().getString("company_phone", "072 605 4207");
        String email = ConfigManager.getInstance().getString("company_email", "");

        Label lblAddress = new Label(addressStr);
        lblAddress.setFont(Font.font("Segoe UI", 11));

        header.getChildren().addAll(lblShopName, lblAddress);

        if (!address2.isEmpty()) {
            Label lblAddr2 = new Label(address2);
            lblAddr2.setFont(Font.font("Segoe UI", 11));
            header.getChildren().add(lblAddr2);
        }

        Label lblTel = new Label("Tel: " + tel);
        lblTel.setFont(Font.font("Segoe UI", 11));
        header.getChildren().add(lblTel);

        if (!email.isEmpty()) {
            Label lblEmail = new Label("Email: " + email);
            lblEmail.setFont(Font.font("Segoe UI", 11));
            header.getChildren().add(lblEmail);
        }
        root.getChildren().add(header);

        root.getChildren().add(createSolidLine(width));

        // --- 2. Meta Info (Layby Specific) ---
        GridPane metaGrid = new GridPane();
        metaGrid.setPadding(new Insets(5, 10, 5, 10)); // Indented
        metaGrid.setHgap(10);
        metaGrid.setVgap(2);

        ColumnConstraints colLabel = new ColumnConstraints();
        colLabel.setMinWidth(40);
        ColumnConstraints colValue = new ColumnConstraints();
        metaGrid.getColumnConstraints().addAll(colLabel, colValue);

        addModernMetaRow(metaGrid, "Type:", "** LAYBY **", 0);
        addModernMetaRow(metaGrid, "Date:",
                java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), 1);
        addModernMetaRow(metaGrid, "Ref:", layby.getCustomLaybyId(), 2);
        addModernMetaRow(metaGrid, "Cust:", layby.getCustomerName(), 3);

        if (layby.getExpiryDate() != null) {
            addModernMetaRow(metaGrid, "Exp:", layby.getExpiryDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    4);
        }

        root.getChildren().add(metaGrid);
        root.getChildren().add(createSolidLine(width));

        // --- 3. Items Table ---
        GridPane tableHeader = new GridPane();
        tableHeader.setPadding(new Insets(3, 5, 3, 5));
        tableHeader.getColumnConstraints().addAll(
                new ColumnConstraints(width * 0.45),
                new ColumnConstraints(width * 0.15),
                new ColumnConstraints(width * 0.20),
                new ColumnConstraints(width * 0.20));

        tableHeader.add(styledModernHeader("Item"), 0, 0);
        Label hQty = styledModernHeader("Qty");
        GridPane.setHalignment(hQty, javafx.geometry.HPos.CENTER);
        tableHeader.add(hQty, 1, 0);

        Label hTotal = styledModernHeader("Total");
        GridPane.setHalignment(hTotal, javafx.geometry.HPos.RIGHT);
        tableHeader.add(hTotal, 3, 0);
        // Note: Layby usually doesn't show unit price in list if space is tight, but we
        // can match Sales Receipt
        // Layby items might not have unit price stored directly in TransactionItem
        // depending on how it's fetched,
        // but `TransactionItem` has `unitPrice`. Let's stick to matching columns: Item,
        // Qty, Price, Total.
        // Wait, current logic skipped Price column in "Items (Simplified)". I'll add it
        // back for consistency if space permits.
        // But let's stick to Item, Qty, Total for Layby to ensure it fits well, OR
        // match Sales exactly.
        // User complained "qty and total" missing.
        // Left Price column empty in tableHeader above (col index 2).
        Label hPrice = styledModernHeader("Price");
        GridPane.setHalignment(hPrice, javafx.geometry.HPos.RIGHT);
        tableHeader.add(hPrice, 2, 0);

        root.getChildren().add(tableHeader);
        root.getChildren().add(createSolidLine(width));

        VBox itemsBox = new VBox(2);
        itemsBox.setPadding(new Insets(2, 5, 2, 5));

        if (layby.getItems() != null) {
            for (TransactionItem item : layby.getItems()) {
                String descText = item.getDescription();
                if (descText == null && item.getProduct() != null)
                    descText = item.getProduct().getDescription();
                if (descText == null)
                    descText = "Item";

                GridPane row = new GridPane();
                row.getColumnConstraints().addAll(
                        new ColumnConstraints(width * 0.45),
                        new ColumnConstraints(width * 0.15),
                        new ColumnConstraints(width * 0.20),
                        new ColumnConstraints(width * 0.20));

                Label lblDesc = new Label(descText);
                lblDesc.setFont(Font.font("Segoe UI", 10));
                lblDesc.setWrapText(true);

                Label q = new Label(String.valueOf(item.getQuantity().intValue()));
                q.setFont(Font.font("Segoe UI", 10));
                GridPane.setHalignment(q, javafx.geometry.HPos.CENTER);

                // Price
                Label p = new Label(String.format("%.2f", item.unitPriceProperty().get()));
                p.setFont(Font.font("Segoe UI", 10));
                GridPane.setHalignment(p, javafx.geometry.HPos.RIGHT);

                Label t = new Label(String.format("%.2f", item.getTotal()));
                t.setFont(Font.font("Segoe UI", 10));
                GridPane.setHalignment(t, javafx.geometry.HPos.RIGHT);

                row.add(lblDesc, 0, 0);
                row.add(q, 1, 0);
                row.add(p, 2, 0);
                row.add(t, 3, 0);

                itemsBox.getChildren().add(row);
            }
        }
        root.getChildren().add(itemsBox);
        root.getChildren().add(createSolidLine(width));

        // --- 4. Totals (Layby Specific) ---
        GridPane totals = new GridPane();
        totals.setPadding(new Insets(5, 5, 5, 5));
        totals.setVgap(4);
        totals.setHgap(10);
        totals.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        ColumnConstraints cLabel = new ColumnConstraints();
        cLabel.setHalignment(javafx.geometry.HPos.RIGHT);
        ColumnConstraints cVal = new ColumnConstraints();
        cVal.setHalignment(javafx.geometry.HPos.RIGHT);
        cVal.setMinWidth(80);
        totals.getColumnConstraints().addAll(cLabel, cVal);

        int r = 0;
        addModernTotalRow(totals, "Total Value:", String.format("%.2f", layby.getTotalAmount()), r++, false);

        java.math.BigDecimal paidBefore = layby.getAmountPaid().subtract(amountPaidNow);
        if (paidBefore.compareTo(java.math.BigDecimal.ZERO) > 0)
            addModernTotalRow(totals, "Paid Before:", String.format("%.2f", paidBefore), r++, false);

        addModernTotalRow(totals, "PAID NOW:", String.format("%.2f", amountPaidNow), r++, true); // Bold
        addModernTotalRow(totals, "Total Paid:", String.format("%.2f", layby.getAmountPaid()), r++, false);

        // Balance
        addModernTotalRow(totals, "BALANCE:", String.format("%.2f", layby.getBalance()), r++, true);

        root.getChildren().add(totals);
        root.getChildren().add(createSolidLine(width));

        // --- 5. Footer ---
        VBox footer = new VBox(5);
        footer.setAlignment(javafx.geometry.Pos.CENTER);
        footer.setPadding(new Insets(10, 0, 10, 0));

        if (layby.getBalance().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            Label lblPaid = new Label("*** PAID IN FULL ***");
            lblPaid.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            footer.getChildren().add(lblPaid);
        }

        BorderPane footerMsg = new BorderPane();
        footerMsg.setPadding(new Insets(0, 5, 0, 5));

        Label lblThanks = new Label("Thank you for your support!");
        lblThanks.setFont(Font.font("Segoe UI", 9));
        Label lblTerms = new Label("Terms & Conditions Apply");
        lblTerms.setFont(Font.font("Segoe UI", 9));

        footerMsg.setLeft(lblThanks);
        footerMsg.setRight(lblTerms);

        Label lblSeeYou = new Label("SEE YOU AGAIN !!!!!!!!");
        lblSeeYou.setFont(Font.font("Segoe UI", 10));

        footer.getChildren().addAll(footerMsg, lblSeeYou);
        root.getChildren().add(footer);

        // Print Logic
        Printer printer = job.getPrinter();
        PageLayout pageLayout = printer.getDefaultPageLayout();
        double printableWidth = pageLayout.getPrintableWidth();

        // Scale logic
        double scale = printableWidth / width;
        if (scale > 1.0)
            scale = 1.0;

        // Always apply scale if needed, or if < 1.0
        // The original logic was only if printable < width.
        // But with width=240, it should fit. If printable is 200, it will scale down.
        if (printableWidth < width) {
            root.getTransforms().add(new Scale(scale, scale));
        }

        // Center if printable is larger
        double finalWidth = width * scale;
        if (printableWidth > finalWidth) {
            double xOffset = (printableWidth - finalWidth) / 2;
            root.getTransforms().add(new Translate(xOffset, 0));
        }

        if (job.printPage(pageLayout, root)) {
            job.endJob();
        } else {
            new Alert(Alert.AlertType.ERROR, "Printing Failed").show();
        }
    }

    public static VBox createA5InvoiceNode(Transaction txn) {
        // Simple A5 Layout (Condensed A4)
        VBox root = new VBox(0);
        root.setPrefWidth(350); // Reduced from 400
        root.setStyle("-fx-background-color: white; -fx-padding: 20;");

        // 1. Header
        BorderPane header = new BorderPane();
        header.setPadding(new Insets(0, 0, 10, 0));

        VBox brandBox = new VBox(1);
        Label lblBrand = new Label(
                ConfigManager.getInstance().getString("company_name", "MALEK ENTERPRISE").toUpperCase());
        lblBrand.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));

        String tel = ConfigManager.getInstance().getString("company_phone", "072 605 4207");
        String vat = ConfigManager.getInstance().getString("company_tax_id", "");
        String addr1 = ConfigManager.getInstance().getString("company_address_1", "23 Lisbon Street");
        String addr2 = ConfigManager.getInstance().getString("company_address_2", "");
        String email = ConfigManager.getInstance().getString("company_email", "");

        brandBox.getChildren().add(lblBrand);
        brandBox.getChildren().add(createClassicLabel(addr1));
        if (!addr2.isEmpty())
            brandBox.getChildren().add(createClassicLabel(addr2));
        brandBox.getChildren().add(createClassicLabel("Tel: " + tel));
        if (!email.isEmpty())
            brandBox.getChildren().add(createClassicLabel("Email: " + email));
        if (!vat.isEmpty())
            brandBox.getChildren().add(createClassicLabel("VAT: " + vat));

        VBox invoiceDetails = new VBox(1);
        invoiceDetails.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        Label lblTitle = new Label("TAX INVOICE");
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));

        invoiceDetails.getChildren().add(lblTitle);
        invoiceDetails.getChildren().add(createClassicLabel(
                "Date: " + txn.getTransactionDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        invoiceDetails.getChildren().add(createClassicLabel("Inv #: "
                + (txn.getCustomTransactionId() != null ? txn.getCustomTransactionId() : txn.getTransactionId())));

        header.setLeft(brandBox);
        header.setRight(invoiceDetails);
        root.getChildren().add(header);

        // 2. Customer
        Debtor debtor = getDebtorDetails(txn.getDebtorId());
        if (debtor != null) {
            HBox custBox = new HBox(5);
            custBox.setPadding(new Insets(0, 0, 10, 0));
            Label lblBill = new Label("BILL TO: ");
            lblBill.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
            Label lblName = new Label(debtor.getCustomerName());
            lblName.setFont(Font.font("Segoe UI", 9));
            custBox.getChildren().addAll(lblBill, lblName);
            root.getChildren().add(custBox);
        }

        // 3. Items
        GridPane table = new GridPane();
        table.setPadding(new Insets(5, 0, 5, 0));
        table.setVgap(4);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(15);
        ColumnConstraints c3 = new ColumnConstraints();
        c3.setPercentWidth(35); // Total
        table.getColumnConstraints().addAll(c1, c2, c3);

        Label h1 = new Label("ITEM");
        h1.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
        Label h2 = new Label("QTY");
        h2.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
        GridPane.setHalignment(h2, javafx.geometry.HPos.CENTER);
        Label h3 = new Label("TOTAL");
        h3.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
        GridPane.setHalignment(h3, javafx.geometry.HPos.RIGHT);

        table.add(h1, 0, 0);
        table.add(h2, 1, 0);
        table.add(h3, 2, 0);
        table.add(createSolidLine(350), 0, 1, 3, 1);

        int r = 2;
        for (TransactionItem item : txn.getItems()) {
            Label d = new Label(item.getDescription());
            d.setFont(Font.font("Segoe UI", 9));
            Label q = new Label(String.valueOf(item.getQuantity().intValue()));
            q.setFont(Font.font("Segoe UI", 9));
            GridPane.setHalignment(q, javafx.geometry.HPos.CENTER);
            Label t = new Label(String.format("%.2f", item.getTotal()));
            t.setFont(Font.font("Segoe UI", 9));
            GridPane.setHalignment(t, javafx.geometry.HPos.RIGHT);

            table.add(d, 0, r);
            table.add(q, 1, r);
            table.add(t, 2, r);
            r++;
        }
        table.add(createSolidLine(350), 0, r, 3, 1);
        root.getChildren().add(table);

        // 4. Totals
        GridPane totals = new GridPane();
        totals.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        totals.setHgap(10);
        totals.setVgap(2);

        int tr = 0;
        addTotalRow(totals, "Sub:", String.format("%.2f", txn.getSubtotal()), tr++, false);
        addTotalRow(totals, "VAT:", String.format("%.2f", txn.getTaxTotal()), tr++, false);
        addTotalRow(totals, "TOT:", String.format("R %,.2f", txn.getGrandTotal()), tr++, true); // Bold

        root.getChildren().add(totals);

        // 5. Footer
        VBox footer = new VBox(2);
        footer.setAlignment(javafx.geometry.Pos.CENTER);
        footer.setPadding(new Insets(15, 0, 0, 0));

        Label lblMsg = new Label("Thank you!");
        lblMsg.setFont(Font.font("Segoe UI", javafx.scene.text.FontPosture.ITALIC, 9));
        footer.getChildren().add(lblMsg);

        root.getChildren().add(footer);

        return root;
    }

    private static void printA5Invoice(PrinterJob job, Transaction txn) {
        VBox root = createA5InvoiceNode(txn);
        PageLayout pageLayout = job.getPrinter().createPageLayout(Paper.A5, PageOrientation.PORTRAIT,
                Printer.MarginType.HARDWARE_MINIMUM);

        double scale = (pageLayout.getPrintableWidth() - 20) / root.getPrefWidth();
        if (scale > 1.0)
            scale = 1.0;

        root.getTransforms().add(new Scale(scale, scale));

        double xOffset = (pageLayout.getPrintableWidth() - (root.getPrefWidth() * scale)) / 2;
        root.getTransforms().add(new Translate(xOffset, 0));

        if (job.printPage(pageLayout, root)) {
            job.endJob();
        } else {
            new Alert(Alert.AlertType.ERROR, "Printing Failed").show();
        }
    }

    // DB Helpers
    private static String getUserName(int userId) {
        String sql = "SELECT username FROM users WHERE user_id = ?";
        try (java.sql.Connection conn = DatabaseManager.getInstance().getConnection();
                java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next())
                    return rs.getString("username");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Unknown";
    }

    private static Debtor getDebtorDetails(Integer debtorId) {
        if (debtorId == null || debtorId == 0)
            return null;
        String sql = "SELECT * FROM debtors WHERE debtor_id = ?";
        try (java.sql.Connection conn = DatabaseManager.getInstance().getConnection();
                java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, debtorId);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Debtor d = new Debtor();
                    d.setDebtorId(rs.getInt("debtor_id"));
                    d.setCustomerName(rs.getString("customer_name"));
                    d.setAddress(rs.getString("address"));
                    d.setPhone(rs.getString("phone"));
                    d.setEmail(rs.getString("email"));
                    // Populate other fields if needed, but these are main for receipt
                    return d;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void printA5LaybyReceipt(PrinterJob job, com.malek.pos.models.Layby layby,
            java.math.BigDecimal amountPaidNow) {
        VBox root = new VBox(0);
        root.setPrefWidth(420);
        root.setStyle("-fx-background-color: white; -fx-padding: 20;");

        // 1. Header
        VBox header = new VBox(2);
        header.setAlignment(javafx.geometry.Pos.CENTER);

        Label lblShopName = new Label(ConfigManager.getInstance().getString("company_name", "Malek Enterprise"));
        lblShopName.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        lblShopName.setStyle("-fx-text-fill: #2c3e50;");

        Text txtAddress = new Text(
                ConfigManager.getInstance().getString("company_address_1", "") + "\n" +
                        "Tel: " + ConfigManager.getInstance().getString("company_phone", ""));
        txtAddress.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        txtAddress.setFont(Font.font("Segoe UI", 9));
        txtAddress.setStyle("-fx-fill: #7f8c8d;");

        header.getChildren().addAll(lblShopName, txtAddress);

        // Separator
        Line headerLine = new Line(0, 0, 380, 0);
        headerLine.setStroke(Color.web("#ecf0f1"));
        headerLine.setStrokeWidth(1);

        // 2. Info Grid
        GridPane infoGrid = new GridPane();
        infoGrid.setPadding(new Insets(15, 0, 15, 0));
        infoGrid.setHgap(20);
        infoGrid.setVgap(5);

        addInfoRow(infoGrid, "TYPE", "LAYBY RECEIPT", 0, 0);
        addInfoRow(infoGrid, "DATE",
                java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")), 1, 0);
        addInfoRow(infoGrid, "LAYBY #", layby.getCustomLaybyId(), 0, 1);
        addInfoRow(infoGrid, "CUSTOMER", layby.getCustomerName(), 1, 1);

        if (layby.getExpiryDate() != null) {
            addInfoRow(infoGrid, "EXPIRY", layby.getExpiryDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy")), 0,
                    2);
        }

        // 3. Items Table
        VBox tableContainer = new VBox(0);
        tableContainer.setPadding(new Insets(5, 0, 5, 0));

        GridPane tableHeader = new GridPane();
        tableHeader.setPadding(new Insets(5));
        tableHeader.setBackground(
                new Background(new BackgroundFill(Color.web("#f8f9fa"), new CornerRadii(4), Insets.EMPTY)));

        ColumnConstraints colDesc = new ColumnConstraints();
        colDesc.setPercentWidth(55);
        ColumnConstraints colQty = new ColumnConstraints();
        colQty.setPercentWidth(15);
        ColumnConstraints colTotal = new ColumnConstraints();
        colTotal.setPercentWidth(30);

        tableHeader.getColumnConstraints().addAll(colDesc, colQty, colTotal);
        tableHeader.add(styledTableHeader("ITEM"), 0, 0);
        tableHeader.add(styledTableHeader("QTY"), 1, 0);
        tableHeader.add(styledTableHeader("TOTAL"), 2, 0);

        tableContainer.getChildren().add(tableHeader);

        if (layby.getItems() != null) {
            for (TransactionItem item : layby.getItems()) {
                GridPane row = new GridPane();
                row.setPadding(new Insets(6));
                row.getColumnConstraints().addAll(colDesc, colQty, colTotal);

                row.setBorder(new Border(
                        new BorderStroke(Color.TRANSPARENT, Color.TRANSPARENT, Color.web("#ecf0f1"), Color.TRANSPARENT,
                                BorderStrokeStyle.SOLID, BorderStrokeStyle.SOLID, BorderStrokeStyle.SOLID,
                                BorderStrokeStyle.SOLID,
                                CornerRadii.EMPTY, new BorderWidths(1), Insets.EMPTY)));

                String d = item.getDescription() != null ? item.getDescription()
                        : (item.getProduct() != null ? item.getProduct().getDescription() : "Item");
                if (d.length() > 25)
                    d = d.substring(0, 25);

                Label lblDesc = new Label(d);
                lblDesc.setFont(Font.font("Segoe UI", 9));
                Label lblQty = new Label(item.getQuantity().toString());
                lblQty.setFont(Font.font("Segoe UI", 9));
                Label lblTotal = new Label(String.format("%.2f", item.getTotal()));
                lblTotal.setFont(Font.font("Segoe UI", 9));

                row.add(lblDesc, 0, 0);
                row.add(lblQty, 1, 0);
                row.add(lblTotal, 2, 0);

                tableContainer.getChildren().add(row);
            }
        }

        // 4. Totals
        VBox bottomSection = new VBox(15);
        bottomSection.setPadding(new Insets(15, 0, 0, 0));

        GridPane totalsGrid = new GridPane();
        totalsGrid.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        totalsGrid.setHgap(15);
        totalsGrid.setVgap(4);

        addTotalRow(totalsGrid, "Total Value:", String.format("%.2f", layby.getTotalAmount()), 0, false);

        java.math.BigDecimal paidBefore = layby.getAmountPaid().subtract(amountPaidNow);
        if (paidBefore.compareTo(java.math.BigDecimal.ZERO) > 0) {
            addTotalRow(totalsGrid, "Paid Before:", String.format("%.2f", paidBefore), 1, false);
        }

        addTotalRow(totalsGrid, "PAID NOW:", String.format("%.2f", amountPaidNow), 2, false);

        // Bold Totals
        Label lblTotalPaidTitle = new Label("TOTAL PAID:");
        lblTotalPaidTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        Label lblTotalPaidVal = new Label(String.format("%.2f", layby.getAmountPaid()));
        lblTotalPaidVal.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        totalsGrid.add(lblTotalPaidTitle, 0, 3);
        totalsGrid.add(lblTotalPaidVal, 1, 3);

        Label lblBalanceTitle = new Label("BALANCE DUE:");
        lblBalanceTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        Label lblBalanceVal = new Label(String.format("%.2f", layby.getBalance()));
        lblBalanceVal.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        totalsGrid.add(lblBalanceTitle, 0, 4);
        totalsGrid.add(lblBalanceVal, 1, 4);

        // Footer
        VBox footerBox = new VBox(5);
        footerBox.setAlignment(javafx.geometry.Pos.CENTER);

        if (layby.getBalance().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            Label paid = new Label("*** PAID IN FULL ***");
            paid.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
            footerBox.getChildren().add(paid);
        }

        Label lblFooter = new Label(ConfigManager.getInstance().getString("receipt_footer", "Thank you!"));
        lblFooter.setFont(Font.font("Segoe UI", javafx.scene.text.FontPosture.ITALIC, 10));
        lblFooter.setTextFill(Color.web("#7f8c8d"));
        footerBox.getChildren().add(lblFooter);

        bottomSection.getChildren().addAll(totalsGrid, footerBox);

        root.getChildren().addAll(header, new Label(" "), headerLine, infoGrid, tableContainer, bottomSection);

        PageLayout pageLayout = job.getPrinter().createPageLayout(Paper.A5, PageOrientation.PORTRAIT,
                Printer.MarginType.HARDWARE_MINIMUM);
        double printableWidth = pageLayout.getPrintableWidth();

        double scale = printableWidth / root.getPrefWidth();
        root.getTransforms().add(new Scale(scale, scale));

        // Center
        double finalWidth = root.getPrefWidth() * scale;
        if (printableWidth > finalWidth) {
            double xOffset = (printableWidth - finalWidth) / 2;
            root.getTransforms().add(new Translate(xOffset, 0));
        }

        if (job.printPage(pageLayout, root))
            job.endJob();
        else
            new Alert(Alert.AlertType.ERROR, "Printing Failed").show();
    }

    private static void openCashDrawer() {
        try {
            // Standard ESC/POS kick command (Pin 2: 27, 112, 0, 25, 250)
            byte[] open = { 27, 112, 0, 25, (byte) 250 };

            String ptrName = ConfigManager.getInstance().getString("print_ptr_KICK", "Default / None");
            PrintService service = null;

            if (ptrName.contains("Default")) {
                service = PrintServiceLookup.lookupDefaultPrintService();
            } else {
                PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
                for (PrintService ps : services) {
                    if (ps.getName().equalsIgnoreCase(ptrName)) {
                        service = ps;
                        break;
                    }
                }
            }

            if (service != null) {
                DocPrintJob job = service.createPrintJob();
                DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
                Doc doc = new SimpleDoc(open, flavor, null);
                job.print(doc, null);
                System.out.println("Drawer Kick Command Sent to: " + service.getName());
            } else {
                System.out.println("No Print Service found for Drawer Kick (Config: " + ptrName + ")");
            }
        } catch (Exception e) {
            System.err.println("Failed to open cash drawer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Formats the Z Read report data into a text string for preview or printing
     * 
     * @param data  Z Read data
     * @param start Start date
     * @param end   End date
     * @return Formatted report text
     */
    public static String formatZReadReportText(com.malek.pos.database.TransactionDAO.DetailedZReadDTO data,
            java.time.LocalDate start, java.time.LocalDate end) {
        StringBuilder sb = new StringBuilder();

        // 1. Header
        String companyName = ConfigManager.getInstance().getString("company_name", "MALEK ENTERPRISE");
        center(sb, companyName);
        center(sb, "PERIOD SALES REPORT");
        sb.append("\n");
        sb.append(String.format("From: %-10s  To: %-10s\n",
                start.format(DateTimeFormatter.ofPattern("dd MMM yy")),
                end.format(DateTimeFormatter.ofPattern("dd MMM yy"))));
        sb.append("\n");

        // 2. Sales Breakdown
        sb.append("Type              Count      Amount\n");
        sb.append("-".repeat(38)).append("\n");
        sb.append("Sales\n");

        appendRow(sb, "  Account", data.accountSalesCount(), data.accountSalesAmount());
        appendRow(sb, "  Cash", data.cashSalesCount(), data.cashSalesAmount());
        appendRow(sb, "  Card", data.cardSalesCount(), data.cardSalesAmount());
        appendRow(sb, "  Bank Transfer", data.bankSalesCount(), data.bankSalesAmount());
        sb.append("  " + "-".repeat(34)).append("\n");
        appendRow(sb, "+ Total Sales", data.totalSalesCount(), data.totalSales());
        sb.append("\n");

        // 3. Refunds
        sb.append("Refunds\n");
        appendRow(sb, "  Account", data.accountRefundCount(), data.accountRefundAmount());
        appendRow(sb, "  Cash", data.cashRefundCount(), data.cashRefundAmount());
        appendRow(sb, "  Card", data.cardRefundCount(), data.cardRefundAmount());
        appendRow(sb, "  Bank Transfer", data.bankRefundCount(), data.bankRefundAmount());
        sb.append("  " + "-".repeat(34)).append("\n");
        appendRow(sb, "- Total Refunds", data.totalRefundCount(), data.totalRefunds());
        sb.append("\n");

        // 4. Net Sales
        BigDecimal netSales = data.totalSales().subtract(data.totalRefunds());
        int netCount = data.totalSalesCount() - data.totalRefundCount();
        appendRow(sb, "Nett Sales", netCount, netSales);
        sb.append("\n");

        // 5. Other Transactions - Tender Breakdown
        sb.append("Other Transactions\n");
        sb.append("Bankable\n");
        appendRow(sb, "  Cash", data.cashSalesCount(), data.cashSalesAmount());
        sb.append("  " + "-".repeat(34)).append("\n");
        appendRow(sb, "  Bankable Total", data.cashSalesCount(), data.cashSalesAmount());
        sb.append("\n");

        sb.append("Non Bankable\n");
        int nonBankCount = data.cardSalesCount() + data.bankSalesCount();
        BigDecimal nonBankTotal = data.cardSalesAmount().add(data.bankSalesAmount());
        appendRow(sb, "  CARD", data.cardSalesCount(), data.cardSalesAmount());
        appendRow(sb, "  Bank Transfer", data.bankSalesCount(), data.bankSalesAmount());
        sb.append("  " + "-".repeat(34)).append("\n");
        appendRow(sb, "  Non Bankable Total", nonBankCount, nonBankTotal);
        sb.append("\n");

        BigDecimal allTenders = data.cashSalesAmount().add(data.cardSalesAmount()).add(data.bankSalesAmount())
                .add(data.accountSalesAmount());
        int allTendersCount = data.cashSalesCount() + data.cardSalesCount() + data.bankSalesCount()
                + data.accountSalesCount();
        appendRow(sb, "Total Tenders", allTendersCount, allTenders);
        sb.append("\n");
        sb.append("-".repeat(38)).append("\n");

        // 6. Tax Breakdown
        sb.append("Tax Breakdown\n");
        sb.append("Sales Amount (Incl):\n");
        appendRow(sb, "  Rate 1 (15.00%)", netCount, netSales);
        sb.append("  " + "-".repeat(34)).append("\n");
        appendRow(sb, "  Total Amount (incl)", netCount, netSales);
        sb.append("\n");

        sb.append("Tax on Sales:\n");
        appendRow(sb, "  Rate 1 (15.00%)", netCount, data.totalTax());
        sb.append("  " + "-".repeat(34)).append("\n");
        appendRow(sb, "  Tax Total", netCount, data.totalTax());
        sb.append("\n");

        // 7. Estimated GP (calculated if cost data available)
        // For now showing placeholder - you'd need cost tracking
        BigDecimal estimatedGP = netSales.multiply(new BigDecimal("0.23")); // Placeholder 23% margin
        sb.append(String.format("Estimated GP (23.00%%): R %,.2f\n", estimatedGP));
        sb.append("\n");

        // Footer
        center(sb, "*** END OF REPORT ***");
        sb.append("\n(Printed: ")
                .append(java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))).append(")");

        return sb.toString();
    }

    public static void printDetailedZRead(com.malek.pos.database.TransactionDAO.DetailedZReadDTO data,
            java.time.LocalDate start, java.time.LocalDate end) {
        String printerName = ConfigManager.getInstance().getString("print_ptr_SALE", "Default / None");
        Printer printer = findPrinter(printerName);
        PrinterJob job = PrinterJob.createPrinterJob(printer);

        if (job == null) {
            new Alert(Alert.AlertType.WARNING, "No printer found.").show();
            return;
        }
        job.setPrinter(printer);

        // Get formatted text using the shared formatting method
        String reportText = formatZReadReportText(data, start, end);

        // Text-based Layout using Monospaced Font
        javafx.scene.text.TextFlow flow = new javafx.scene.text.TextFlow();
        String fontName = "Courier New";
        double fontSize = 9;
        Font font = Font.font(fontName, FontWeight.NORMAL, fontSize);

        Text textNode = new Text(reportText);
        textNode.setFont(font);
        flow.getChildren().add(textNode);

        // Wrap in VBox for margins
        VBox root = new VBox(flow);
        root.setPadding(new Insets(10));
        root.setPrefWidth(300);

        // Print
        PageLayout pageLayout = printer.getDefaultPageLayout();
        double printableWidth = pageLayout.getPrintableWidth();
        double scale = printableWidth / root.getPrefWidth();
        if (scale > 1.0)
            scale = 1.0;
        if (root.getPrefWidth() * scale > printableWidth) {
            scale = printableWidth / root.getPrefWidth();
        }
        root.getTransforms().add(new Scale(scale, scale));

        if (job.printPage(pageLayout, root)) {
            job.endJob();
        } else {
            new Alert(Alert.AlertType.ERROR, "Printing Failed").show();
        }
    }

    private static void appendRow(StringBuilder sb, String label, int count, BigDecimal amount) {
        // Format: Label (left) Count (right-ish) Amount (right)
        // Max width ~38 chars for thermal
        String countStr = String.valueOf(count);
        String amountStr = String.format("R %,11.2f", amount);

        sb.append(String.format("%-18s %5s %s\n", label, countStr, amountStr));
    }

    // ==========================================
    // DAILY REPORT PRINTING
    // ==========================================

    public static void printDailyReportA4(com.malek.pos.database.TransactionDAO.DetailedZReadDTO salesData,
            com.malek.pos.database.LaybyDAO.LaybyReportDTO laybyData,
            java.time.LocalDate date) {
        Printer printer = Printer.getDefaultPrinter();
        PrinterJob job = PrinterJob.createPrinterJob(printer);
        if (job != null) {
            job.setPrinter(printer);
            printReportLayout(job, salesData, laybyData, date, "A4");
        }
    }

    public static void printDailyReportA5(com.malek.pos.database.TransactionDAO.DetailedZReadDTO salesData,
            com.malek.pos.database.LaybyDAO.LaybyReportDTO laybyData,
            java.time.LocalDate date) {
        Printer printer = Printer.getDefaultPrinter();
        PrinterJob job = PrinterJob.createPrinterJob(printer);
        if (job != null) {
            job.setPrinter(printer);
            printReportLayout(job, salesData, laybyData, date, "A5");
        }
    }

    public static void printDailyReportThermal(com.malek.pos.database.TransactionDAO.DetailedZReadDTO salesData,
            com.malek.pos.database.LaybyDAO.LaybyReportDTO laybyData,
            java.time.LocalDate date) {
        Printer printer = Printer.getDefaultPrinter();
        PrinterJob job = PrinterJob.createPrinterJob(printer);
        if (job != null) {
            job.setPrinter(printer);
            printReportLayout(job, salesData, laybyData, date, "THERMAL");
        }
    }

    private static void printReportLayout(PrinterJob job,
            com.malek.pos.database.TransactionDAO.DetailedZReadDTO salesData,
            com.malek.pos.database.LaybyDAO.LaybyReportDTO laybyData,
            java.time.LocalDate date, String format) {

        double width = 550; // A4 Default
        if ("A5".equalsIgnoreCase(format))
            width = 400;
        if ("THERMAL".equalsIgnoreCase(format))
            width = 280;

        VBox root = new VBox(10);
        root.setPrefWidth(width);
        root.setStyle("-fx-padding: 15; -fx-background-color: white;");

        // Header
        VBox header = new VBox(2);
        header.setAlignment(javafx.geometry.Pos.CENTER);
        Label lblTitle = new Label("DAILY TRANSACTION REPORT");
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, "THERMAL".equals(format) ? 12 : 16));
        Label lblDate = new Label("Date: " + date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        header.getChildren().addAll(lblTitle, lblDate);

        // Sections
        VBox content = new VBox(15);
        content.getChildren().add(createSection("SALES SUMMARY", width, format,
                new String[] { "Total Revenue", "Count", "Cash", "Card", "Bank Transfer", "Account" },
                new String[] {
                        formatMoney(salesData.totalSales()),
                        String.valueOf(salesData.totalSalesCount()),
                        formatMoney(salesData.cashSalesAmount()),
                        formatMoney(salesData.cardSalesAmount()),
                        formatMoney(salesData.bankSalesAmount()),
                        formatMoney(salesData.accountSalesAmount())
                }));

        content.getChildren().add(createSection("REFUNDS", width, format,
                new String[] { "Total Refunded", "Count", "Cash Refunds", "Card Refunds", "Bank Refunds" },
                new String[] {
                        formatMoney(salesData.totalRefunds()),
                        String.valueOf(salesData.totalRefundCount()),
                        formatMoney(salesData.cashRefundAmount()),
                        formatMoney(salesData.cardRefundAmount()),
                        formatMoney(salesData.bankRefundAmount())
                }));

        content.getChildren().add(createSection("LAYBYS", width, format,
                new String[] { "New Laybys", "New Value", "Payments Rx", "Payments Val" },
                new String[] {
                        String.valueOf(laybyData.newLaybysCount()),
                        formatMoney(laybyData.newLaybysTotalValue()),
                        String.valueOf(laybyData.paymentsReceivedCount()),
                        formatMoney(laybyData.paymentsReceivedTotalValue())
                }));

        // Footer
        VBox footer = new VBox(5);
        footer.setAlignment(javafx.geometry.Pos.CENTER);
        footer.getChildren()
                .add(new Label("Printed: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))));

        root.getChildren().addAll(header, new Separator(), content, new Separator(), footer);

        // Scaling
        PageLayout pageLayout = job.getPrinter().getDefaultPageLayout();
        if ("A4".equalsIgnoreCase(format)) {
            pageLayout = job.getPrinter().createPageLayout(Paper.A4, PageOrientation.PORTRAIT,
                    Printer.MarginType.DEFAULT);
        } else if ("A5".equalsIgnoreCase(format)) {
            pageLayout = job.getPrinter().createPageLayout(Paper.A5, PageOrientation.PORTRAIT,
                    Printer.MarginType.DEFAULT);
        }

        double scale = pageLayout.getPrintableWidth() / width;
        if (scale < 1.0 || "THERMAL".equalsIgnoreCase(format)) {
            root.getTransforms().add(new Scale(scale, scale));
        }

        if (job.printPage(pageLayout, root)) {
            job.endJob();
        } else {
            new Alert(Alert.AlertType.ERROR, "Printing Failed").show();
        }
    }

    private static javafx.scene.Node createSection(String title, double width, String format, String[] labels,
            String[] values) {
        VBox box = new VBox(5);
        Label userLbl = new Label(title);
        userLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, "THERMAL".equals(format) ? 10 : 12));
        box.getChildren().add(userLbl);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(2);

        for (int i = 0; i < labels.length; i++) {
            grid.add(new Label(labels[i] + ":"), 0, i);
            Label val = new Label(values[i]);
            val.setStyle("-fx-font-weight: bold;");
            grid.add(val, 1, i);
        }
        box.getChildren().add(grid);
        return box;
    }

    private static String formatMoney(BigDecimal amt) {
        if (amt == null)
            return "R 0.00";
        return "R " + amt.setScale(2, java.math.RoundingMode.HALF_UP).toString();
    }

    // Helper class for Separator (standard JavaFX Separator might not print well
    // without CSS, using Line)
    private static class Separator extends VBox {
        public Separator() {
            super();
            setPadding(new Insets(5, 0, 5, 0));
            Line line = new Line(0, 0, 100, 0); // width handled by layout
            line.setStroke(Color.GRAY);
            line.endXProperty().bind(widthProperty());
            getChildren().add(line);
        }
    }
}
