package com.malek.pos.models;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Expense {
    private int expenseId;
    private String description;
    private BigDecimal amount;
    private String category; // 'PETTY_CASH', 'UTILITIES', etc.
    private LocalDateTime expenseDate;
    private int userId;
}
