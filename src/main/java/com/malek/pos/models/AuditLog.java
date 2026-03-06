package com.malek.pos.models;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AuditLog {
    private int logId;
    private int userId;
    private String actionType; // DELETE_ITEM, VOID_SALE, PRICE_OVERRIDE, LOGIN, LOGOUT
    private String description;
    private LocalDateTime timestamp;
    private Integer supervisorId; // Nullable
}
