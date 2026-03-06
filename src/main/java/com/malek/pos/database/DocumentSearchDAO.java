package com.malek.pos.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class DocumentSearchDAO {

    public record SearchResult(
            String type, // SALE, REFUND, LAYBY, QUOTE
            String id, // The DB ID (int converted to string if needed, or custom ID)
            String displayId, // The Custom ID (SALE..., LAY...)
            String description, // Customer Name or Summary
            String date,
            double amount,
            String status) {
    }    public DocumentSearchDAO() {
        // Connection pool managed by DatabaseManager - get connections per operation
    }

    public List<SearchResult> search(String query) {
        List<SearchResult> results = new ArrayList<>();
        String likeQuery = "%" + query.trim() + "%";

        // UNION Query for Transactions and Laybys
        // We cast columns to match types
        String sql = """
                SELECT
                    transaction_type as type,
                    transaction_id as id,
                    custom_transaction_id as display_id,
                    'Customer ID: ' || IFNULL(debtor_id, 'Cash') as description,
                    transaction_date as date,
                    grand_total as amount,
                    'COMPLETED' as status
                FROM transactions
                WHERE custom_transaction_id LIKE ? OR transaction_id LIKE ?

                UNION ALL

                SELECT
                    'LAYBY' as type,
                    layby_id as id,
                    custom_layby_id as display_id,
                    customer_name as description,
                    start_date as date,
                    total_amount as amount,
                    status
                FROM laybys
                WHERE custom_layby_id LIKE ? OR customer_name LIKE ?

                UNION ALL

                SELECT
                    'PRODUCT' as type,
                    product_id as id,
                    product_code as display_id,
                    description as description,
                    'N/A' as date,
                    retail_price as amount,
                    'ACTIVE' as status
                FROM products
                WHERE description LIKE ? OR product_code LIKE ? OR barcode LIKE ?

                UNION ALL

                SELECT
                    'DEBTOR' as type,
                    debtor_id as id,
                    account_no as display_id,
                    name || ' (' || phone || ')' as description,
                    created_at as date,
                    current_balance as amount,
                    'ACTIVE' as status
                FROM debtors
                WHERE name LIKE ? OR account_no LIKE ? OR phone LIKE ?
                """
                + " ORDER BY date DESC LIMIT 50";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, likeQuery);
            stmt.setString(2, likeQuery);
            stmt.setString(3, likeQuery);
            stmt.setString(4, likeQuery);
            // Products
            stmt.setString(5, likeQuery);
            stmt.setString(6, likeQuery);
            stmt.setString(7, likeQuery);
            // Debtors
            stmt.setString(8, likeQuery);
            stmt.setString(9, likeQuery);
            stmt.setString(10, likeQuery);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                results.add(new SearchResult(
                        rs.getString("type"),
                        rs.getString("id"), // Keeping as string for flexibility
                        rs.getString("display_id"),
                        rs.getString("description"),
                        rs.getString("date"),
                        rs.getDouble("amount"),
                        rs.getString("status")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }
}
