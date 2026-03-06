package com.malek.pos.models;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class Debtor {
    private int debtorId;
    private String accountNo;
    private String customerName;
    private String phone;
    private String email;
    private String address;

    private BigDecimal creditLimit;
    private BigDecimal currentBalance;
    private String priceTier; // 'RETAIL' or 'TRADE'

    public boolean isTradeCustomer() {
        return "TRADE".equalsIgnoreCase(priceTier);
    }

    // Check if new sale will exceed limit
    public boolean canPurchase(BigDecimal amount) {
        if (creditLimit == null || creditLimit.compareTo(BigDecimal.ZERO) == 0)
            return true; // No limit
        return currentBalance.add(amount).compareTo(creditLimit) <= 0;
    }
}
