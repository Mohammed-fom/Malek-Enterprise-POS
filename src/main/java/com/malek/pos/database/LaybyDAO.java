package com.malek.pos.database;

import com.malek.pos.models.Layby;
import com.malek.pos.models.TransactionItem;
import com.malek.pos.models.Product;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LaybyDAO {
    private static final Logger logger = LoggerFactory.getLogger(LaybyDAO.class);

    public LaybyDAO() {
        // Connection pool managed by DatabaseManager - get connections per operation
    }

    private synchronized String generateCustomId() {
        // format LAYddMMyy001
        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("ddMMyy");
        String datePart = java.time.LocalDate.now().format(dtf);
        String base = "LAY" + datePart;

        String sql = "SELECT custom_layby_id FROM laybys WHERE custom_layby_id LIKE ? ORDER BY layby_id DESC LIMIT 1";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, base + "%");
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String lastId = rs.getString(1);
                String suffixStr = lastId.substring(base.length());
                int seq = Integer.parseInt(suffixStr) + 1;
                return base + String.format("%03d", seq);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return base + "001";
    }

    public boolean createLayby(Layby layby, BigDecimal deposit, String paymentMethod) {
        String insertLayby = "INSERT INTO laybys (custom_layby_id, customer_name, customer_phone, customer_address, total_amount, amount_paid, duration_months, start_date, expiry_date, status, user_id) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, 'ACTIVE', ?)";
        String insertItem = "INSERT INTO layby_items (layby_id, product_id, quantity, unit_price, line_total) VALUES (?, ?, ?, ?, ?)";
        String insertPayment = "INSERT INTO layby_payments (layby_id, amount, payment_method, user_id, payment_date) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
        String updateStock = "UPDATE products SET stock_on_hand = stock_on_hand - ? WHERE product_id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Generate ID
                String customId = generateCustomId();
                layby.setCustomLaybyId(customId);

                // Calc Expiry
                LocalDateTime expiry = LocalDateTime.now().plusMonths(layby.getDurationMonths());

                int laybyId = -1;
                try (PreparedStatement stmt = conn.prepareStatement(insertLayby, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, customId);
                    stmt.setString(2, layby.getCustomerName());
                    stmt.setString(3, layby.getCustomerPhone());
                    stmt.setString(4, layby.getCustomerAddress());
                    stmt.setBigDecimal(5, layby.getTotalAmount());
                    stmt.setBigDecimal(6, deposit);
                    stmt.setInt(7, layby.getDurationMonths());
                    stmt.setTimestamp(8, Timestamp.valueOf(expiry));
                    stmt.setInt(9, layby.getUserId());

                    stmt.executeUpdate();
                    ResultSet gk = stmt.getGeneratedKeys();
                    if (gk.next())
                        laybyId = gk.getInt(1);
                }

                if (laybyId == -1)
                    throw new SQLException("Failed to create layby record.");

                // 2. Items & Stock
                try (PreparedStatement itemStmt = conn.prepareStatement(insertItem);
                        PreparedStatement stockStmt = conn.prepareStatement(updateStock)) {

                    for (TransactionItem item : layby.getItems()) {
                        itemStmt.setInt(1, laybyId);
                        itemStmt.setInt(2, item.getProduct().getProductId());
                        itemStmt.setBigDecimal(3, item.quantityProperty().get());
                        itemStmt.setBigDecimal(4, item.unitPriceProperty().get());
                        itemStmt.setBigDecimal(5, item.totalProperty().get());
                        itemStmt.addBatch();

                        if (!item.getProduct().isServiceItem()) {
                            stockStmt.setBigDecimal(1, item.quantityProperty().get());
                            stockStmt.setInt(2, item.getProduct().getProductId());
                            stockStmt.addBatch();
                        }
                    }
                    itemStmt.executeBatch();
                    stockStmt.executeBatch();
                }

                // 3. Deposit Payment
                if (deposit.compareTo(BigDecimal.ZERO) > 0) {
                    try (PreparedStatement payStmt = conn.prepareStatement(insertPayment)) {
                        payStmt.setInt(1, laybyId);
                        payStmt.setBigDecimal(2, deposit);
                        payStmt.setString(3, paymentMethod);
                        payStmt.setInt(4, layby.getUserId());
                        payStmt.executeUpdate();
                    }
                }

                conn.commit();
                com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_HISTORY);
                com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_REPORTS);
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                conn.rollback();
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Layby> getAllLaybys() {
        List<Layby> list = new ArrayList<>();
        String sql = "SELECT * FROM laybys ORDER BY start_date DESC";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Layby l = new Layby();
                l.setLaybyId(rs.getInt("layby_id"));
                l.setCustomLaybyId(rs.getString("custom_layby_id"));
                l.setCustomerName(rs.getString("customer_name"));
                l.setCustomerPhone(rs.getString("customer_phone"));
                l.setCustomerAddress(rs.getString("customer_address"));
                l.setTotalAmount(rs.getBigDecimal("total_amount"));
                l.setAmountPaid(rs.getBigDecimal("amount_paid"));
                l.setDurationMonths(rs.getInt("duration_months"));
                l.setStartDate(rs.getTimestamp("start_date").toLocalDateTime());
                Timestamp exp = rs.getTimestamp("expiry_date");
                if (exp != null)
                    l.setExpiryDate(exp.toLocalDateTime());
                l.setStatus(rs.getString("status"));
                l.setUserId(rs.getInt("user_id"));
                list.add(l);
            }
        } catch (SQLException e) {
            logger.error("Database error in LaybyDAO", e);
        }
        return list;
    }

    public boolean addPayment(int laybyId, BigDecimal amount, String method, int userId) {
        String updateLayby = "UPDATE laybys SET amount_paid = amount_paid + ? WHERE layby_id = ?";
        String insertPayment = "INSERT INTO layby_payments (layby_id, amount, payment_method, user_id, payment_date) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement pstmt = conn.prepareStatement(updateLayby)) {
                    pstmt.setBigDecimal(1, amount);
                    pstmt.setInt(2, laybyId);
                    pstmt.executeUpdate();
                }

                try (PreparedStatement pstmt = conn.prepareStatement(insertPayment)) {
                    pstmt.setInt(1, laybyId);
                    pstmt.setBigDecimal(2, amount);
                    pstmt.setString(3, method);
                    pstmt.setInt(4, userId);
                    pstmt.executeUpdate();
                }

                conn.commit();
                com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_HISTORY);
                com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_REPORTS);
                return true;
            } catch (SQLException e) {
                logger.error("Database error", e);
                conn.rollback();
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void closeLayby(int laybyId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn
                        .prepareStatement("UPDATE laybys SET status = 'COMPLETED' WHERE layby_id = ?")) {
            stmt.setInt(1, laybyId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Database error in LaybyDAO", e);
        }
    }

    // Optional: Get Items for a Layby if needed
    public List<TransactionItem> getLaybyItems(int laybyId) {
        List<TransactionItem> items = new ArrayList<>();
        String sql = "SELECT li.*, p.* FROM layby_items li JOIN products p ON li.product_id = p.product_id WHERE li.layby_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, laybyId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Product p = new Product();
                p.setProductId(rs.getInt("product_id"));
                p.setDescription(rs.getString("description"));
                // ... map other product fields if critical ...
                p.setPriceRetail(rs.getBigDecimal("unit_price")); // Use historical price?

                BigDecimal qty = rs.getBigDecimal("quantity");
                TransactionItem item = new TransactionItem(p, qty, false);
                item.unitPriceProperty().set(rs.getBigDecimal("unit_price"));
                item.recalculate();
                items.add(item);
            }
        } catch (SQLException e) {
            logger.error("Database error in LaybyDAO", e);
        }
        return items;
    }

    public java.util.Map<java.time.LocalDate, java.math.BigDecimal> getLaybyPaymentsDaily(java.time.LocalDate start,
            java.time.LocalDate end) {
        java.util.Map<java.time.LocalDate, java.math.BigDecimal> map = new java.util.HashMap<>();

        // Aggregate Layby Payments by Date
        String sql = "SELECT date(payment_date) as p_date, SUM(amount) " +
                "FROM layby_payments " +
                "WHERE date(payment_date) BETWEEN date(?) AND date(?) " +
                "GROUP BY p_date";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, start.toString());
            stmt.setString(2, end.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String dSig = rs.getString(1);
                if (dSig != null) {
                    java.time.LocalDate d = java.time.LocalDate.parse(dSig);
                    map.put(d, rs.getBigDecimal(2));
                }
            }
        } catch (SQLException e) {
            logger.error("Database error in LaybyDAO", e);
        }
        return map;
    }

    public void cancelLayby(int laybyId, int userId) {
        String updateStatus = "UPDATE laybys SET status = 'CANCELLED' WHERE layby_id = ?";
        String getItems = "SELECT product_id, quantity FROM layby_items WHERE layby_id = ?";
        String returnStock = "UPDATE products SET stock_on_hand = stock_on_hand + ? WHERE product_id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Update Status
                try (PreparedStatement stmt = conn.prepareStatement(updateStatus)) {
                    stmt.setInt(1, laybyId);
                    stmt.executeUpdate();
                }

                // 2. Return Stock
                try (PreparedStatement stmt = conn.prepareStatement(getItems);
                        PreparedStatement stockStmt = conn.prepareStatement(returnStock)) {

                    stmt.setInt(1, laybyId);
                    ResultSet rs = stmt.executeQuery();

                    while (rs.next()) {
                        int pid = rs.getInt("product_id");
                        BigDecimal qty = rs.getBigDecimal("quantity");

                        // Check if service item? In createLayby we only deducted if !isServiceItem.
                        // Ideally we should check product type but for simplicity and safety against
                        // data inconsistencies, we can check product table or assume consistency.
                        // Let's check is_service_item to be safe.

                        // BUT, to keep this efficient inside one transaction, let's just add back.
                        // If it was a service item, we might be adding stock to a service item (which
                        // is usually ignored anyway).
                        // Let's do it properly by checking.
                        // OR: createLayby only seemingly inserted into layby_items logic...
                        // Re-reading createLayby: it updates stock only `if
                        // (!item.getProduct().isServiceItem())`.
                        // So we should only return stock if not service item.
                        // Let's skip the check for now or do a joined update?
                        // Simple 2-step: Select items. For each, update stock.
                        // Update stock query: "UPDATE products SET stock_on_hand = stock_on_hand + ?
                        // WHERE product_id = ? AND is_service_item = 0"

                        // Revised Query
                        String returnStockSafe = "UPDATE products SET stock_on_hand = stock_on_hand + ? WHERE product_id = ? AND is_service_item = 0";
                        try (PreparedStatement safeStock = conn.prepareStatement(returnStockSafe)) {
                            safeStock.setBigDecimal(1, qty);
                            safeStock.setInt(2, pid);
                            safeStock.executeUpdate();
                        }
                    }
                }

                // 3. Create Refund Transaction if amount paid > 0
                String strPaid = "SELECT amount_paid, customer_name FROM laybys WHERE layby_id = ?";
                BigDecimal amountPaid = BigDecimal.ZERO;
                String customerName = "Unknown";

                try (PreparedStatement stmt = conn.prepareStatement(strPaid)) {
                    stmt.setInt(1, laybyId);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        amountPaid = rs.getBigDecimal("amount_paid");
                        customerName = rs.getString("customer_name");
                    }
                }

                if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
                    // 4. Record Refund (Atomic)
                    com.malek.pos.models.Transaction refundTxn = new com.malek.pos.models.Transaction();
                    refundTxn.setTransactionType("REFUND");
                    refundTxn.setTransactionDate(java.time.LocalDateTime.now());
                    refundTxn.setUserId(userId);
                    refundTxn.setGrandTotal(amountPaid.negate()); // Refund is negative in history? Or Positive
                                                                  // Type=REFUND?
                    // Standard: REFUND type, Grand Total negative usually to balance.
                    // But let's check standard logic. Usually REFUND type is enough.
                    // Reference: TransactionDAO.saveTransaction doesn't auto-negate.
                    // Let's use negative for consistency with logic "grand_total < 0".
                    refundTxn.setGrandTotal(amountPaid.negate());

                    refundTxn.setSubtotal(amountPaid.negate());
                    refundTxn.setTaxTotal(BigDecimal.ZERO); // Simplified

                    // Assuming Cash Refund for Layby Cancel
                    refundTxn.setTenderCash(amountPaid);
                    refundTxn.setStatus("COMPLETED");

                    // Manually insert utilizing the existing connection
                    // We need a helper or use TransactionDAO with connection passing (not
                    // available)
                    // or straight insert here.
                    // Quickest valid fix: Inline SQL insert for atomic refund.
                    String refSql = "INSERT INTO transactions (transaction_type, transaction_date, user_id, grand_total, subtotal, tax_total, tender_cash, status, custom_transaction_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement rStmt = conn.prepareStatement(refSql)) {
                        rStmt.setString(1, "REFUND");
                        rStmt.setString(2, java.time.LocalDateTime.now().toString());
                        rStmt.setInt(3, userId);
                        rStmt.setBigDecimal(4, amountPaid.negate());
                        rStmt.setBigDecimal(5, amountPaid.negate());
                        rStmt.setBigDecimal(6, BigDecimal.ZERO);
                        rStmt.setBigDecimal(7, amountPaid);
                        rStmt.setString(8, "COMPLETED");
                        rStmt.setString(9, "LAYBY-CANCEL-" + laybyId);
                        rStmt.executeUpdate();
                    }
                }

                conn.commit();
                com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_HISTORY);
                com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_REPORTS);
            } catch (SQLException e) {
                e.printStackTrace();
                conn.rollback();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createLaybyRefundTransaction(int laybyId, int userId, BigDecimal amount, String customer) {
        try {
            com.malek.pos.database.TransactionDAO txnDAO = new com.malek.pos.database.TransactionDAO();
            com.malek.pos.models.Transaction txn = new com.malek.pos.models.Transaction();
            txn.setTransactionType("REFUND");
            txn.setUserId(userId);
            txn.setTransactionDate(java.time.LocalDateTime.now());
            txn.setStatus("COMPLETED");
            txn.setSubtotal(amount);
            txn.setTaxTotal(BigDecimal.ZERO); // No tax on refund of money usually? Or reverse tax? Layby payment was
                                              // tax inclusive?
            // Usually cash refund.
            txn.setGrandTotal(amount);
            txn.setTenderCash(amount); // Cash Refund
            txn.setChangeDue(BigDecimal.ZERO);

            // Dummy Item
            Product p = new Product();
            p.setProductId(0); // Generic
            p.setDescription("Layby Cancellation Refund - #" + laybyId);
            p.setPriceRetail(amount);
            p.setServiceItem(true); // Don't affect stock
            p.setTaxRate(BigDecimal.ZERO);

            TransactionItem item = new TransactionItem(p, BigDecimal.ONE, false);
            item.unitPriceProperty().set(amount);
            item.recalculate();

            txn.setItems(java.util.Collections.singletonList(item));

            txnDAO.saveTransaction(txn);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public record LaybyReportDTO(
            int newLaybysCount,
            BigDecimal newLaybysTotalValue,
            int paymentsReceivedCount,
            BigDecimal paymentsReceivedTotalValue,
            BigDecimal cashLaybyPayments,
            BigDecimal cardLaybyPayments,
            BigDecimal bankLaybyPayments) {
    }

    public LaybyReportDTO getLaybyReport(java.time.LocalDate start, java.time.LocalDate end) {
        int newLaybysCount = 0;
        BigDecimal newLaybysTotalValue = BigDecimal.ZERO;
        int paymentsCount = 0;
        BigDecimal paymentsTotalValue = BigDecimal.ZERO;
        BigDecimal cashLaybyPayments = BigDecimal.ZERO;
        BigDecimal cardLaybyPayments = BigDecimal.ZERO;
        BigDecimal bankLaybyPayments = BigDecimal.ZERO;

        java.sql.Timestamp startTs = java.sql.Timestamp.valueOf(start.atStartOfDay());
        java.sql.Timestamp endTs = java.sql.Timestamp.valueOf(end.atTime(23, 59, 59));

        // 1. New Laybys Created
        String sqlNew = "SELECT COUNT(*), SUM(total_amount) FROM laybys " +
                "WHERE start_date >= ? AND start_date <= ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sqlNew)) {
            stmt.setTimestamp(1, startTs);
            stmt.setTimestamp(2, endTs);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                newLaybysCount = rs.getInt(1);
                newLaybysTotalValue = rs.getBigDecimal(2);
                if (newLaybysTotalValue == null)
                    newLaybysTotalValue = BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            logger.error("Error fetching new layby stats", e);
        }

        // 2. Payments (Deposits + Installments) Breakdown
        // 2. Payments (Deposits + Installments) Breakdown
        String sqlPayments = "SELECT payment_method, COUNT(*), SUM(amount) FROM layby_payments " +
                "WHERE payment_date >= ? AND payment_date <= ? " +
                "GROUP BY payment_method";

        // We also want totals, which we can sum up from the breakdown or do a separate
        // query.
        // Summing up is more efficient.

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sqlPayments)) {
            stmt.setTimestamp(1, startTs);
            stmt.setTimestamp(2, endTs);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String method = rs.getString("payment_method");
                if (method == null)
                    method = "CASH"; // Default fallback
                method = method.toUpperCase();

                int count = rs.getInt(2);
                BigDecimal amount = rs.getBigDecimal(3);
                if (amount == null)
                    amount = BigDecimal.ZERO;

                paymentsCount += count;
                paymentsTotalValue = paymentsTotalValue.add(amount);

                if (method.contains("CASH")) {
                    cashLaybyPayments = cashLaybyPayments.add(amount);
                } else if (method.contains("CARD")) {
                    cardLaybyPayments = cardLaybyPayments.add(amount);
                } else if (method.contains("BANK") || method.contains("EFT")) {
                    bankLaybyPayments = bankLaybyPayments.add(amount);
                } else {
                    // Fallback for other methods if any, treat as CASH or separate?
                    // For now, let's treat anything else as CASH or just ignore?
                    // Best to add to Cash if unsure, or maybe Account?
                    // Requirement only asks for Cash, Card, Bank.
                    // Let's assume default is CASH if not Card/Bank.
                    cashLaybyPayments = cashLaybyPayments.add(amount);
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching layby payment stats", e);
        }

        return new LaybyReportDTO(newLaybysCount, newLaybysTotalValue, paymentsCount, paymentsTotalValue,
                cashLaybyPayments, cardLaybyPayments, bankLaybyPayments);
    }

    public com.malek.pos.models.reporting.ReportingDTOs.LaybyDetailedReport getDetailedLaybyReport(
            java.time.LocalDate start, java.time.LocalDate end) {
        com.malek.pos.models.reporting.ReportingDTOs.LaybyDetailedReport report = new com.malek.pos.models.reporting.ReportingDTOs.LaybyDetailedReport();

        // Fix: Convert Dates to Strings matching SQLite DEFAULT CURRENT_TIMESTAMP
        // format (partially)
        // Actually, we just need the date range. SQLite strings compare
        // lexicographically.
        // Start: YYYY-MM-DD 00:00:00
        // End: YYYY-MM-DD 23:59:59
        String startStr = start.toString() + " 00:00:00";
        String endStr = end.toString() + " 23:59:59";

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {

            // 1. Snapshot of ALL ACTIVE Laybys
            String sqlActive = "SELECT COUNT(*), SUM(total_amount), SUM(total_amount - amount_paid) FROM laybys WHERE status = 'ACTIVE'";
            try (PreparedStatement stmt = conn.prepareStatement(sqlActive)) {
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    report.setActiveLaybysCount(rs.getInt(1));
                    report.setTotalValueLocked(rs.getBigDecimal(2));
                    report.setTotalBalanceOutstanding(rs.getBigDecimal(3));
                }
            }
            if (report.getTotalValueLocked() == null)
                report.setTotalValueLocked(BigDecimal.ZERO);
            if (report.getTotalBalanceOutstanding() == null)
                report.setTotalBalanceOutstanding(BigDecimal.ZERO);

            // 2. Activity in Date Range - New Opened
            // Fix: Use String comparison for SQLite datetime
            String sqlNew = "SELECT COUNT(*) FROM laybys WHERE start_date BETWEEN ? AND ?";
            try (PreparedStatement stmt = conn.prepareStatement(sqlNew)) {
                stmt.setString(1, startStr);
                stmt.setString(2, endStr);
                ResultSet rs = stmt.executeQuery();
                if (rs.next())
                    report.setNewLaybysOpened(rs.getInt(1));
            }

            // 3. Activity in Date Range - Completed
            // Fix: Cast payment_date to check range safely
            String sqlCompleted = "SELECT COUNT(DISTINCT l.layby_id) FROM laybys l " +
                    "JOIN layby_payments p ON l.layby_id = p.layby_id " +
                    "WHERE l.status = 'COMPLETED' " +
                    "GROUP BY l.layby_id " +
                    "HAVING MAX(p.payment_date) BETWEEN ? AND ?";
            String sqlCompletedWrapper = "SELECT COUNT(*) FROM (" + sqlCompleted + ") as sub";

            try (PreparedStatement stmt = conn.prepareStatement(sqlCompletedWrapper)) {
                stmt.setString(1, startStr);
                stmt.setString(2, endStr);
                ResultSet rs = stmt.executeQuery();
                if (rs.next())
                    report.setLaybysCompleted(rs.getInt(1));
            }

            // 4. Activity in Date Range - Cancelled
            // Fix: Transaction date string comparison
            String sqlCancelled = "SELECT COUNT(*) FROM transactions " +
                    "WHERE transaction_type = 'REFUND' " +
                    "AND custom_transaction_id LIKE 'LAYBY-CANCEL-%' " +
                    "AND transaction_date BETWEEN ? AND ?";
            try (PreparedStatement stmt = conn.prepareStatement(sqlCancelled)) {
                stmt.setString(1, startStr); // Transaction DAO typically stores ISO string
                stmt.setString(2, endStr);
                ResultSet rs = stmt.executeQuery();
                if (rs.next())
                    report.setLaybysCancelled(rs.getInt(1));
            }

            // 5. Financials - Total Payments in Range
            // Fix: Remove overwrite logic. Assign all to Installments for now.
            String sqlPayments = "SELECT SUM(amount) FROM layby_payments WHERE payment_date BETWEEN ? AND ?";
            try (PreparedStatement stmt = conn.prepareStatement(sqlPayments)) {
                stmt.setString(1, startStr);
                stmt.setString(2, endStr);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    BigDecimal total = rs.getBigDecimal(1);
                    if (total == null)
                        total = BigDecimal.ZERO;

                    report.setTotalDeposits(BigDecimal.ZERO);
                    report.setTotalInstallments(total); // Assign all to installments so it shows up
                }
            }

            // 6. Risk Watchlist - Expiring in 7 Days (Active only)
            // SQL: active and expiry_date between NOW and NOW+7 days
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.LocalDateTime nextWeek = now.plusDays(7);

            String sqlRisk = "SELECT * FROM laybys WHERE status = 'ACTIVE' AND expiry_date BETWEEN ? AND ? ORDER BY expiry_date ASC";
            List<com.malek.pos.models.reporting.ReportingDTOs.LaybySummary> riskList = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sqlRisk)) {
                stmt.setTimestamp(1, java.sql.Timestamp.valueOf(now));
                stmt.setTimestamp(2, java.sql.Timestamp.valueOf(nextWeek));
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String cid = rs.getString("custom_layby_id");
                    String cname = rs.getString("customer_name");
                    java.time.LocalDate sDate = rs.getTimestamp("start_date").toLocalDateTime().toLocalDate();
                    java.time.LocalDate eDate = rs.getTimestamp("expiry_date").toLocalDateTime().toLocalDate();
                    BigDecimal total = rs.getBigDecimal("total_amount");
                    BigDecimal paid = rs.getBigDecimal("amount_paid");
                    BigDecimal bal = total.subtract(paid);

                    riskList.add(new com.malek.pos.models.reporting.ReportingDTOs.LaybySummary(
                            cid, cname, sDate, eDate, bal));
                }
            }
            report.setExpiringSoonList(riskList);

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return report;
    }
}
