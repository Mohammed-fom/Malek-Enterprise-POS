package com.malek.pos.models;

import javafx.beans.property.*;
import java.math.BigDecimal;

public class TransactionItem {
    private final Product product;
    private final StringProperty code;
    private final StringProperty description;
    private final ObjectProperty<BigDecimal> unitPrice;
    private final ObjectProperty<BigDecimal> quantity;
    private final ObjectProperty<BigDecimal> total;
    private final ObjectProperty<BigDecimal> taxAmount; // For hidden calc

    public TransactionItem(Product product, BigDecimal qty, boolean isTrade) {
        this.product = product;
        this.code = new SimpleStringProperty(
                product.getBarcode() != null && !product.getBarcode().isEmpty() ? product.getBarcode()
                        : String.valueOf(product.getProductId()));
        this.description = new SimpleStringProperty(product.getDescription());

        // Calculate Inclusive Price
        BigDecimal priceExcl = product.getPrice(isTrade);
        BigDecimal taxRate = product.getTaxRate() != null ? product.getTaxRate() : BigDecimal.ZERO;
        BigDecimal multiplier = BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100)));
        BigDecimal priceIncl = priceExcl.multiply(multiplier).setScale(2, java.math.RoundingMode.HALF_UP);

        this.unitPrice = new SimpleObjectProperty<>(priceIncl);
        this.quantity = new SimpleObjectProperty<>(qty);

        this.total = new SimpleObjectProperty<>(priceIncl.multiply(qty));
        this.taxAmount = new SimpleObjectProperty<>(BigDecimal.ZERO);

        // Auto-recalculate when price or qty changes
        this.unitPrice.addListener((obs, oldVal, newVal) -> recalculate());
        this.quantity.addListener((obs, oldVal, newVal) -> recalculate());

        recalculate();
    }

    public void recalculate() {
        BigDecimal t = unitPrice.get().multiply(quantity.get());
        total.set(t);
    }

    // Getters for Properties (required for TableView)
    public StringProperty codeProperty() {
        return code;
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    public ObjectProperty<BigDecimal> unitPriceProperty() {
        return unitPrice;
    }

    public ObjectProperty<BigDecimal> quantityProperty() {
        return quantity;
    }

    public ObjectProperty<BigDecimal> totalProperty() {
        return total;
    }

    public Product getProduct() {
        return product;
    }

    // Helpers for direct access
    public String getCode() {
        return code.get();
    }

    public String getDescription() {
        return description.get();
    }

    public int getProductId() {
        return product.getProductId();
    }

    public BigDecimal getQuantity() {
        return quantity.get();
    }

    public BigDecimal getUnitPrice() {
        return unitPrice.get();
    }

    public BigDecimal getTotal() {
        return total.get();
    }
}
