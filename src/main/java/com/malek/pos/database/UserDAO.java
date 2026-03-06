package com.malek.pos.database;

import com.malek.pos.models.User;
import com.malek.pos.utils.PasswordHashUtil;
import java.sql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserDAO {

    private static final Logger logger = LoggerFactory.getLogger(UserDAO.class);

    public UserDAO() {
        // Connection pool managed by DatabaseManager - get connections per operation
    }

    public User authenticate(String username, String password) {
        String sql = "SELECT u.*, r.role_name FROM users u JOIN roles r ON u.role_id = r.role_id WHERE u.username = ? AND u.is_active = 1";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    // Check if password is hashed or plaintext (for migration)
                    if (PasswordHashUtil.isHashed(storedHash)) {
                        // Verify BCrypt hash
                        if (PasswordHashUtil.verifyPassword(password, storedHash)) {
                            return mapResultSetToUser(rs);
                        }
                    } else {
                        // Legacy plaintext comparison (auto-migrate on next update)
                        if (password.equals(storedHash)) {
                            return mapResultSetToUser(rs);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error authenticating user", e);
        }
        return null;
    }

    public User authenticateByPin(String pin) {
        String sql = "SELECT u.*, r.role_name FROM users u JOIN roles r ON u.role_id = r.role_id WHERE u.pin_code = ? AND u.is_active = 1";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pin);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error authenticating by PIN", e);
        }
        return null; // Invalid PIN
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setFullName(rs.getString("full_name"));
        u.setRoleId(rs.getInt("role_id"));
        u.setPinCode(rs.getString("pin_code"));
        u.setActive(rs.getBoolean("is_active"));
        u.setRoleName(rs.getString("role_name"));
        return u;
    }

    // --- CRUD ---
    public java.util.List<User> getAllUsers() {
        java.util.List<User> list = new java.util.ArrayList<>();
        String sql = "SELECT u.*, r.role_name FROM users u LEFT JOIN roles r ON u.role_id = r.role_id ORDER BY u.username";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToUser(rs));
            }
        } catch (SQLException e) {
            logger.error("Error fetching all users", e);
        }
        return list;
    }

    public void createUser(User u) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, full_name, role_id, pin_code, is_active) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, u.getUsername());
            // Hash password if not already hashed
            String passwordToStore = u.getPasswordHash();
            if (!PasswordHashUtil.isHashed(passwordToStore)) {
                passwordToStore = PasswordHashUtil.hashPassword(passwordToStore);
            }
            stmt.setString(2, passwordToStore);
            stmt.setString(3, u.getFullName());
            stmt.setInt(4, u.getRoleId());
            stmt.setString(5, u.getPinCode());
            stmt.setBoolean(6, u.isActive());
            stmt.executeUpdate();
        }
    }

    public void updateUser(User u) throws SQLException {
        String sql = "UPDATE users SET username=?, password_hash=?, full_name=?, role_id=?, pin_code=?, is_active=? WHERE user_id=?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, u.getUsername());
            // Hash password if not already hashed
            String passwordToStore = u.getPasswordHash();
            if (!PasswordHashUtil.isHashed(passwordToStore)) {
                passwordToStore = PasswordHashUtil.hashPassword(passwordToStore);
            }
            stmt.setString(2, passwordToStore);
            stmt.setString(3, u.getFullName());
            stmt.setInt(4, u.getRoleId());
            stmt.setString(5, u.getPinCode());
            stmt.setBoolean(6, u.isActive());
            stmt.setInt(7, u.getUserId());
            stmt.executeUpdate();
        }
    }

    public void deleteUser(int userId) throws SQLException {
        // Soft delete preferred? Or Hard delete?
        // Let's do Soft Delete to preserve history usually, but requirement said
        // "Delete".
        // Schema constraints might block hard delete if transactions exist.
        // Let's try Hard Delete, if fails fallback or user sees error.
        String sql = "DELETE FROM users WHERE user_id=?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
    }

    public java.util.List<String> getAllRoles() {
        java.util.List<String> roles = new java.util.ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT role_name FROM roles")) {
            while (rs.next()) {
                roles.add(rs.getString(1));
            }
        } catch (SQLException e) {
            logger.error("Error fetching roles", e);
        }
        return roles;
    }

    public int getRoleId(String roleName) {
        String sql = "SELECT role_id FROM roles WHERE role_name = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, roleName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Error getting role ID for: {}", roleName, e);
        }
        return 2; // Default to Cashier/Low priv if not found
    }
}
