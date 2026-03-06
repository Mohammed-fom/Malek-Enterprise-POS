package com.malek.pos.database;

import com.malek.pos.models.Product;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProductDAO {

    private static final Logger logger = LoggerFactory.getLogger(ProductDAO.class);

    public ProductDAO() {
        // Connection pool managed by DatabaseManager - get connections per operation
    }

    public Product findByBarcodeOrCode(String query) {
        // Search by Barcode or Product Code
        String sql = "SELECT p.* FROM products p LEFT JOIN barcodes b ON p.product_id = b.product_id WHERE b.barcode = ? OR p.product_code = ? LIMIT 1";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, query);
            stmt.setString(2, query);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToProduct(rs, conn);
            }
        } catch (SQLException e) {
            logger.error("Database error in ProductDAO", e);
        }

        // Check if numeric ID
        try {
            int id = Integer.parseInt(query);
            return findById(id);
        } catch (NumberFormatException e) {
            // Not an ID
        }

        return null;
    }

    public Product findByBarcode(String barcode) {
        return findByBarcodeOrCode(barcode);
    }

    public boolean isBarcodeExists(String barcode) {
        String sql = "SELECT 1 FROM barcodes WHERE barcode = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, barcode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next())
                    return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // Also check products.product_code? User said "create a barcode system of 6
        // numbers".
        // The current findByBarcodeOrCode checks both tables. To be safe, we should
        // check both if we want absolute uniqueness across everything.
        // But usually barcode is strictly the barcode table. Let's stick to barcodes
        // table for now.
        return false;
    }

    public Product getProductById(int id) {
        return findById(id);
    }

    public com.malek.pos.models.Product findById(int id) {
        String query = "SELECT * FROM products WHERE product_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToProduct(rs, conn);
            }
        } catch (SQLException e) {
            logger.error("Database error in ProductDAO", e);
        }
        return null;
    }

    // Fallback or explicit text search
    public List<Product> searchByDescription(String descriptionQuery) {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE description LIKE ? OR product_code LIKE ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            String q = "%" + descriptionQuery + "%";
            stmt.setString(1, q);
            stmt.setString(2, q);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                products.add(mapResultSetToProduct(rs, conn));
            }
        } catch (SQLException e) {
            logger.error("Database error in ProductDAO", e);
        }
        return products;
    }

    public List<Product> getAllProducts() {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products ORDER BY description";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                java.sql.Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                products.add(mapResultSetToProduct(rs, conn));
            }
        } catch (SQLException e) {
            logger.error("Database error in ProductDAO", e);
        }
        return products;
    }

    public boolean addProduct(Product p) {
        String sql = "INSERT INTO products (description, category, cost_price, price_retail, price_trade, tax_rate, stock_on_hand, low_stock_threshold, is_service_item, supplier_id, product_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, p.getDescription());
            stmt.setString(2, p.getCategory());
            stmt.setBigDecimal(3, p.getCostPrice());
            stmt.setBigDecimal(4, p.getPriceRetail());
            stmt.setBigDecimal(5, p.getPriceTrade());
            stmt.setBigDecimal(6, p.getTaxRate());
            stmt.setBigDecimal(7, p.getStockOnHand());
            stmt.setBigDecimal(8, p.getLowStockThreshold());
            stmt.setBoolean(9, p.isServiceItem());
            stmt.setInt(10, p.getSupplierId());
            stmt.setString(11, p.getProductCode());
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        p.setProductId(rs.getInt(1));
                        if (p.getBarcode() != null && !p.getBarcode().isEmpty()) {
                            addBarcode(p.getProductId(), p.getBarcode());
                        }
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            logger.error("Database error in ProductDAO", e);
        }
        return false;
    }

    public int getLowStockCount() {
        String sql = "SELECT COUNT(*) FROM products WHERE stock_on_hand <= low_stock_threshold AND is_service_item = 0";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Database error in ProductDAO", e);
        }
        return 0;
    }

    public boolean updateProduct(Product p) {
        String sql = "UPDATE products SET description=?, category=?, cost_price=?, price_retail=?, price_trade=?, tax_rate=?, stock_on_hand=?, low_stock_threshold=?, is_service_item=?, supplier_id=?, product_code=? WHERE product_id=?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, p.getDescription());
            stmt.setString(2, p.getCategory());
            stmt.setBigDecimal(3, p.getCostPrice());
            stmt.setBigDecimal(4, p.getPriceRetail());
            stmt.setBigDecimal(5, p.getPriceTrade());
            stmt.setBigDecimal(6, p.getTaxRate());
            stmt.setBigDecimal(7, p.getStockOnHand());
            stmt.setBigDecimal(8, p.getLowStockThreshold());
            stmt.setBoolean(9, p.isServiceItem());
            stmt.setInt(10, p.getSupplierId());
            stmt.setString(11, p.getProductCode());
            stmt.setInt(12, p.getProductId());

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                // Update barcode logic:
                // For simplicity, we delete existing barcodes for this product and re-add the
                // new one if changed.
                // In a real multi-barcode system, we'd need more complex logic.
                if (p.getBarcode() != null && !p.getBarcode().isEmpty()) {
                    // Check if already exists? Or just wipe all and add this one?
                    // Let's wipe all for this product to keep it a "primary barcode" behavior for
                    // now as requested.
                    deleteBarcodes(p.getProductId());
                    addBarcode(p.getProductId(), p.getBarcode());
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void addBarcode(int productId, String barcode) {
        String sql = "INSERT INTO barcodes (product_id, barcode) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            stmt.setString(2, barcode);
            stmt.executeUpdate();
        } catch (SQLException e) {
            // Ignore duplicate barcode errors for now or log them
            System.err.println("Barcode insert failed (dup?): " + e.getMessage());
        }
    }

    private void deleteBarcodes(int productId) {
        String sql = "DELETE FROM barcodes WHERE product_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Database error in ProductDAO", e);
        }
    }

    public boolean deleteProduct(int productId) {
        // Delete barcodes first (if no cascade)
        deleteBarcodes(productId);

        String sql = "DELETE FROM products WHERE product_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false; // Likely constraint violation (foreign keys)
        }
    }

    private Product mapResultSetToProduct(ResultSet rs, Connection conn) throws SQLException {
        Product p = new Product();
        p.setProductId(rs.getInt("product_id"));
        p.setDescription(rs.getString("description"));
        p.setCategory(rs.getString("category"));
        p.setCostPrice(rs.getBigDecimal("cost_price"));
        p.setPriceRetail(rs.getBigDecimal("price_retail"));
        p.setPriceTrade(rs.getBigDecimal("price_trade"));
        p.setTaxRate(rs.getBigDecimal("tax_rate"));
        p.setStockOnHand(rs.getBigDecimal("stock_on_hand"));
        p.setLowStockThreshold(rs.getBigDecimal("low_stock_threshold"));
        p.setServiceItem(rs.getBoolean("is_service_item"));
        p.setSupplierId(rs.getInt("supplier_id"));
        try {
            p.setProductCode(rs.getString("product_code"));
        } catch (SQLException e) {
            // Field might not exist yet if migration failed or cached resultset issue
        }

        // Fetch primary barcode
        try (PreparedStatement stmt = conn
                .prepareStatement("SELECT barcode FROM barcodes WHERE product_id = ? LIMIT 1")) {
            stmt.setInt(1, p.getProductId());
            ResultSet brs = stmt.executeQuery();
            if (brs.next()) {
                p.setBarcode(brs.getString("barcode"));
            }
        }
        return p;
    }

    // --- Purchase Tracking ---
    public boolean recordPurchase(int productId, int supplierId, java.math.BigDecimal qty,
            java.math.BigDecimal unitCost, String invoiceNo) {
        String sqlPurchase = "INSERT INTO purchases (supplier_id, total_cost, invoice_number, status) VALUES (?, ?, ?, 'RECEIVED')";
        String sqlItem = "INSERT INTO purchase_items (purchase_id, product_id, quantity, unit_cost) VALUES (?, ?, ?, ?)";
        String sqlUpdateProduct = "UPDATE products SET stock_on_hand = stock_on_hand + ?, cost_price = ? WHERE product_id = ?";

        // Use a single connection for the entire transaction
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);

            try {
                // 1. Create Purchase Record
                int purchaseId = 0;
                try (PreparedStatement stmt = conn.prepareStatement(sqlPurchase, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setInt(1, supplierId);
                    stmt.setBigDecimal(2, qty.multiply(unitCost));
                    stmt.setString(3, invoiceNo);
                    stmt.executeUpdate();
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next())
                            purchaseId = rs.getInt(1);
                    }
                }

                // 2. Create Purchase Item
                if (purchaseId > 0) {
                    try (PreparedStatement stmt = conn.prepareStatement(sqlItem)) {
                        stmt.setInt(1, purchaseId);
                        stmt.setInt(2, productId);
                        stmt.setBigDecimal(3, qty);
                        stmt.setBigDecimal(4, unitCost);
                        stmt.executeUpdate();
                    }

                    // 3. Update Product Stock & Cost (WAC Logic)
                    // Fetch Current Stock & Cost first
                    BigDecimal currentStock = BigDecimal.ZERO;
                    BigDecimal currentCost = BigDecimal.ZERO;

                    try (PreparedStatement stockQ = conn
                            .prepareStatement("SELECT stock_on_hand, cost_price FROM products WHERE product_id = ?")) {
                        stockQ.setInt(1, productId);
                        ResultSet rs = stockQ.executeQuery();
                        if (rs.next()) {
                            currentStock = rs.getBigDecimal("stock_on_hand");
                            currentCost = rs.getBigDecimal("cost_price");
                        }
                    }

                    // WAC Calculation
                    BigDecimal totalValBefore = currentStock.multiply(currentCost);
                    BigDecimal totalValAdded = qty.multiply(unitCost);
                    BigDecimal newTotalStock = currentStock.add(qty);

                    BigDecimal newWacCost = unitCost;
                    if (currentStock.compareTo(BigDecimal.ZERO) <= 0) {
                        // If stock was negative or zero, reset cost to incoming batch cost
                        newWacCost = unitCost;
                    } else if (newTotalStock.compareTo(BigDecimal.ZERO) > 0) {
                        // Only apply WAC if we had positive stock
                        newWacCost = totalValBefore.add(totalValAdded).divide(newTotalStock, 2,
                                java.math.RoundingMode.HALF_UP);
                    }

                    try (PreparedStatement stmt = conn.prepareStatement(sqlUpdateProduct)) {
                        stmt.setBigDecimal(1, qty);
                        stmt.setBigDecimal(2, newWacCost); // Update to new WAC
                        stmt.setInt(3, productId);
                        stmt.executeUpdate();
                    }

                    conn.commit();
                    return true;
                } else {
                    conn.rollback();
                }
            } catch (SQLException e) {
                e.printStackTrace();
                conn.rollback();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Database error in ProductDAO", e);
        }
        return false;
    }

    public List<PurchaseHistoryDTO> getPurchaseHistory(int productId) {
        List<PurchaseHistoryDTO> list = new ArrayList<>();
        String sql = "SELECT p.purchase_date, s.company_name, pi.quantity, pi.unit_cost, p.invoice_number " +
                "FROM purchase_items pi " +
                "JOIN purchases p ON pi.purchase_id = p.purchase_id " +
                "LEFT JOIN suppliers s ON p.supplier_id = s.supplier_id " +
                "WHERE pi.product_id = ? " +
                "ORDER BY p.purchase_date DESC";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new PurchaseHistoryDTO(
                        rs.getTimestamp("purchase_date").toLocalDateTime(),
                        rs.getString("company_name"),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("unit_cost"),
                        rs.getString("invoice_number")));
            }
        } catch (SQLException e) {
            logger.error("Database error in ProductDAO", e);
        }
        return list;
    }

    public record PurchaseHistoryDTO(java.time.LocalDateTime date, String supplier, java.math.BigDecimal qty,
            java.math.BigDecimal cost, String invoice) {
    }

    // --- Stock Tracking / Wastage ---
    public void logWastage(int productId, java.math.BigDecimal qty, String reason, int userId) throws SQLException {
        // Wastage means removing stock. Qty should be negative in log, but input
        // usually positive "How much wasted?"
        // Implementing: Stock decreases by qty. Log records -qty.
        String updateStock = "UPDATE products SET stock_on_hand = stock_on_hand - ? WHERE product_id = ?";
        String insertLog = "INSERT INTO inventory_logs (product_id, quantity_change, reason, user_id) VALUES (?, ?, ?, ?)";

        // Use a single connection for the entire transaction
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ustmt = conn.prepareStatement(updateStock)) {
                    ustmt.setBigDecimal(1, qty);
                    ustmt.setInt(2, productId);
                    ustmt.executeUpdate();
                }
                try (PreparedStatement lstmt = conn.prepareStatement(insertLog)) {
                    lstmt.setInt(1, productId);
                    lstmt.setBigDecimal(2, qty.negate()); // Negative change
                    lstmt.setString(3, reason);
                    lstmt.setInt(4, userId);
                    lstmt.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public java.util.List<Product> getLowStockProducts() {
        java.util.List<Product> list = new java.util.ArrayList<>();
        String sql = "SELECT * FROM products WHERE stock_on_hand <= low_stock_threshold AND is_service_item = 0";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToProduct(rs, conn));
            }
        } catch (SQLException e) {
            logger.error("Database error in ProductDAO", e);
        }
        return list;
    }
}
