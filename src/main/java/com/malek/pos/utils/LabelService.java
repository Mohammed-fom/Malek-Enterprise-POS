package com.malek.pos.utils;

import com.malek.pos.models.Product;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Scale;

public class LabelService {

    public static void printLabel(Product product, LabelConfigDialog.LabelConfig config) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(null)) {
            Node labelNode = createLabelNode(product, config);

            // Attempt to configure paper size if possible, otherwise rely on scale
            // Custom paper sizes are tricky in JavaFX, often best to just print to default
            // and scale content
            PageLayout layout = job.getPrinter().createPageLayout(Paper.NA_LETTER, PageOrientation.PORTRAIT,
                    Printer.MarginType.HARDWARE_MINIMUM);

            // Calculate scale to fit the config width/height into points (1mm = 2.83465
            // points)
            double targetWidthPts = config.widthMm * 2.83465;
            double targetHeightPts = config.heightMm * 2.83465;

            // For roll printers, the "page" might be the label size.
            // We'll scale the node to the target size.
            // labelNode is built with pixel-based sizes roughly mapping to points?
            // Let's create the node with preferred size in pixels/points.

            boolean success = true;
            for (int i = 0; i < config.quantity; i++) {
                if (!job.printPage(labelNode)) {
                    success = false;
                    break;
                }
            }

            if (success) {
                job.endJob();
            } else {
                new Alert(Alert.AlertType.ERROR, "Failed to print label").show();
                job.endJob(); // Ensure job is closed even on failure if started
            }
        }
    }

    private static VBox createLabelNode(Product product, LabelConfigDialog.LabelConfig config) {
        double widthPts = config.widthMm * 2.83465;
        double heightPts = config.heightMm * 2.83465;

        VBox root = new VBox(2);
        root.setPrefSize(widthPts, heightPts);
        root.setMaxSize(widthPts, heightPts);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: white; -fx-padding: 2;");

        // Description (Truncate if too long)
        Label lblDesc = new Label(product.getDescription());
        lblDesc.setFont(Font.font("System", FontWeight.BOLD, 10)); // Adjust font size based on height?
        lblDesc.setWrapText(true);
        lblDesc.setTextAlignment(TextAlignment.CENTER);
        lblDesc.setMaxWidth(widthPts - 4);

        // QR Code
        // Size it to fit available space - approx 60% of height?
        double qrSize = Math.min(widthPts * 0.8, heightPts * 0.6);
        Image qrImage = BarcodeGenerator.generateQRCode(product.getBarcode(), (int) qrSize, (int) qrSize);
        ImageView qrView = new ImageView(qrImage);
        qrView.setFitWidth(qrSize);
        qrView.setFitHeight(qrSize);

        // Price
        Label lblPrice = new Label(
                "R " + product.getPriceRetail().setScale(2, java.math.RoundingMode.HALF_UP).toString());
        lblPrice.setFont(Font.font("System", FontWeight.BOLD, 12));

        root.getChildren().addAll(lblDesc, qrView, lblPrice);

        // Scale down if content overflows?
        // For now, simple layout.

        return root;
    }
}
