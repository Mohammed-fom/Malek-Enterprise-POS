package com.malek.pos;

import com.malek.pos.database.TransactionDAO;
import com.malek.pos.database.LaybyDAO;
import com.malek.pos.database.DatabaseManager;
import java.time.LocalDate;
import java.sql.Connection;

public class DebugDB {
    public static void main(String[] args) {
        System.out.println("DEBUG: Testing Reports Logic via DebugDB...");
        try {
            LocalDate today = LocalDate.now();
            System.out.println("Querying for date: " + today);

            System.out.println("DB Path: " + new java.io.File("pos_enterprise.db").getAbsolutePath());

            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                System.out.println("DB Connection Successful: " + !conn.isClosed());

                String rawSql = "SELECT quote(transaction_date) FROM transactions ORDER BY transaction_id DESC LIMIT 1";
                try (java.sql.Statement stmt = conn.createStatement();
                        java.sql.ResultSet rs = stmt.executeQuery(rawSql)) {
                    while (rs.next()) {
                        System.out.println("QUOTED_DATE: " + rs.getString(1));
                    }
                }
            }

            System.out.println("TEST COMPLETED SUCCESSFULLY");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("TEST FAILED");
        }
        System.exit(0);
    }
}
