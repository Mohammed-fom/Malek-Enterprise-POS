package com.malek.pos.models.reporting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ReportingDTOs {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SalesSummary {
        private BigDecimal totalSales;
        private int transactionCount;
        private BigDecimal averageBasketValue;
        private BigDecimal netProfit;
        private BigDecimal totalTaxCollected;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HourlySales {
        private int hour; // 0-23
        private BigDecimal totalSales;
        private int transactionCount;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PaymentMethodSummary {
        private String method; // CASH, CARD, ACCOUNT
        private BigDecimal totalAmount;
        private int count;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProductPerformance {
        private int productId;
        private String description;
        private BigDecimal quantitySold;
        private BigDecimal totalRevenue;
        private BigDecimal totalProfit;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InventoryWastage {
        private int productId;
        private String description;
        private BigDecimal quantityLost;
        private String reason;
        private LocalDate date;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ZReadReportDTO {
        private LocalDate reportDate;
        private String firstSaleId;
        private String lastSaleId;

        // Sales
        private BigDecimal salesCash;
        private int salesCashCount;
        private BigDecimal salesCard;
        private int salesCardCount;
        private BigDecimal salesAccount;
        private int salesAccountCount;
        private BigDecimal salesTotal;
        private int salesTotalCount;

        // Refunds (Values should be positive for display)
        private BigDecimal refundsCash;
        private int refundsCashCount;
        private BigDecimal refundsCard;
        private int refundsCardCount;
        private BigDecimal refundsAccount;
        private int refundsAccountCount;
        private BigDecimal refundsTotal;
        private int refundsTotalCount;

        private BigDecimal netSales;

        // Other / Bankable
        private BigDecimal bankableCash; // Usually Cash Sales - Cash Refunds
        private BigDecimal nonBankableCard; // Card Sales - Card Refunds
        private BigDecimal totalTenders;
        private BigDecimal cashInDrawer;

        // Tax
        private BigDecimal taxRate; // 15.00
        private BigDecimal amountIncTax;
        private BigDecimal taxAmount;

        private BigDecimal estimatedGP;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LaybyDetailedReport {
        private int activeLaybysCount;
        private BigDecimal totalValueLocked; // Total Value of Stock in Active Laybys
        private BigDecimal totalBalanceOutstanding;

        // Activity in Date Range
        private int newLaybysOpened;
        private int laybysCompleted;
        private int laybysCancelled;

        // Financials in Date Range
        private BigDecimal totalDeposits;
        private BigDecimal totalInstallments;

        // Risk Watchlist
        private java.util.List<LaybySummary> expiringSoonList;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LaybySummary {
        private String customId;
        private String customerName;
        private LocalDate startDate;
        private LocalDate expiryDate;
        private BigDecimal balance;
    }
}
