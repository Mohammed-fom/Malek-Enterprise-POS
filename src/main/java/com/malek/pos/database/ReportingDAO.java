package com.malek.pos.database;

import com.malek.pos.models.reporting.ReportingDTOs.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReportingDAO {

    // --- Sales Summary ---
    public SalesSummary getSalesSummary(LocalDate start, LocalDate end) {
        // Query: Sales + Refunds
        // Revenue = Sum(Sales Total) - Sum(Refund Total)
        String sql = """
                    SELECT
                        COUNT(*) as tx_count,
                        SUM(CASE WHEN transaction_type = 'SALE' THEN grand_total ELSE -grand_total END) as total_revenue,
                        SUM(CASE WHEN transaction_type = 'SALE' THEN tax_total ELSE -tax_total END) as total_tax
                    FROM transactions
                    WHERE status = 'COMPLETED'
                      AND transaction_type IN ('SALE', 'REFUND')
                      AND transaction_date >= ? AND transaction_date <= ?
                """;

        java.sql.Timestamp startTs = java.sql.Timestamp.valueOf(start.atStartOfDay());
        java.sql.Timestamp endTs = java.sql.Timestamp.valueOf(end.atTime(23, 59, 59));

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, startTs);
            stmt.setTimestamp(2, endTs);

            System.out.println("ReportingDAO: Querying Sales/Refunds between " + startTs + " and " + endTs);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                BigDecimal totalRev = rs.getBigDecimal("total_revenue");
                if (totalRev == null)
                    totalRev = BigDecimal.ZERO;

                BigDecimal totalTax = rs.getBigDecimal("total_tax");
                if (totalTax == null)
                    totalTax = BigDecimal.ZERO;

                int count = rs.getInt("tx_count");

                System.out.println("ReportingDAO: Found " + count + " txns (Sale+Refund), Net Revenue: " + totalRev);

                BigDecimal avgBasket = count > 0
                        ? totalRev.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                // Profit: NetSales - COGS
                // COGS should effectively be subtracted for Sales, and ADDED BACK for Refunds?
                // getCOGS queries 'SALE' only. We need to handle 'REFUND' to reduce COGS
                // (return to stock).
                // Actually, if we return to stock, we regain the asset.
                // Profit = Revenue - (COGS_Sold - COGS_Returned).
                // Let's implement refined COGS logic roughly or just Net Revenue for now?
                // User asked for "logic right".
                // Simple: Net Revenue - (COGS of Sales). Refunds usually imply item returned to
                // stock.
                // If item returned, we have the asset back. So we shouldn't count its cost as
                // "Sold".
                // So COGS should be: COGS(Sales) - COGS(Refunds).
                BigDecimal cogs = getCOGS(start, end);
                BigDecimal netProfit = totalRev.subtract(totalTax).subtract(cogs);

                return new SalesSummary(totalRev, count, avgBasket, netProfit, totalTax);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new SalesSummary(BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private BigDecimal getCOGS(LocalDate start, LocalDate end) {
        // COGS = Cost of Sales - Cost of Returns
        String sql = """
                    SELECT
                        SUM(CASE WHEN t.transaction_type = 'SALE' THEN (ti.quantity * p.cost_price)
                                 WHEN t.transaction_type = 'REFUND' THEN -(ti.quantity * p.cost_price)
                                 ELSE 0 END) as total_cost
                    FROM transaction_items ti
                    JOIN transactions t ON t.transaction_id = ti.transaction_id
                    JOIN products p ON p.product_id = ti.product_id
                    WHERE t.status = 'COMPLETED'
                      AND t.transaction_type IN ('SALE', 'REFUND')
                      AND t.transaction_date >= ? AND t.transaction_date <= ?
                """;

        java.sql.Timestamp startTs = java.sql.Timestamp.valueOf(start.atStartOfDay());
        java.sql.Timestamp endTs = java.sql.Timestamp.valueOf(end.atTime(23, 59, 59));

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, startTs);
            stmt.setTimestamp(2, endTs);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                BigDecimal cost = rs.getBigDecimal("total_cost");
                return cost != null ? cost : BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return BigDecimal.ZERO;
    }

    // --- Dead Stock ---
    public List<com.malek.pos.models.Product> getDeadStock(int months) {
        List<com.malek.pos.models.Product> list = new ArrayList<>();
        // Find products NOT sold in the last N months.
        // We look for products where product_id is NOT IN transaction_items of sales in
        // date range.
        // Start Date = NOW - months.

        String sql = """
                    SELECT * FROM products p
                    WHERE p.product_id NOT IN (
                        SELECT ti.product_id
                        FROM transaction_items ti
                        JOIN transactions t ON t.transaction_id = ti.transaction_id
                        WHERE t.transaction_type = 'SALE' AND t.status = 'COMPLETED'
                          AND t.transaction_date >= date('now', '-' || ? || ' months')
                    )
                    AND p.stock_on_hand > 0
                """;
        // Added check for stock_on_hand > 0, because if we have no stock, it's not
        // really "dead stock", it's just out of stock/inactive.
        // User requirement: "items that haven't been sold for 3 months".
        // Usually implies things sitting on shelf.

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, months);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                com.malek.pos.models.Product p = new com.malek.pos.models.Product();
                p.setProductId(rs.getInt("product_id"));
                p.setBarcode(rs.getString("barcode")); // Needed?
                p.setDescription(rs.getString("description"));
                p.setCategory(rs.getString("category"));
                p.setPriceRetail(rs.getBigDecimal("price_retail_incl"));
                p.setStockOnHand(rs.getBigDecimal("stock_on_hand"));
                list.add(p);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // --- Payment Method Breakdown ---
    public List<PaymentMethodSummary> getPaymentBreakdown(LocalDate start, LocalDate end) {
        List<PaymentMethodSummary> list = new ArrayList<>();

        java.sql.Timestamp startTs = java.sql.Timestamp.valueOf(start.atStartOfDay());
        java.sql.Timestamp endTs = java.sql.Timestamp.valueOf(end.atTime(23, 59, 59));

        // 1. Sales (Add) & Refunds (Subtract)
        // Refunds usually have Positive Tender in DB, but Type=REFUND.
        String sql = """
                    SELECT
                        SUM(CASE WHEN transaction_type = 'SALE' THEN (tender_cash - change_due) ELSE -(tender_cash) END) as net_cash,
                        SUM(CASE WHEN transaction_type = 'SALE' THEN tender_card ELSE -(tender_card) END) as net_card,
                        SUM(CASE WHEN transaction_type = 'SALE' THEN tender_account ELSE -(tender_account) END) as net_account,
                        SUM(CASE WHEN transaction_type = 'SALE' THEN tender_bank ELSE -(tender_bank) END) as net_bank
                    FROM transactions
                    WHERE status = 'COMPLETED'
                      AND transaction_type IN ('SALE', 'REFUND')
                      AND transaction_date >= ? AND transaction_date <= ?
                """;

        BigDecimal cashTotal = BigDecimal.ZERO;
        BigDecimal cardTotal = BigDecimal.ZERO;
        BigDecimal accountTotal = BigDecimal.ZERO;
        BigDecimal bankTotal = BigDecimal.ZERO;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, startTs);
            stmt.setTimestamp(2, endTs);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                if (rs.getBigDecimal("net_cash") != null)
                    cashTotal = cashTotal.add(rs.getBigDecimal("net_cash"));
                if (rs.getBigDecimal("net_card") != null)
                    cardTotal = cardTotal.add(rs.getBigDecimal("net_card"));
                if (rs.getBigDecimal("net_account") != null)
                    accountTotal = accountTotal.add(rs.getBigDecimal("net_account"));
                if (rs.getBigDecimal("net_bank") != null)
                    bankTotal = bankTotal.add(rs.getBigDecimal("net_bank"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // 2. Add Layby Payments
        com.malek.pos.database.LaybyDAO laybyDAO = new com.malek.pos.database.LaybyDAO();
        com.malek.pos.database.LaybyDAO.LaybyReportDTO laybyReport = laybyDAO.getLaybyReport(start, end);

        cashTotal = cashTotal.add(laybyReport.cashLaybyPayments());
        cardTotal = cardTotal.add(laybyReport.cardLaybyPayments());
        bankTotal = bankTotal.add(laybyReport.bankLaybyPayments());

        // 3. Build List
        list.add(new PaymentMethodSummary("CASH", cashTotal, 0));
        list.add(new PaymentMethodSummary("CARD", cardTotal, 0));
        list.add(new PaymentMethodSummary("ACCOUNT", accountTotal, 0));
        list.add(new PaymentMethodSummary("BANK", bankTotal, 0));

        return list;
    }

    public com.malek.pos.models.reporting.ReportingDTOs.ZReadReportDTO getZReadReport(LocalDate date) {
        com.malek.pos.models.reporting.ReportingDTOs.ZReadReportDTO report = new com.malek.pos.models.reporting.ReportingDTOs.ZReadReportDTO();
        report.setReportDate(date);

        java.sql.Timestamp startTs = java.sql.Timestamp.valueOf(date.atStartOfDay());
        java.sql.Timestamp endTs = java.sql.Timestamp.valueOf(date.atTime(23, 59, 59));

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            // 1. First/Last Sale ID
            // Using Custom Transaction ID for display.
            String idSql = "SELECT MIN(custom_transaction_id), MAX(custom_transaction_id) FROM transactions " +
                    "WHERE transaction_type = 'SALE' AND transaction_date BETWEEN ? AND ?";
            try (PreparedStatement stmt = conn.prepareStatement(idSql)) {
                stmt.setTimestamp(1, startTs);
                stmt.setTimestamp(2, endTs);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    report.setFirstSaleId(rs.getString(1) != null ? rs.getString(1) : "-");
                    report.setLastSaleId(rs.getString(2) != null ? rs.getString(2) : "-");
                }
            }

            // 2. Sales Breakdown (Account, Cash, Card)
            // Note: A single txn can have mix. We sum columns.
            // Counts: We count *Transactions* that have > 0 of that tender.
            // But if mixed, sum of counts > total txns. That's usually acceptable or we
            // just count dominance.
            // User image just says "Count". Let's count presence.
            String salesSql = """
                        SELECT
                            SUM(tender_cash - change_due) as cash_amt,
                            COUNT(CASE WHEN (tender_cash - change_due) > 0.01 THEN 1 END) as cash_cnt,
                            SUM(tender_card) as card_amt,
                            COUNT(CASE WHEN tender_card > 0.01 THEN 1 END) as card_cnt,
                            SUM(tender_account) as acc_amt,
                            COUNT(CASE WHEN tender_account > 0.01 THEN 1 END) as acc_cnt,
                            SUM(grand_total) as total_amt,
                            COUNT(*) as total_cnt,
                            SUM(subtotal) as sub_amt,
                            SUM(tax_total) as tax_amt
                        FROM transactions
                        WHERE transaction_type = 'SALE' AND status = 'COMPLETED'
                          AND transaction_date BETWEEN ? AND ?
                    """;

            try (PreparedStatement stmt = conn.prepareStatement(salesSql)) {
                stmt.setTimestamp(1, startTs);
                stmt.setTimestamp(2, endTs);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    report.setSalesCash(
                            rs.getBigDecimal("cash_amt") != null ? rs.getBigDecimal("cash_amt") : BigDecimal.ZERO);
                    report.setSalesCashCount(rs.getInt("cash_cnt"));

                    report.setSalesCard(
                            rs.getBigDecimal("card_amt") != null ? rs.getBigDecimal("card_amt") : BigDecimal.ZERO);
                    report.setSalesCardCount(rs.getInt("card_cnt"));

                    report.setSalesAccount(
                            rs.getBigDecimal("acc_amt") != null ? rs.getBigDecimal("acc_amt") : BigDecimal.ZERO);
                    report.setSalesAccountCount(rs.getInt("acc_cnt"));

                    report.setSalesTotal(
                            rs.getBigDecimal("total_amt") != null ? rs.getBigDecimal("total_amt") : BigDecimal.ZERO);
                    report.setSalesTotalCount(rs.getInt("total_cnt"));

                    report.setAmountIncTax(
                            rs.getBigDecimal("total_amt") != null ? rs.getBigDecimal("total_amt") : BigDecimal.ZERO);
                    report.setTaxAmount(
                            rs.getBigDecimal("tax_amt") != null ? rs.getBigDecimal("tax_amt") : BigDecimal.ZERO);
                }
            }

            // 3. Refunds Breakdown
            // Refunds are stored with Positive or Negative tenders?
            // In TransactionDAO, Refunds saved with Positive Tenders presumably
            // (representing payout).
            // But grand_total might be positive in DB?
            // "refundTxn.setGrandTotal(grandTotal);" -> Positive.
            // Type = 'REFUND'.
            String refundSql = """
                        SELECT
                            SUM(tender_cash) as cash_amt,
                            COUNT(CASE WHEN tender_cash > 0.01 THEN 1 END) as cash_cnt,
                            SUM(tender_card) as card_amt,
                            COUNT(CASE WHEN tender_card > 0.01 THEN 1 END) as card_cnt,
                            SUM(tender_account) as acc_amt,
                            COUNT(CASE WHEN tender_account > 0.01 THEN 1 END) as acc_cnt,
                            SUM(grand_total) as total_amt,
                            COUNT(*) as total_cnt
                        FROM transactions
                        WHERE transaction_type = 'REFUND' AND status = 'COMPLETED'
                          AND transaction_date BETWEEN ? AND ?
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(refundSql)) {
                stmt.setTimestamp(1, startTs);
                stmt.setTimestamp(2, endTs);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    report.setRefundsCash(
                            rs.getBigDecimal("cash_amt") != null ? rs.getBigDecimal("cash_amt") : BigDecimal.ZERO);
                    report.setRefundsCashCount(rs.getInt("cash_cnt"));

                    report.setRefundsCard(
                            rs.getBigDecimal("card_amt") != null ? rs.getBigDecimal("card_amt") : BigDecimal.ZERO);
                    report.setRefundsCardCount(rs.getInt("card_cnt"));

                    report.setRefundsAccount(
                            rs.getBigDecimal("acc_amt") != null ? rs.getBigDecimal("acc_amt") : BigDecimal.ZERO);
                    report.setRefundsAccountCount(rs.getInt("acc_cnt"));

                    report.setRefundsTotal(
                            rs.getBigDecimal("total_amt") != null ? rs.getBigDecimal("total_amt") : BigDecimal.ZERO);
                    report.setRefundsTotalCount(rs.getInt("total_cnt"));
                }
            }

            // 4. Calculations
            report.setNetSales(report.getSalesTotal().subtract(report.getRefundsTotal()));

            // Bankable (Cash Sales - Cash Refunds)
            // NOTE: Add Layby Cash Payments if they exist?
            // LaybyDAO handles payments separately in `layby_payments` table.
            LaybyDAO laybyDAO = new LaybyDAO();
            LaybyDAO.LaybyReportDTO laybyStats = laybyDAO.getLaybyReport(date, date);
            BigDecimal laybyCash = laybyStats.cashLaybyPayments();
            BigDecimal laybyCard = laybyStats.cardLaybyPayments();
            BigDecimal laybyBank = laybyStats.bankLaybyPayments(); // Direct Bank Transfer

            report.setBankableCash(report.getSalesCash().subtract(report.getRefundsCash()).add(laybyCash));

            // Non-Bankable (Card + Account? User image put CARD in Non-Bankable. Account is
            // seemingly skipped there.)
            // Logic: Bankable = Physical Cash. Non-Bankable = Card (auto-banked) + Account
            // (No money yet).
            // User image just had "CARD" under Non-Bankable.
            // We'll put Card there.
            report.setNonBankableCard(report.getSalesCard().subtract(report.getRefundsCard()).add(laybyCard));

            // Account is neither? It's "On Book".
            // Total Tenders = Bankable + Non-Bankable (Cash + Card).
            // Does it include Account? Usually "Tenders" means Payments Received. Account
            // is Credit.
            report.setTotalTenders(report.getBankableCash().add(report.getNonBankableCard()));
            report.setCashInDrawer(report.getBankableCash());

            // 5. GP
            BigDecimal cogs = getCOGS(date, date);
            report.setEstimatedGP(report.getNetSales().subtract(report.getTaxAmount()).subtract(cogs));
            // Note: NetSales includes Tax. GP = (NetSalesExclTax) - COGS.
            // NetSales in Report = SalesTotal (Inc Tax) - Refunds (Inc Tax).
            // So NetSales - TaxAmount = NetSalesExclTax.

            report.setTaxRate(new BigDecimal("15.00"));

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return report;
    }

    // --- Top Performers ---
    public List<ProductPerformance> getTopProducts(LocalDate start, LocalDate end, int limit) {
        List<ProductPerformance> list = new ArrayList<>();
        String sql = """
                    SELECT p.product_id, p.description,
                           SUM(ti.quantity) as qty_sold,
                           SUM(ti.line_total) as revenue,
                           (SUM(ti.line_total) - SUM(ti.quantity * p.cost_price)) as profit
                    FROM transaction_items ti
                    JOIN transactions t ON t.transaction_id = ti.transaction_id
                    JOIN products p ON p.product_id = ti.product_id
                    WHERE t.status = 'COMPLETED' AND t.transaction_type = 'SALE'
                      AND date(t.transaction_date) BETWEEN ? AND ?
                    GROUP BY p.product_id
                    ORDER BY qty_sold DESC
                    LIMIT ?
                """;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, start.toString());
            stmt.setString(2, end.toString());
            stmt.setInt(3, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new ProductPerformance(
                        rs.getInt("product_id"),
                        rs.getString("description"),
                        rs.getBigDecimal("qty_sold"),
                        rs.getBigDecimal("revenue"),
                        rs.getBigDecimal("profit")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}
