package com.malek.pos.database;

import com.malek.pos.models.AuditLog;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

public class AuditDAO {    public AuditDAO() {
        // Connection pool managed by DatabaseManager - get connections per operation
    }

    public void logAction(int userId, String actionType, String description, Integer supervisorId) {
        String sql = "INSERT INTO audit_logs (user_id, action_type, description, timestamp, supervisor_id) VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, actionType);
            stmt.setString(3, description);

            if (supervisorId != null) {
                stmt.setInt(4, supervisorId);
            } else {
                stmt.setNull(4, Types.INTEGER);
            }

            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("Failed to write audit log: " + actionType);
        }
    }
}
