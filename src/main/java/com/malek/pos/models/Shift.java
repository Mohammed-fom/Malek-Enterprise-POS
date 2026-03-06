package com.malek.pos.models;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Shift {
    private int shiftId;
    private int userId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private BigDecimal openingFloat;

    private BigDecimal declaredCash = BigDecimal.ZERO;
    private BigDecimal declaredCard = BigDecimal.ZERO;
    private BigDecimal systemCash = BigDecimal.ZERO;
    private BigDecimal systemCard = BigDecimal.ZERO;

    private String status; // 'OPEN', 'CLOSED'
}
