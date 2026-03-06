package com.malek.pos.models;

import java.math.BigDecimal;

public class PurchaseItem {
    private int productId;
    private BigDecimal quantity;
    private BigDecimal unitCost;

    public PurchaseItem(int pid, BigDecimal q, BigDecimal cost) {
        this.productId = pid;
        this.quantity = q;
        this.unitCost = cost;
    }

    public int getProductId() {
        return productId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }
}
