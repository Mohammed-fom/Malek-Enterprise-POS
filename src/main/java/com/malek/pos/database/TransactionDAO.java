package com.malek.pos.database;

import com.malek.pos.models.Transaction;
import com.malek.pos.models.TransactionItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;

public class TransactionDAO {

    private static final Logger logger = LoggerFactory.getLogger(TransactionDAO.class);

    public TransactionDAO() {
        // Connection pool managed by DatabaseManager - get connections per operation
    }

    private synchronized String generateCustomId(String type) {
        String prefix = type.equalsIgnoreCase("REFUND") ? "RFD" : "SALE";
        int padding = type.equalsIgnoreCase("REFUND") ? 3 : 2;

        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("ddMMyy");
        String datePart = java.time.LocalDate.now().format(dtf);

        String base = prefix + datePart; // e.g., SALE120126

        String sql = "SELECT custom_transaction_id FROM transactions WHERE custom_transaction_id LIKE ? ORDER BY transaction_id DESC LIMIT 1";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, base + "%");
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String lastId = rs.getString(1);
                // Extract suffix
                String suffixStr = lastId.substring(base.length());
                int seq = Integer.parseInt(suffixStr) + 1;
                return base + String.format("%0" + padding + "d", seq);
            }
        } catch (SQLException | NumberFormatException e) {
            logger.error("Error generating custom transaction ID: {}", e.getMessage(), e);
        }

        return base + String.format("%0" + padding + "d", 1);
    }

    public boolean saveTransaction(Transaction txn) {
        String insertTxn = "INSERT INTO transactions (transaction_type, user_id, debtor_id, subtotal, tax_total, discount_total, grand_total, tender_cash, tender_card, tender_account, tender_bank, change_due, status, shift_id, custom_transaction_id, transaction_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String insertItem = "INSERT INTO transaction_items (transaction_id, product_id, quantity, unit_price, line_total, tax_percent, custom_description, cost_price_at_sale) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String updateStock = "UPDATE products SET stock_on_hand = stock_on_hand - ? WHERE product_id = ?";

        // Reverse stock logic for refunds?
        // If Refund, we ADD stock back.
        // Logic below: "stock_on_hand - ?"
        // If refund, quantity should be passed as Negative? Or logic flipped?
        // Usually SaveTransaction receives Items with Positive Quantity.
        // If Type is REFUND, we should FLIP the sign for stock update?
        // Let's assume Refund items come in with Positive Quantity, so we flip update
        // sign.
        boolean isRefund = "REFUND".equalsIgnoreCase(txn.getTransactionType());
        if (isRefund) {
            updateStock = "UPDATE products SET stock_on_hand = stock_on_hand + ? WHERE product_id = ?";
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Generate ID
                String customId = generateCustomId(txn.getTransactionType());
                txn.setCustomTransactionId(customId);

                int txnId = -1;
                try (PreparedStatement stmt = conn.prepareStatement(insertTxn, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, txn.getTransactionType());
                    stmt.setInt(2, txn.getUserId());
                    if (txn.getDebtorId() != null)
                        stmt.setInt(3, txn.getDebtorId());
                    else
                        stmt.setNull(3, Types.INTEGER);
                    stmt.setBigDecimal(4, txn.getSubtotal());
                    stmt.setBigDecimal(5, txn.getTaxTotal());
                    stmt.setBigDecimal(6, txn.getDiscountTotal());
                    stmt.setBigDecimal(7, txn.getGrandTotal());
                    stmt.setBigDecimal(8, txn.getTenderCash());
                    stmt.setBigDecimal(9, txn.getTenderCard());
                    stmt.setBigDecimal(10, txn.getTenderAccount());
                    stmt.setBigDecimal(11, txn.getTenderBank()); // New
                    stmt.setBigDecimal(12, txn.getChangeDue());
                    stmt.setString(13, txn.getStatus());
                    stmt.setInt(14, txn.getShiftId());
                    stmt.setString(15, txn.getCustomTransactionId());
                    stmt.setTimestamp(16, java.sql.Timestamp.valueOf(txn.getTransactionDate()));

                    int affected = stmt.executeUpdate();
                    if (affected == 0)
                        throw new SQLException("Creating transaction failed, no rows affected.");

                    try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            txnId = generatedKeys.getInt(1);
                            txn.setTransactionId(txnId); // Set back to object
                        } else {
                            throw new SQLException("Creating transaction failed, no ID obtained.");
                        }
                    }
                }

                try (PreparedStatement itemStmt = conn.prepareStatement(insertItem);
                        PreparedStatement stockStmt = conn.prepareStatement(updateStock)) {

                    for (TransactionItem item : txn.getItems()) {
                        itemStmt.setInt(1, txnId);
                        itemStmt.setInt(2, item.getProduct().getProductId());
                        itemStmt.setBigDecimal(3, item.quantityProperty().get());
                        itemStmt.setBigDecimal(4, item.unitPriceProperty().get());
                        itemStmt.setBigDecimal(5, item.totalProperty().get());
                        itemStmt.setBigDecimal(6, item.getProduct().getTaxRate());

                        // Check if description differs from product description -> save as custom
                        String customDesc = item.getDescription();
                        if (customDesc != null && !customDesc.equals(item.getProduct().getDescription())) {
                            itemStmt.setString(7, customDesc);
                        } else {
                            itemStmt.setNull(7, Types.VARCHAR);
                        }

                        // Task 1: Data Integrity - Save Cost Price
                        itemStmt.setBigDecimal(8,
                                item.getProduct().getCostPrice() != null ? item.getProduct().getCostPrice()
                                        : BigDecimal.ZERO);

                        itemStmt.addBatch();

                        if (!item.getProduct().isServiceItem() && !"QUOTE".equals(txn.getTransactionType())) {
                            boolean skipStock = isRefund && !txn.isReturnToStock();
                            if (!skipStock) {
                                stockStmt.setBigDecimal(1, item.quantityProperty().get());
                                stockStmt.setInt(2, item.getProduct().getProductId());
                                stockStmt.addBatch();
                            }
                        }
                    }
                    itemStmt.executeBatch();
                    if (!"QUOTE".equals(txn.getTransactionType())) {
                        stockStmt.executeBatch();
                    }
                }

                // Update Debtor Balance (Account Tender)
                if (txn.getDebtorId() != null && txn.getTenderAccount().compareTo(BigDecimal.ZERO) != 0) {
                    String updateBalance = "UPDATE debtors SET current_balance = current_balance + ? WHERE debtor_id = ?";
                    // If Sale (Positive Total), Account Tender Increases Debt (+).
                    // If Refund (Negative Total), Account Tender (passed as positive usually in
                    // TenderAccount field?)
                    // Wait. SalesController line 840 sets TenderAccount from PaymentController.
                    // In PaymentController for Refund, "Cash" usually means Payout.
                    // If "Account" is used for Refund, it means "Credit Account" (Refund to
                    // Account).
                    // Does Refund mean DECREASE Debt?
                    // If I owe $100, and I return item for $10 -> Debt becomes $90.
                    // So update should include Sign based on transaction type?
                    // Current logic: updateBalance + ?.
                    // If Sale: tenders are positive. +$50 -> Debt $50. Correct.
                    // If Refund: PaymentController returns positive amounts usually?
                    // SalesController line 838: txn.setTenderCash(payment.getPaidCash());
                    // If Refund, PaymentController logic: "Refund Due: -50.00".
                    // Tenders are usually input as positive actions.
                    // WE NEED TO CHECK TRANSACTION TYPE.
                    // If REFUND, we should SUBTRACT.
                    // OR ensure tender amount is Signed correctly?
                    // Let's assume standard logic:
                    // SALE: Debt Up (+).
                    // REFUND: Debt Down (-).
                    BigDecimal amount = txn.getTenderAccount();
                    if ("REFUND".equalsIgnoreCase(txn.getTransactionType())) {
                        amount = amount.negate();
                    }

                    try (PreparedStatement balStmt = conn.prepareStatement(updateBalance)) {
                        balStmt.setBigDecimal(1, amount);
                        balStmt.setInt(2, txn.getDebtorId());
                        balStmt.executeUpdate();
                    }
                }

                conn.commit();
                com.malek.pos.utils.EventBus.publish(com.malek.pos.utils.EventBus.REFRESH_HISTORY);
                return true;
            } catch (SQLException e) {
                logger.error("Error saving transaction: {}", e.getMessage(), e);
                conn.rollback();
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Error saving transaction: {}", e.getMessage(), e);
            return false;
        }
    }

    public void updateTransactionStatus(int transactionId, String newStatus) {
        String sql = "UPDATE transactions SET status = ? WHERE transaction_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newStatus);
            stmt.setInt(2, transactionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating transaction status for ID {}: {}", transactionId, e.getMessage(), e);
        }
    }

    public java.util.List<Transaction> getOpenQuotes() {
        java.util.List<Transaction> list = new java.util.ArrayList<>();
        String sql = "SELECT * FROM transactions WHERE transaction_type = 'QUOTE' AND status = 'SAVED'";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToTransaction(rs));
            }
        } catch (SQLException e) {
            logger.error("Error retrieving open quotes: {}", e.getMessage(), e);
        }
        return list;
    }

    public java.util.List<TransactionItem> getTransactionItems(int transactionId) {
        java.util.List<TransactionItem> items = new java.util.ArrayList<>();
        String sql = "SELECT ti.*, p.* FROM transaction_items ti JOIN products p ON ti.product_id = p.product_id WHERE ti.transaction_id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, transactionId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                com.malek.pos.models.Product p = new com.malek.pos.models.Product();
                p.setProductId(rs.getInt("product_id"));
                p.setDescription(rs.getString("description"));
                p.setCategory(rs.getString("category"));
                p.setCostPrice(rs.getBigDecimal("cost_price"));
                p.setPriceRetail(rs.getBigDecimal("price_retail"));
                p.setPriceTrade(rs.getBigDecimal("price_trade"));
                p.setTaxRate(rs.getBigDecimal("tax_rate"));
                p.setServiceItem(rs.getBoolean("is_service_item"));

                BigDecimal qty = rs.getBigDecimal("quantity");
                TransactionItem item = new TransactionItem(p, qty, false);
                // Note: Price in item will be re-calculated from Product defaults.
                // If we want exact price from quote, we should set it explicitly:
                item.unitPriceProperty().set(rs.getBigDecimal("unit_price"));
                item.recalculate();

                String customDesc = rs.getString("custom_description");
                if (customDesc != null && !customDesc.isEmpty()) {
                    item.descriptionProperty().set(customDesc);
                }

                items.add(item);
            }
        } catch (SQLException e) {
            logger.error("Error retrieving transaction items for transaction ID {}: {}", transactionId, e.getMessage(),
                    e);
        }
        return items;
    }

    private Transaction mapResultSetToTransaction(ResultSet rs) throws SQLException {
        Transaction t = new Transaction();
        t.setTransactionId(rs.getInt("transaction_id"));
        try {
            t.setCustomTransactionId(rs.getString("custom_transaction_id"));
        } catch (Exception e) {
            // Ignore if column missing in partial queries (though usually present)
        }
        t.setTransactionDate(rs.getTimestamp("transaction_date").toLocalDateTime());
        t.setTransactionType(rs.getString("transaction_type"));

        // Map Status
        t.setStatus(rs.getString("status"));

        // Map Financials
        t.setSubtotal(rs.getBigDecimal("subtotal"));
        t.setTaxTotal(rs.getBigDecimal("tax_total"));
        t.setDiscountTotal(rs.getBigDecimal("discount_total"));
        t.setGrandTotal(rs.getBigDecimal("grand_total"));

        // Map Payment/Tender
        t.setTenderCash(rs.getBigDecimal("tender_cash"));
        if (rs.wasNull())
            t.setTenderCash(BigDecimal.ZERO);

        t.setTenderCard(rs.getBigDecimal("tender_card"));
        if (rs.wasNull())
            t.setTenderCard(BigDecimal.ZERO);

        t.setTenderAccount(rs.getBigDecimal("tender_account"));
        if (rs.wasNull())
            t.setTenderAccount(BigDecimal.ZERO);

        t.setChangeDue(rs.getBigDecimal("change_due"));
        if (rs.wasNull())
            t.setChangeDue(BigDecimal.ZERO);

        // Map Debtor
        t.setDebtorId(rs.getInt("debtor_id"));
        if (rs.wasNull())
            t.setDebtorId(null);

        t.setUserId(rs.getInt("user_id"));
        t.setShiftId(rs.getInt("shift_id"));

        return t;
    }

    public java.util.List<Transaction> getTransactionsByDebtor(int debtorId) {
        java.util.List<Transaction> list = new java.util.ArrayList<>();
        String sql = "SELECT * FROM transactions WHERE debtor_id = ? ORDER BY transaction_date DESC";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, debtorId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(mapResultSetToTransaction(rs));
            }
        } catch (SQLException e) {
            logger.error("Error retrieving transactions for debtor ID {}: {}", debtorId, e.getMessage(), e);
        }
        return list;
    }

    public java.util.List<Transaction> getAllTransactions() {
        java.util.List<Transaction> list = new java.util.ArrayList<>();
        String sql = "SELECT * FROM transactions ORDER BY transaction_id DESC";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToTransaction(rs));
            }
        } catch (SQLException e) {
            logger.error("Error retrieving all transactions: {}", e.getMessage(), e);
        }
        return list;
    }

    public Transaction findByCustomTransactionId(String customId) {
        String sql = "SELECT * FROM transactions WHERE custom_transaction_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, customId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToTransaction(rs);
            }
        } catch (SQLException e) {
            logger.error("Error finding transaction by custom ID {}: {}", customId, e.getMessage(), e);
        }
        return null;
    }

    public boolean persistParkedTransaction(int userId, Integer debtorId, String itemsJson, String referenceNote) {
        String sql = "INSERT INTO parked_transactions (user_id, debtor_id, items_json, reference_note) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            if (debtorId != null)
                stmt.setInt(2, debtorId);
            else
                stmt.setNull(2, Types.INTEGER);
            stmt.setString(3, itemsJson);
            stmt.setString(4, referenceNote);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("Error persisting parked transaction: {}", e.getMessage(), e);
            return false;
        }
    }

    public java.util.List<ParkedTransaction> getParkedTransactions() {
        java.util.List<ParkedTransaction> list = new java.util.ArrayList<>();
        String sql = "SELECT * FROM parked_transactions ORDER BY parked_date DESC";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new ParkedTransaction(
                        rs.getInt("parked_id"),
                        rs.getTimestamp("parked_date").toLocalDateTime(),
                        rs.getInt("user_id"),
                        (Integer) rs.getObject("debtor_id"),
                        rs.getString("items_json"),
                        rs.getString("reference_note") // Add this
                ));
            }
        } catch (SQLException e) {
            logger.error("Error retrieving parked transactions: {}", e.getMessage(), e);
        }
        return list;
    }

    public void deleteParkedTransaction(int parkedId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement("DELETE FROM parked_transactions WHERE parked_id = ?")) {
            stmt.setInt(1, parkedId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error deleting parked transaction with ID {}: {}", parkedId, e.getMessage(), e);
        }
    }

    public java.util.List<Transaction> findTransactionsByProduct(String query) {
        java.util.List<Transaction> list = new java.util.ArrayList<>();
        // Search by Product Description or Barcode
        // We join transactions with items, products and barcodes
        String sql = """
                    SELECT DISTINCT t.*
                    FROM transactions t
                    JOIN transaction_items ti ON t.transaction_id = ti.transaction_id
                    JOIN products p ON ti.product_id = p.product_id
                    LEFT JOIN barcodes b ON p.product_id = b.product_id
                    WHERE (p.description LIKE ? OR b.barcode = ?)
                    ORDER BY t.transaction_date DESC
                    LIMIT 50
                """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + query + "%");
            stmt.setString(2, query); // Exact match for barcode usually

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(mapResultSetToTransaction(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // --- Analytics ---

    public java.math.BigDecimal getDailySalesTotal(java.time.LocalDate date) {
        return getSalesTotalBetween(date.atStartOfDay(), date.atTime(23, 59, 59));
    }

    public java.math.BigDecimal getSalesTotalBetween(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        String sql = "SELECT SUM(grand_total) FROM transactions WHERE transaction_type = 'SALE' AND transaction_date BETWEEN ? AND ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Ensure Timestamp format matches DB
            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(end));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                java.math.BigDecimal val = rs.getBigDecimal(1);
                return val != null ? val : java.math.BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return java.math.BigDecimal.ZERO;
    }

    public java.util.Map<String, Integer> getTopSellingProducts(int limit) {
        // Default to All Time for backward compatibility if needed, or maybe This
        // Month?
        // Let's keep it "All Time" as per original implementation logic (no date
        // filter).
        java.util.Map<String, Integer> map = new java.util.LinkedHashMap<>();
        String sql = "SELECT p.description, SUM(ti.quantity) as total_qty " +
                "FROM transaction_items ti " +
                "JOIN transactions t ON ti.transaction_id = t.transaction_id " +
                "JOIN products p ON ti.product_id = p.product_id " +
                "WHERE t.transaction_type = 'SALE' " +
                "GROUP BY p.product_id " +
                "ORDER BY total_qty DESC LIMIT ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                map.put(rs.getString("description"), rs.getInt("total_qty"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    public java.util.Map<String, Integer> getTopSellingProducts(java.time.LocalDateTime start,
            java.time.LocalDateTime end, int limit) {
        java.util.Map<String, Integer> map = new java.util.LinkedHashMap<>();
        String sql = "SELECT p.description, SUM(ti.quantity) as total_qty " +
                "FROM transaction_items ti " +
                "JOIN transactions t ON ti.transaction_id = t.transaction_id " +
                "JOIN products p ON ti.product_id = p.product_id " +
                "WHERE t.transaction_type = 'SALE' " +
                "AND t.transaction_date BETWEEN ? AND ? " +
                "GROUP BY p.product_id " +
                "ORDER BY total_qty DESC LIMIT ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(end));
            stmt.setInt(3, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                map.put(rs.getString("description"), rs.getInt("total_qty"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    // --- Reporting ---

    public FinancialReportDTO getReportFinancials(java.time.LocalDate date) {
        // Initialize with zeros
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalRefunds = BigDecimal.ZERO;
        int transactionCount = 0;

        String sql = "SELECT transaction_type, SUM(grand_total), SUM(tax_total), COUNT(*) FROM transactions WHERE date(transaction_date) = date(?) GROUP BY transaction_type";

        try (java.sql.Connection conn = DatabaseManager.getInstance().getConnection()) {
            // 1. Transactions (Sales/Refunds)
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, date.toString());
                java.sql.ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String type = rs.getString(1);
                    BigDecimal total = rs.getBigDecimal(2);
                    BigDecimal tax = rs.getBigDecimal(3);
                    int count = rs.getInt(4);

                    if ("SALE".equals(type)) {
                        if (total.compareTo(BigDecimal.ZERO) >= 0) {
                            totalSales = totalSales.add(total);
                            totalTax = totalTax.add(tax);
                            transactionCount += count;
                        } else {
                            // Negative Sale -> Refund
                            totalRefunds = totalRefunds.add(total.abs());
                        }
                    } else if ("REFUND".equals(type)) {
                        totalRefunds = totalRefunds.add(total.abs()); // Ensure positive
                    }
                }
            }

            // 2. Layby Payments (Considered generally as Sales/Cash In when paid)
            // We need to count these towards Total Sales for the Dashboard to reflect
            // "Money In"
            String laybySql = "SELECT SUM(amount) FROM layby_payments WHERE date(payment_date) = date(?)";
            try (java.sql.PreparedStatement stmt2 = conn.prepareStatement(laybySql)) {
                stmt2.setString(1, date.toString());
                java.sql.ResultSet rs2 = stmt2.executeQuery();
                if (rs2.next()) {
                    BigDecimal laybyTotal = rs2.getBigDecimal(1);
                    if (laybyTotal != null) {
                        totalSales = totalSales.add(laybyTotal);
                        // Tax logic for layby payments is tricky as it's partial.
                        // For simplicity in this Dashboard View, we assume the tax is part of the sales
                        // total
                        // but maybe don't calculate tax explicitly or approximate it.
                        // Or we just add to Gross Sales.
                        // Let's add to Sales.
                    }
                }
            }

            // 2b. Layby Initial Deposits (Captured in layby_payments? Check LaybyDAO)
            // If LaybyDAO.createLayby adds a payment to layby_payments, then we are good.
            // If it handles deposit separately, we might miss it.
            // Checking LaybyDAO via code view would be safer but typically payments are
            // unified.
            // Assuming LaybyDAO adds deposit to layby_payments table.

        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }

        return new FinancialReportDTO(totalSales, totalTax, totalRefunds, transactionCount);
    }

    public TenderBreakdownDTO getTenderBreakdown(java.time.LocalDate date) {
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal card = BigDecimal.ZERO;
        BigDecimal account = BigDecimal.ZERO;

        String sql = "SELECT SUM(tender_cash), SUM(tender_card), SUM(tender_account) FROM transactions WHERE transaction_type = 'SALE' AND date(transaction_date) = date(?)";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, date.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                cash = rs.getBigDecimal(1) != null ? rs.getBigDecimal(1) : BigDecimal.ZERO;
                card = rs.getBigDecimal(2) != null ? rs.getBigDecimal(2) : BigDecimal.ZERO;
                account = rs.getBigDecimal(3) != null ? rs.getBigDecimal(3) : BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return new TenderBreakdownDTO(cash, card, account);
    }

    public java.util.List<ProductSalesDTO> getProductSalesStats(java.time.LocalDate startDate,
            java.time.LocalDate endDate) {
        java.util.List<ProductSalesDTO> list = new java.util.ArrayList<>();
        // Use COALESCE to group by custom description if available, else product
        // description
        String sql = "SELECT COALESCE(ti.custom_description, p.description) as effective_desc, SUM(ti.quantity) as qty, SUM(ti.line_total) as revenue "
                +
                "FROM transaction_items ti " +
                "JOIN transactions t ON ti.transaction_id = t.transaction_id " +
                "JOIN products p ON ti.product_id = p.product_id " +
                "WHERE t.transaction_date BETWEEN ? AND ? " +
                "AND t.status = 'COMPLETED' " +
                "GROUP BY effective_desc " +
                "ORDER BY revenue DESC";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(startDate.atStartOfDay()));
            stmt.setTimestamp(2, Timestamp.valueOf(endDate.atTime(23, 59, 59)));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new ProductSalesDTO(
                        rs.getString("effective_desc"),
                        rs.getBigDecimal("qty"),
                        rs.getBigDecimal("revenue")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public record ProductSalesDTO(String description, BigDecimal quantity, BigDecimal revenue) {
    }

    public record FinancialReportDTO(BigDecimal totalSales, BigDecimal totalTax, BigDecimal totalRefunds,
            int transactionCount) {
    }

    public record TenderBreakdownDTO(BigDecimal cash, BigDecimal card, BigDecimal account) {
    }

    public record ParkedTransaction(int parkedId, java.time.LocalDateTime date, int userId, Integer debtorId,
            String itemsJson, String referenceNote) {

        @Override
        public String toString() {
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm");
            String s = date.format(dtf);
            if (referenceNote != null && !referenceNote.isEmpty())
                s += " - " + referenceNote;
            else if (debtorId != null && debtorId != 0)
                s += " (Debtor #" + debtorId + ")";
            else
                s += " (No Ref)";
            return s;
        }
    }

    public java.util.Map<String, Object> getSingleProductSalesStats(int productId, java.time.LocalDate startDate,
            java.time.LocalDate endDate) {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;

        // Use date() for robust day-level comparison regardless of time/timezone
        // Include SALE and REFUND to capture net performance
        String sql = "SELECT SUM(ti.quantity), SUM(ti.line_total) " +
                "FROM transaction_items ti " +
                "JOIN transactions t ON ti.transaction_id = t.transaction_id " +
                "WHERE ti.product_id = ? " +
                "AND date(t.transaction_date) BETWEEN date(?) AND date(?) " +
                "AND t.transaction_type IN ('SALE', 'REFUND')";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            stmt.setString(2, startDate.toString()); // Pass YYYY-MM-DD string
            stmt.setString(3, endDate.toString()); // Pass YYYY-MM-DD string
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                if (rs.getBigDecimal(1) != null)
                    qty = rs.getBigDecimal(1);
                if (rs.getBigDecimal(2) != null)
                    revenue = rs.getBigDecimal(2);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        stats.put("quantity", qty);
        stats.put("revenue", revenue);
        return stats;
    }

    public java.util.Map<java.time.LocalDate, DailyFinancialDTO> getDailyFinancialStats(java.time.LocalDate startDate,
            java.time.LocalDate endDate) {
        java.util.Map<java.time.LocalDate, DailyFinancialDTO> stats = new java.util.LinkedHashMap<>(); // LinkedHashMap
                                                                                                       // preserves date
                                                                                                       // order if
                                                                                                       // inserted
                                                                                                       // sequentially?
                                                                                                       // No, Map
                                                                                                       // doesn't
                                                                                                       // guarantee.
                                                                                                       // TreeMap?
        // Let's use TreeMap to be safe for date ordering
        stats = new java.util.TreeMap<>();

        // Check for SQLite vs others for date function
        // SQLite: date(transaction_date) works.
        // Added tender_bank (index 7 if inserted before discount)
        String sql = "SELECT date(transaction_date) as t_date, transaction_type, SUM(grand_total), SUM(tax_total), SUM(tender_cash), SUM(tender_card), SUM(tender_bank), SUM(discount_total), COUNT(*) "
                + "FROM transactions " +
                "WHERE date(transaction_date) BETWEEN date(?) AND date(?) " +
                "GROUP BY t_date, transaction_type";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, startDate.toString());
            stmt.setString(2, endDate.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String dateStr = rs.getString("t_date");
                if (dateStr == null)
                    continue;
                java.time.LocalDate date = java.time.LocalDate.parse(dateStr);

                String type = rs.getString("transaction_type");
                BigDecimal total = rs.getBigDecimal(3) != null ? rs.getBigDecimal(3) : BigDecimal.ZERO;
                BigDecimal tax = rs.getBigDecimal(4) != null ? rs.getBigDecimal(4) : BigDecimal.ZERO;
                BigDecimal cash = rs.getBigDecimal(5) != null ? rs.getBigDecimal(5) : BigDecimal.ZERO;
                BigDecimal card = rs.getBigDecimal(6) != null ? rs.getBigDecimal(6) : BigDecimal.ZERO;
                BigDecimal bank = rs.getBigDecimal(7) != null ? rs.getBigDecimal(7) : BigDecimal.ZERO;
                BigDecimal discount = rs.getBigDecimal(8) != null ? rs.getBigDecimal(8) : BigDecimal.ZERO;
                int count = rs.getInt(9); // now index 9

                DailyFinancialDTO dto = stats.getOrDefault(date, new DailyFinancialDTO(date, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO, 0));

                boolean isRefundLogic = "REFUND".equalsIgnoreCase(type)
                        || ("SALE".equalsIgnoreCase(type) && total.signum() < 0);

                if (isRefundLogic) {
                    dto = new DailyFinancialDTO(
                            date,
                            dto.totalSales,
                            dto.totalTax, // Refunds usually don't tax reverse here visually or? Original logic:
                                          // totalTax add tax?
                            // Actually if refund, tax is negative?
                            // If total is negative, tax is negative.
                            // If we want "Total Tax" to only be TAX COLLECTED on SALES, we shouldn't add
                            // negative tax here.
                            // Let's stick to original behavior: Refunds added, Sales added.
                            // But wait, user wants separation.
                            // If Refund, it adds to Refund Total (Positive).
                            dto.totalRefunds.add(total.abs()),
                            dto.cashSales,
                            dto.cardSales,
                            dto.bankSales,
                            dto.laybyPayments,
                            dto.totalDiscounts,
                            dto.txnCount + count);
                } else {
                    // Positive Sale
                    dto = new DailyFinancialDTO(
                            date,
                            dto.totalSales.add(total),
                            dto.totalTax.add(tax),
                            dto.totalRefunds,
                            dto.cashSales.add(cash),
                            dto.cardSales.add(card),
                            dto.bankSales.add(bank),
                            dto.laybyPayments,
                            dto.totalDiscounts.add(discount),
                            dto.txnCount + count);
                }
                stats.put(date, dto);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }

    public record DailyFinancialDTO(
            java.time.LocalDate date,
            BigDecimal totalSales,
            BigDecimal totalTax,
            BigDecimal totalRefunds,
            BigDecimal cashSales,
            BigDecimal cardSales,
            BigDecimal bankSales,
            BigDecimal laybyPayments,
            BigDecimal totalDiscounts,
            int txnCount) {

        public DailyFinancialDTO withLayby(BigDecimal amount) {
            return new DailyFinancialDTO(date, totalSales, totalTax, totalRefunds, cashSales, cardSales, bankSales,
                    laybyPayments.add(amount), totalDiscounts, txnCount);
        }
    }

    public java.time.LocalDateTime getLastSaleDate(int productId) {
        String sql = "SELECT t.transaction_date " +
                "FROM transaction_items ti " +
                "JOIN transactions t ON ti.transaction_id = t.transaction_id " +
                "WHERE ti.product_id = ? " +
                "AND t.transaction_type = 'SALE' " +
                "ORDER BY t.transaction_date DESC LIMIT 1";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getTimestamp(1).toLocalDateTime();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // Never sold
    }

    public java.util.Map<Integer, BigDecimal> getHourlySalesStats(java.time.LocalDate date) {
        java.util.Map<Integer, BigDecimal> stats = new java.util.HashMap<>();
        // Initialize all hours with 0
        for (int i = 0; i < 24; i++) {
            stats.put(i, BigDecimal.ZERO);
        }

        String sql = "SELECT strftime('%H', transaction_date) as hour, SUM(grand_total) " +
                "FROM transactions " +
                "WHERE transaction_type = 'SALE' " +
                "AND date(transaction_date) = date(?) " +
                "GROUP BY hour";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, date.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int hour = Integer.parseInt(rs.getString(1));
                BigDecimal total = rs.getBigDecimal(2);
                stats.put(hour, total);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }

    public java.util.Map<java.time.LocalDate, BigDecimal> getWeeklySalesStats(java.time.LocalDate endDate) {
        java.util.Map<java.time.LocalDate, BigDecimal> stats = new java.util.TreeMap<>(); // TreeMap for sorting
        java.time.LocalDate startDate = endDate.minusDays(6);

        // Fill with zeros first
        for (java.time.LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            stats.put(date, BigDecimal.ZERO);
        }

        String sql = "SELECT date(transaction_date) as d, SUM(grand_total) " +
                "FROM transactions " +
                "WHERE transaction_type = 'SALE' " +
                "AND date(transaction_date) BETWEEN date(?) AND date(?) " +
                "GROUP BY d";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, startDate.toString());
            stmt.setString(2, endDate.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String dStr = rs.getString(1);
                if (dStr != null) {
                    java.time.LocalDate d = java.time.LocalDate.parse(dStr);
                    stats.put(d, rs.getBigDecimal(2));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }

    public java.util.Map<Integer, BigDecimal> getMonthlySalesStats(java.time.YearMonth month) {
        java.util.Map<Integer, BigDecimal> stats = new java.util.TreeMap<>();
        java.time.LocalDate start = month.atDay(1);
        java.time.LocalDate end = month.atEndOfMonth();

        // Fill with zeros
        for (int i = 1; i <= month.lengthOfMonth(); i++) {
            stats.put(i, BigDecimal.ZERO);
        }

        String sql = "SELECT strftime('%d', transaction_date) as day, SUM(grand_total) " +
                "FROM transactions " +
                "WHERE transaction_type = 'SALE' " +
                "AND date(transaction_date) BETWEEN date(?) AND date(?) " +
                "GROUP BY day";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, start.toString());
            stmt.setString(2, end.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int day = Integer.parseInt(rs.getString(1));
                stats.put(day, rs.getBigDecimal(2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }

    // Detailed Z-Read DTO for production reporting
    public record DetailedZReadDTO(
            // Sales by tender type
            int accountSalesCount,
            BigDecimal accountSalesAmount,
            int cashSalesCount,
            BigDecimal cashSalesAmount,
            int cardSalesCount,
            BigDecimal cardSalesAmount,
            int bankSalesCount,
            BigDecimal bankSalesAmount,

            // Refunds by tender type
            int accountRefundCount,
            BigDecimal accountRefundAmount,
            int cashRefundCount,
            BigDecimal cashRefundAmount,
            int cardRefundCount,
            BigDecimal cardRefundAmount,
            int bankRefundCount,
            BigDecimal bankRefundAmount,

            // Totals
            BigDecimal totalSales,
            BigDecimal totalRefunds,
            BigDecimal totalTax,
            BigDecimal totalCost, // New
            int totalSalesCount,
            int totalRefundCount) {
    }

    public DetailedZReadDTO getDetailedZReadData(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        // Initialize counters
        int accountSalesCount = 0, cashSalesCount = 0, cardSalesCount = 0, bankSalesCount = 0;
        BigDecimal accountSalesAmt = BigDecimal.ZERO, cashSalesAmt = BigDecimal.ZERO;
        BigDecimal cardSalesAmt = BigDecimal.ZERO, bankSalesAmt = BigDecimal.ZERO;

        int accountRefundCount = 0, cashRefundCount = 0, cardRefundCount = 0, bankRefundCount = 0;
        BigDecimal accountRefundAmt = BigDecimal.ZERO, cashRefundAmt = BigDecimal.ZERO;
        BigDecimal cardRefundAmt = BigDecimal.ZERO, bankRefundAmt = BigDecimal.ZERO;

        BigDecimal totalSales = BigDecimal.ZERO, totalRefunds = BigDecimal.ZERO, totalTax = BigDecimal.ZERO;
        int totalSalesCount = 0, totalRefundCount = 0;

        // Query for SALES (Strictly Positive Sales)
        // Correct join to get sum of cost_price_at_sale * quantity
        String salesSql = "SELECT " +
                "COUNT(DISTINCT t.transaction_id) as txn_count, " +
                "SUM(CASE WHEN t.tender_account > 0 THEN 1 ELSE 0 END) as account_count, " +
                "SUM(CASE WHEN t.tender_cash > 0 THEN 1 ELSE 0 END) as cash_count, " +
                "SUM(CASE WHEN t.tender_card > 0 THEN 1 ELSE 0 END) as card_count, " +
                "SUM(CASE WHEN t.tender_bank > 0 THEN 1 ELSE 0 END) as bank_count, " +
                "SUM(t.tender_account) as account_amt, " +
                "SUM(t.tender_cash) as cash_amt, " +
                "SUM(t.tender_card) as card_amt, " +
                "SUM(t.tender_bank) as bank_amt, " +
                "SUM(t.grand_total) as total, " +
                "SUM(t.tax_total) as tax, " +
                // Cost Calculation: Sum of (item cost * item qty)
                "(SELECT SUM(ti.cost_price_at_sale * ti.quantity) " +
                " FROM transaction_items ti " +
                " JOIN transactions t2 ON ti.transaction_id = t2.transaction_id " +
                " WHERE t2.transaction_type = 'SALE' " +
                " AND t2.grand_total >= 0 " +
                " AND t2.transaction_date BETWEEN ? AND ?) as total_cost " +
                "FROM transactions t " +
                "WHERE t.transaction_type = 'SALE' " +
                "AND t.status = 'COMPLETED' " +
                "AND t.grand_total >= 0 " +
                "AND substr(t.transaction_date, 1, 10) BETWEEN ? AND ?";

        BigDecimal totalCost = BigDecimal.ZERO;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(salesSql)) {
            stmt.setString(1, startDate.toString());
            stmt.setString(2, endDate.toString());
            stmt.setString(3, startDate.toString());
            stmt.setString(4, endDate.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                totalSalesCount = rs.getInt("txn_count");
                accountSalesCount = rs.getInt("account_count");
                cashSalesCount = rs.getInt("cash_count");
                cardSalesCount = rs.getInt("card_count");
                bankSalesCount = rs.getInt("bank_count");

                accountSalesAmt = rs.getBigDecimal("account_amt");
                if (accountSalesAmt == null)
                    accountSalesAmt = BigDecimal.ZERO;
                cashSalesAmt = rs.getBigDecimal("cash_amt");
                if (cashSalesAmt == null)
                    cashSalesAmt = BigDecimal.ZERO;
                cardSalesAmt = rs.getBigDecimal("card_amt");
                if (cardSalesAmt == null)
                    cardSalesAmt = BigDecimal.ZERO;
                bankSalesAmt = rs.getBigDecimal("bank_amt");
                if (bankSalesAmt == null)
                    bankSalesAmt = BigDecimal.ZERO;

                totalSales = rs.getBigDecimal("total");
                if (totalSales == null)
                    totalSales = BigDecimal.ZERO;
                totalTax = rs.getBigDecimal("tax");
                if (totalTax == null)
                    totalTax = BigDecimal.ZERO;

                totalCost = rs.getBigDecimal("total_cost");
                if (totalCost == null)
                    totalCost = BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Query for REFUNDS (Includes explicit REFUNDs and Negative Sales)
        // We use ABS() to ensure we get positive magnitudes for the report
        String refundSql = "SELECT " +
                "COUNT(*) as txn_count, " +
                "SUM(CASE WHEN ABS(tender_account) > 0 THEN 1 ELSE 0 END) as account_count, " +
                "SUM(CASE WHEN ABS(tender_cash) > 0 THEN 1 ELSE 0 END) as cash_count, " +
                "SUM(CASE WHEN ABS(tender_card) > 0 THEN 1 ELSE 0 END) as card_count, " +
                "SUM(CASE WHEN ABS(tender_bank) > 0 THEN 1 ELSE 0 END) as bank_count, " +
                "SUM(ABS(tender_account)) as account_amt, " +
                "SUM(ABS(tender_cash)) as cash_amt, " +
                "SUM(ABS(tender_card)) as card_amt, " +
                "SUM(ABS(tender_bank)) as bank_amt, " +
                "SUM(ABS(grand_total)) as total " +
                "FROM transactions " +
                "WHERE (transaction_type = 'REFUND' OR (transaction_type = 'SALE' AND grand_total < 0)) " +
                "AND status = 'COMPLETED' " +
                "AND substr(transaction_date, 1, 10) BETWEEN ? AND ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(refundSql)) {
            stmt.setString(1, startDate.toString());
            stmt.setString(2, endDate.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                totalRefundCount = rs.getInt("txn_count");
                accountRefundCount = rs.getInt("account_count");
                cashRefundCount = rs.getInt("cash_count");
                cardRefundCount = rs.getInt("card_count");
                bankRefundCount = rs.getInt("bank_count");

                accountRefundAmt = rs.getBigDecimal("account_amt");
                if (accountRefundAmt == null)
                    accountRefundAmt = BigDecimal.ZERO;
                cashRefundAmt = rs.getBigDecimal("cash_amt");
                if (cashRefundAmt == null)
                    cashRefundAmt = BigDecimal.ZERO;
                cardRefundAmt = rs.getBigDecimal("card_amt");
                if (cardRefundAmt == null)
                    cardRefundAmt = BigDecimal.ZERO;
                bankRefundAmt = rs.getBigDecimal("bank_amt");
                if (bankRefundAmt == null)
                    bankRefundAmt = BigDecimal.ZERO;

                totalRefunds = rs.getBigDecimal("total");
                if (totalRefunds == null)
                    totalRefunds = BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return new DetailedZReadDTO(
                accountSalesCount, accountSalesAmt,
                cashSalesCount, cashSalesAmt,
                cardSalesCount, cardSalesAmt,
                bankSalesCount, bankSalesAmt,
                accountRefundCount, accountRefundAmt,
                cashRefundCount, cashRefundAmt,
                cardRefundCount, cardRefundAmt,
                bankRefundCount, bankRefundAmt,
                totalSales, totalRefunds, totalTax,
                totalCost, // New
                totalSalesCount, totalRefundCount);
    }
}
