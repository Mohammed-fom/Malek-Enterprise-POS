package com.malek.pos.database;

import com.malek.pos.models.Purchase;
import com.malek.pos.models.PurchaseItem;
import com.malek.pos.models.Supplier;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SupplierDAO {
    public SupplierDAO() {
        // Connection pool managed by DatabaseManager - get connections per operation
    }

    public List<Supplier> getAllSuppliers() {
        List<Supplier> list = new ArrayList<>();
        String sql = "SELECT * FROM suppliers ORDER BY company_name";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Supplier s = new Supplier();
                s.setSupplierId(rs.getInt("supplier_id"));
                s.setCompanyName(rs.getString("company_name"));
                s.setContactPerson(rs.getString("contact_person"));
                s.setEmail(rs.getString("email"));
                s.setPhone(rs.getString("phone"));
                s.setAddress(rs.getString("address"));
                s.setCurrentBalance(rs.getBigDecimal("current_balance"));
                list.add(s);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void createSupplier(Supplier s) throws SQLException {
        String sql = "INSERT INTO suppliers(company_name, contact_person, email, phone, address) VALUES(?,?,?,?,?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, s.getCompanyName());
            stmt.setString(2, s.getContactPerson());
            stmt.setString(3, s.getEmail());
            stmt.setString(4, s.getPhone());
            stmt.setString(5, s.getAddress());
            stmt.executeUpdate();
        }
    }

    // Returns warning message if price increased significantly, or null if ok
    public String createPurchase(Purchase purchase, List<PurchaseItem> items) {
        StringBuilder priceWarnings = new StringBuilder();
        String insertPurchase = "INSERT INTO purchases(supplier_id, total_cost, invoice_number, status) VALUES(?, ?, ?, ?)";
        String insertItem = "INSERT INTO purchase_items(purchase_id, product_id, quantity, unit_cost) VALUES(?, ?, ?, ?)";
        String updateStock = "UPDATE products SET stock_on_hand = stock_on_hand + ?, cost_price = ? WHERE product_id = ?";
        String updateSupplier = "UPDATE suppliers SET current_balance = current_balance + ? WHERE supplier_id = ?";
        String checkPrice = "SELECT cost_price, description FROM products WHERE product_id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Insert Purchase
                int pid = -1;
                try (PreparedStatement stmt = conn.prepareStatement(insertPurchase, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setInt(1, purchase.getSupplierId());
                    stmt.setBigDecimal(2, purchase.getTotalCost());
                    stmt.setString(3, purchase.getInvoiceNumber());
                    stmt.setString(4, "RECEIVED");
                    stmt.executeUpdate();
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next())
                            pid = rs.getInt(1);
                    }
                }

                // 2. Insert Items & Updates
                try (PreparedStatement istmt = conn.prepareStatement(insertItem);
                        PreparedStatement ustmt = conn.prepareStatement(updateStock);
                        PreparedStatement cstmt = conn.prepareStatement(checkPrice)) {

                    for (PurchaseItem item : items) {
                        // WAC Logic:
                        // 1. Get current stock and cost
                        BigDecimal currentStock = BigDecimal.ZERO;
                        BigDecimal currentCost = BigDecimal.ZERO;

                        // Check Price & Stock info
                        cstmt.setInt(1, item.getProductId());
                        ResultSet rs = cstmt.executeQuery();
                        if (rs.next()) {
                            // "checkPrice" currently selects cost_price, description.
                            // We need stock_on_hand too. Query needs updating OR we fetch full product?
                            // Let's modify checkPrice SQL above or just do a quick select here.
                            // Ideally we update the SQL constant at top of method, but replacing just this
                            // block
                            // means we might miss the SQL definition update if it's out of scope.
                            // "checkPrice" is defined at line 59: SELECT cost_price, description FROM
                            // products...

                            // Let's rely on a fresh query here for clarity and WAC
                            currentCost = rs.getBigDecimal("cost_price");
                            // Warning Log
                            if (item.getUnitCost().compareTo(currentCost) > 0) {
                                priceWarnings.append("Price Hike: ").append(rs.getString("description"))
                                        .append(" (").append(currentCost).append(" -> ").append(item.getUnitCost())
                                        .append(")\n");
                            }
                        }

                        // We need Stock On Hand for WAC.
                        // Let's execute a specific stock query using the same connection
                        try (PreparedStatement stockQ = conn
                                .prepareStatement("SELECT stock_on_hand FROM products WHERE product_id = ?")) {
                            stockQ.setInt(1, item.getProductId());
                            ResultSet srs = stockQ.executeQuery();
                            if (srs.next()) {
                                currentStock = srs.getBigDecimal("stock_on_hand");
                            }
                        }

                        // WAC Calculation
                        // newCost = ((currentStock * currentCost) + (newQty * newUnitCost)) /
                        // (currentStock + newQty)
                        BigDecimal totalValBefore = currentStock.multiply(currentCost);
                        BigDecimal totalValAdded = item.getQuantity().multiply(item.getUnitCost());
                        BigDecimal newTotalStock = currentStock.add(item.getQuantity());

                        BigDecimal newWacCost = item.getUnitCost(); // Default to new cost if stock was 0 or negative
                        if (newTotalStock.compareTo(BigDecimal.ZERO) > 0) {
                            newWacCost = totalValBefore.add(totalValAdded).divide(newTotalStock, 2,
                                    java.math.RoundingMode.HALF_UP);
                        }

                        // Insert Item
                        istmt.setInt(1, pid);
                        istmt.setInt(2, item.getProductId());
                        istmt.setBigDecimal(3, item.getQuantity());
                        istmt.setBigDecimal(4, item.getUnitCost()); // Invoice Unit Cost (Audit trail)
                        istmt.addBatch();

                        // Update Stock & Cost Price (WAC)
                        ustmt.setBigDecimal(1, item.getQuantity());
                        ustmt.setBigDecimal(2, newWacCost); // Update with WAC
                        ustmt.setInt(3, item.getProductId());
                        ustmt.addBatch();
                    }
                    istmt.executeBatch();
                    ustmt.executeBatch();
                }

                // 3. Update Supplier Balance
                try (PreparedStatement sstmt = conn.prepareStatement(updateSupplier)) {
                    sstmt.setBigDecimal(1, purchase.getTotalCost());
                    sstmt.setInt(2, purchase.getSupplierId());
                    sstmt.executeUpdate();
                }

                conn.commit();
                return priceWarnings.length() > 0 ? priceWarnings.toString() : null;
            } catch (SQLException e) {
                e.printStackTrace();
                conn.rollback();
                return "Error: " + e.getMessage();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    public void paySupplier(int supplierId, BigDecimal amount) {
        String insertPay = "INSERT INTO supplier_payments(supplier_id, amount, payment_method) VALUES(?, ?, ?)";
        String updateBal = "UPDATE suppliers SET current_balance = current_balance - ? WHERE supplier_id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = conn.prepareStatement(insertPay)) {
                    stmt.setInt(1, supplierId);
                    stmt.setBigDecimal(2, amount);
                    stmt.setString(3, "CASH");
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(updateBal)) {
                    stmt.setBigDecimal(1, amount);
                    stmt.setInt(2, supplierId);
                    stmt.executeUpdate();
                }
                conn.commit();
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

    public void updateSupplier(Supplier s) throws SQLException {
        String sql = "UPDATE suppliers SET company_name=?, contact_person=?, email=?, phone=?, address=? WHERE supplier_id=?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, s.getCompanyName());
            stmt.setString(2, s.getContactPerson());
            stmt.setString(3, s.getEmail());
            stmt.setString(4, s.getPhone());
            stmt.setString(5, s.getAddress());
            stmt.setInt(6, s.getSupplierId());
            stmt.executeUpdate();
        }
    }

    public void deleteSupplier(int id) throws SQLException {
        String sql = "DELETE FROM suppliers WHERE supplier_id=?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }
}
