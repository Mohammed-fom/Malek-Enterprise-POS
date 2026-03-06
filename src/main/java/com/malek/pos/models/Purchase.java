package com.malek.pos.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Purchase {
    private int purchaseId;
    private int supplierId;
    private LocalDateTime purchaseDate;
    private BigDecimal totalCost;
    private String invoiceNumber;
    private String status;

    public Purchase(int supplierId, String invoiceNumber) {
        this.supplierId = supplierId;
        this.invoiceNumber = invoiceNumber;
        this.purchaseDate = LocalDateTime.now();
        this.status = "PENDING";
        this.totalCost = BigDecimal.ZERO;
    }

    // Getters Setters
    public int getPurchaseId() {
        return purchaseId;
    }

    public void setPurchaseId(int i) {
        purchaseId = i;
    }

    public int getSupplierId() {
        return supplierId;
    }

    public LocalDateTime getPurchaseDate() {
        return purchaseDate;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal b) {
        totalCost = b;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String s) {
        status = s;
    }
}
