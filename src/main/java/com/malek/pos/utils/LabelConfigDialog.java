package com.malek.pos.utils;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.Pair;

import java.util.Optional;
import com.malek.pos.database.ConfigManager;

public class LabelConfigDialog {

    public static class LabelConfig {
        public double widthMm;
        public double heightMm;
        public int quantity;

        public LabelConfig(double widthMm, double heightMm, int quantity) {
            this.widthMm = widthMm;
            this.heightMm = heightMm;
            this.quantity = quantity;
        }
    }

    public static Optional<LabelConfig> showAndWait() {
        Dialog<LabelConfig> dialog = new Dialog<>();
        dialog.setTitle("Label Settings");
        dialog.setHeaderText("Configure Label");

        ButtonType saveButtonType = new ButtonType("Print", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField width = new TextField();
        width.setPromptText("Width (mm)");
        double currentW = ConfigManager.getInstance().getDouble("label_width_mm", 50.0);
        width.setText(String.valueOf(currentW));

        TextField height = new TextField();
        height.setPromptText("Height (mm)");
        double currentH = ConfigManager.getInstance().getDouble("label_height_mm", 30.0);
        height.setText(String.valueOf(currentH));

        TextField quantity = new TextField();
        quantity.setPromptText("Qty");
        quantity.setText("1");

        grid.add(new Label("Width (mm):"), 0, 0);
        grid.add(width, 1, 0);
        grid.add(new Label("Height (mm):"), 0, 1);
        grid.add(height, 1, 1);
        grid.add(new Label("Quantity:"), 0, 2);
        grid.add(quantity, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    double w = Double.parseDouble(width.getText());
                    double h = Double.parseDouble(height.getText());
                    int q = Integer.parseInt(quantity.getText());

                    // Save to config (dimensions only)
                    ConfigManager.getInstance().updateSetting("label_width_mm", String.valueOf(w));
                    ConfigManager.getInstance().updateSetting("label_height_mm", String.valueOf(h));

                    return new LabelConfig(w, h, q);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });

        return dialog.showAndWait();
    }
}
