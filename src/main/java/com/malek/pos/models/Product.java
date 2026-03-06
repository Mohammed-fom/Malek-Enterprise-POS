package com.malek.pos.models;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class Product {
    private int productId;
    private String description;
    private String category;

    // Pricing
    private BigDecimal costPrice;
    private BigDecimal priceRetail;
    private BigDecimal priceTrade;
    private BigDecimal taxRate;

    // Stock
    private BigDecimal stockOnHand;
    private BigDecimal lowStockThreshold;
    private boolean isServiceItem;

    private int supplierId;

    // Transient field for UI handling (mapped to barcodes table)
    private String barcode;
    private String productCode; // Secondary/Supplier Code

    // Helper to get price by tier
    public BigDecimal getPrice(boolean isTrade) {
        return isTrade ? priceTrade : priceRetail;
    }

    public BigDecimal getCostPriceIncl() {
        if (costPrice == null)
            return BigDecimal.ZERO;
        BigDecimal rate = (taxRate != null) ? taxRate : new BigDecimal("15.00");
        return costPrice.multiply(BigDecimal.ONE.add(rate.divide(BigDecimal.valueOf(100)))).setScale(2,
                java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal getPriceRetailIncl() {
        if (priceRetail == null)
            return BigDecimal.ZERO;
        BigDecimal rate = (taxRate != null) ? taxRate : new BigDecimal("15.00");
        return priceRetail.multiply(BigDecimal.ONE.add(rate.divide(BigDecimal.valueOf(100)))).setScale(2,
                java.math.RoundingMode.HALF_UP);
    }

    @Override
    public String toString() {
        String display = (description != null) ? description : "";
        if (barcode != null && !barcode.isEmpty()) {
            display = "[" + barcode + "] " + display;
        }
        if (priceRetail != null) {
            // Calculate price including VAT
            BigDecimal taxRate = (this.taxRate != null) ? this.taxRate : new BigDecimal("15.00");
            BigDecimal priceInclVat = priceRetail.multiply(BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100))));
            display += " - R " + String.format("%.2f", priceInclVat);
        }
        if (stockOnHand != null) {
            display += " (Stk: " + stockOnHand + ")";
        }
        return display;
    }
}
