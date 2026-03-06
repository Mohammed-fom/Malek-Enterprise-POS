package com.malek.pos.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Layby {
    private int laybyId;
    private String customLaybyId;
    private String customerName;
    private String customerPhone;
    private String customerAddress;

    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private int durationMonths;

    private LocalDateTime startDate;
    private LocalDateTime expiryDate;
    private String status; // ACTIVE, COMPLETED, CANCELLED
    private int userId;

    private List<TransactionItem> items = new ArrayList<>();

    // Getters and Setters
    public int getLaybyId() {
        return laybyId;
    }

    public void setLaybyId(int laybyId) {
        this.laybyId = laybyId;
    }

    public String getCustomLaybyId() {
        return customLaybyId;
    }

    public void setCustomLaybyId(String customLaybyId) {
        this.customLaybyId = customLaybyId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public void setCustomerAddress(String customerAddress) {
        this.customerAddress = customerAddress;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }

    public int getDurationMonths() {
        return durationMonths;
    }

    public void setDurationMonths(int durationMonths) {
        this.durationMonths = durationMonths;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDateTime expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public List<TransactionItem> getItems() {
        return items;
    }

    public void setItems(List<TransactionItem> items) {
        this.items = items;
    }

    public BigDecimal getBalance() {
        if (totalAmount == null)
            return BigDecimal.ZERO;
        BigDecimal paid = amountPaid != null ? amountPaid : BigDecimal.ZERO;
        return totalAmount.subtract(paid);
    }
}
