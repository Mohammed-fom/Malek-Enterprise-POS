package com.malek.pos.database;

import com.malek.pos.models.Shift;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;

public class ShiftDAO {    public ShiftDAO() {
        // Connection pool managed by DatabaseManager - get connections per operation
    }

    public int startShift(int userId, BigDecimal floatAmount) {
        String sql = "INSERT INTO shifts (user_id, opening_float, status) VALUES (?, ?, 'OPEN')";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, userId);
            stmt.setBigDecimal(2, floatAmount);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public Shift getCurrentOpenShift() {
        String sql = "SELECT * FROM shifts WHERE status = 'OPEN' ORDER BY shift_id DESC LIMIT 1";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return mapResultSetToShift(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // No open shift
    }

    public void closeShift(Shift shift) {
        String sql = "UPDATE shifts SET end_time = CURRENT_TIMESTAMP, declared_cash = ?, declared_card = ?, " +
                "system_cash = ?, system_card = ?, status = 'CLOSED' WHERE shift_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBigDecimal(1, shift.getDeclaredCash());
            stmt.setBigDecimal(2, shift.getDeclaredCard());
            stmt.setBigDecimal(3, shift.getSystemCash());
            stmt.setBigDecimal(4, shift.getSystemCard());
            stmt.setInt(5, shift.getShiftId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Calculate System Totals for X-Read / Z-Read
    public BigDecimal getSystemCashTotal(int shiftId) {
        // Sum 'tender_cash' - 'change_due' from transactions linked to this shift?
        // Note: Schema currently links txn to shift_id (added in creation logic? No
        // wait, missing update)
        // Correction: My Transaction Logic didn't link shift_id.
        // Logic: Sum transactions where transaction_date > shift.start_time AND user_id
        // = shift.user_id
        // Ideally we should have updated the insert to include shift_id. For now, let's
        // use time range of the shift.

        // Fetch start time from shift
        // ... simplistic approach for prototype:

        // Better Approach: Update transactions table to include shift_id? Yes, schema
        // has it.
        // I need to ensure when saving transaction I set the shift_id using the current
        // open shift.

        // For now, let's query sums where shift_id = ?
        String sql = "SELECT SUM(tender_cash - change_due) FROM transactions WHERE shift_id = ? AND status = 'COMPLETED'";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, shiftId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next())
                return rs.getBigDecimal(1) != null ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getSystemCardTotal(int shiftId) {
        String sql = "SELECT SUM(tender_card) FROM transactions WHERE shift_id = ? AND status = 'COMPLETED'";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, shiftId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next())
                return rs.getBigDecimal(1) != null ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return BigDecimal.ZERO;
    }

    private Shift mapResultSetToShift(ResultSet rs) throws SQLException {
        Shift s = new Shift();
        s.setShiftId(rs.getInt("shift_id"));
        s.setUserId(rs.getInt("user_id"));
        // s.setStartTime(rs.getTimestamp("start_time").toLocalDateTime()); //
        // simplified
        s.setOpeningFloat(rs.getBigDecimal("opening_float"));
        s.setStatus(rs.getString("status"));
        return s;
    }
}
