package com.malek.pos.database;

import com.malek.pos.models.Debtor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DebtorDAO {    public DebtorDAO() {
        // Connection pool managed by DatabaseManager - get connections per operation
    }

    public Debtor findByAccountNo(String accountNo) {
        String sql = "SELECT * FROM debtors WHERE account_no = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, accountNo);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToDebtor(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Debtor getDebtorById(int debtorId) {
        String sql = "SELECT * FROM debtors WHERE debtor_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, debtorId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToDebtor(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Debtor mapResultSetToDebtor(ResultSet rs) throws SQLException {
        Debtor d = new Debtor();
        d.setDebtorId(rs.getInt("debtor_id"));
        d.setAccountNo(rs.getString("account_no"));
        d.setCustomerName(rs.getString("customer_name"));
        d.setPhone(rs.getString("phone"));
        d.setEmail(rs.getString("email"));
        d.setAddress(rs.getString("address"));
        d.setCreditLimit(rs.getBigDecimal("credit_limit"));
        d.setCurrentBalance(rs.getBigDecimal("current_balance"));
        d.setPriceTier(rs.getString("price_tier"));
        return d;
    }

    public java.util.List<Debtor> searchDebtors(String query) {
        java.util.List<Debtor> list = new java.util.ArrayList<>();
        String sql = "SELECT * FROM debtors WHERE customer_name LIKE ? OR account_no LIKE ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String q = "%" + query + "%";
            stmt.setString(1, q);
            stmt.setString(2, q);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(mapResultSetToDebtor(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public java.util.List<Debtor> getAllDebtors() {
        java.util.List<Debtor> list = new java.util.ArrayList<>();
        String sql = "SELECT * FROM debtors";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                list.add(mapResultSetToDebtor(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean addDebtor(Debtor d) {
        String sql = "INSERT INTO debtors (account_no, customer_name, phone, email, address, credit_limit, current_balance, price_tier) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, d.getAccountNo());
            stmt.setString(2, d.getCustomerName());
            stmt.setString(3, d.getPhone());
            stmt.setString(4, d.getEmail());
            stmt.setString(5, d.getAddress());
            stmt.setBigDecimal(6, d.getCreditLimit());
            stmt.setBigDecimal(7, d.getCurrentBalance() != null ? d.getCurrentBalance() : java.math.BigDecimal.ZERO);
            stmt.setString(8, d.getPriceTier());
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateDebtor(Debtor d) {
        String sql = "UPDATE debtors SET account_no=?, customer_name=?, phone=?, email=?, address=?, credit_limit=?, price_tier=? WHERE debtor_id=?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, d.getAccountNo());
            stmt.setString(2, d.getCustomerName());
            stmt.setString(3, d.getPhone());
            stmt.setString(4, d.getEmail());
            stmt.setString(5, d.getAddress());
            stmt.setBigDecimal(6, d.getCreditLimit());
            stmt.setString(7, d.getPriceTier());
            stmt.setInt(8, d.getDebtorId());
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Update balance directly (e.g. for payments or manual adjustments)
    public boolean updateBalance(int debtorId, java.math.BigDecimal newBalance) {
        String sql = "UPDATE debtors SET current_balance = ? WHERE debtor_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBigDecimal(1, newBalance);
            stmt.setInt(2, debtorId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
