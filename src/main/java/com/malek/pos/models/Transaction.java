package com.malek.pos.models;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class Transaction {
    private int transactionId;
    private String customTransactionId;
    private String transactionType; // 'SALE', 'QUOTE', 'REFUND'
    private LocalDateTime transactionDate;

    private int userId; // Cashier
    private Integer debtorId; // Nullable

    private BigDecimal subtotal;
    private BigDecimal taxTotal;
    private BigDecimal discountTotal;
    private BigDecimal grandTotal;

    // Payments
    private BigDecimal tenderCash = BigDecimal.ZERO;
    private BigDecimal tenderCard = BigDecimal.ZERO;
    private BigDecimal tenderAccount = BigDecimal.ZERO;
    private BigDecimal tenderBank = BigDecimal.ZERO; // New
    private BigDecimal changeDue = BigDecimal.ZERO;

    private String status;
    private int shiftId; // Link to daily shift

    // Items
    private List<TransactionItem> items;

    // Transient Flags / Extra info
    private boolean returnToStock = true; // Default to true (Standard Refund)
    private String notes; // For Reason Code
}
