package com.malek.pos.database;

import com.malek.pos.models.Expense;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ExpenseDAO {    public ExpenseDAO() {
        // Connection pool managed by DatabaseManager - get connections per operation
    }

    public List<Expense> getAllExpenses() {
        List<Expense> list = new ArrayList<>();
        String sql = "SELECT * FROM expenses ORDER BY expense_date DESC";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Expense e = new Expense();
                e.setExpenseId(rs.getInt("expense_id"));
                e.setDescription(rs.getString("description"));
                e.setAmount(rs.getBigDecimal("amount"));
                e.setCategory(rs.getString("category"));
                e.setExpenseDate(rs.getTimestamp("expense_date").toLocalDateTime());
                e.setUserId(rs.getInt("user_id"));
                list.add(e);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void addExpense(Expense e) throws SQLException {
        String sql = "INSERT INTO expenses (description, amount, category, user_id) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, e.getDescription());
            stmt.setBigDecimal(2, e.getAmount());
            stmt.setString(3, e.getCategory());
            stmt.setInt(4, e.getUserId());
            stmt.executeUpdate();
        }
    }
}
