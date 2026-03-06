package com.malek.pos.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DB_URL = "jdbc:sqlite:pos_enterprise.db";
    private static DatabaseManager instance;
    private HikariDataSource dataSource;

    private DatabaseManager() {
        try {
            // Configure HikariCP connection pool
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setMaximumPoolSize(10); // Max 10 connections
            config.setMinimumIdle(2); // Keep 2 idle connections ready
            config.setConnectionTimeout(30000); // 30 seconds
            config.setIdleTimeout(600000); // 10 minutes
            config.setMaxLifetime(1800000); // 30 minutes

            dataSource = new HikariDataSource(config);
            initializeDatabase();
        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Get a connection from the pool.
     * IMPORTANT: Connections are automatically returned to pool when closed.
     */
    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Error getting connection from pool", e);
        }
    }

    /**
     * Shutdown the connection pool gracefully.
     */
    public void closeConnection() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed.");
        }
    }

    /**
     * Reinitialize the connection pool (used after database restore).
     */
    private void reconnect() throws SQLException {
        // Close existing pool if open
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }

        // Reinitialize HikariCP connection pool
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        dataSource = new HikariDataSource(config);
        logger.info("Database connection pool reinitialized.");
    }

    private void initializeDatabase() {
        try (InputStream is = getClass().getResourceAsStream("/schema.sql")) {
            if (is == null) {
                logger.warn("No schema.sql found, skipping init.");
                return;
            }
            try (Scanner scanner = new Scanner(is).useDelimiter(";");
                    Connection conn = getConnection();
                    Statement statement = conn.createStatement()) {
                while (scanner.hasNext()) {
                    String sql = scanner.next().trim();
                    if (!sql.isEmpty()) {
                        statement.execute(sql);
                    }
                }

                // MIGRATION: Add custom_transaction_id if missing
                try {
                    statement.execute("ALTER TABLE transactions ADD COLUMN custom_transaction_id TEXT");
                    logger.info("Migrated: Added custom_transaction_id column.");
                } catch (SQLException e) {
                    // Likely already exists, verify message if needed, but ignore
                    if (!e.getMessage().contains("duplicate column")) {
                        // System.err.println("Migration note: " + e.getMessage());
                    }
                }

                // MIGRATION: Add parked_transactions table
                try {
                    statement.execute(
                            "CREATE TABLE IF NOT EXISTS parked_transactions (parked_id INTEGER PRIMARY KEY AUTOINCREMENT, parked_date DATETIME DEFAULT CURRENT_TIMESTAMP, user_id INTEGER, debtor_id INTEGER, items_json TEXT, reference_note TEXT)");
                    // ALTER for existing
                    try {
                        statement.execute("ALTER TABLE parked_transactions ADD COLUMN reference_note TEXT");
                        logger.info("Migrated: Added reference_note to parked_transactions.");
                    } catch (SQLException e) {
                        if (!e.getMessage().contains("duplicate"))
                            e.printStackTrace();
                    }

                    System.out.println("Migrated: Checked/Created parked_transactions table.");
                } catch (SQLException e) {
                    logger.error("Error creating parked_transactions table", e);
                }

                // MIGRATION: Add layby tables
                try {
                    statement.execute(
                            "CREATE TABLE IF NOT EXISTS laybys (layby_id INTEGER PRIMARY KEY AUTOINCREMENT, custom_layby_id TEXT, customer_name TEXT NOT NULL, customer_phone TEXT NOT NULL, customer_address TEXT, total_amount DECIMAL(10,2) NOT NULL, amount_paid DECIMAL(10,2) DEFAULT 0.00, duration_months INTEGER NOT NULL, start_date DATETIME DEFAULT CURRENT_TIMESTAMP, expiry_date DATETIME, status TEXT DEFAULT 'ACTIVE', user_id INTEGER)");
                    statement.execute(
                            "CREATE TABLE IF NOT EXISTS layby_items (id INTEGER PRIMARY KEY AUTOINCREMENT, layby_id INTEGER NOT NULL, product_id INTEGER NOT NULL, quantity DECIMAL(10,2) NOT NULL, unit_price DECIMAL(10,2) NOT NULL, line_total DECIMAL(10,2) NOT NULL)");
                    statement.execute(
                            "CREATE TABLE IF NOT EXISTS layby_payments (payment_id INTEGER PRIMARY KEY AUTOINCREMENT, layby_id INTEGER NOT NULL, payment_date DATETIME DEFAULT CURRENT_TIMESTAMP, amount DECIMAL(10,2) NOT NULL, payment_method TEXT, user_id INTEGER)");
                    logger.info("Migrated: Checked/Created layby tables.");
                } catch (SQLException e) {
                    logger.error("Error creating layby tables", e);
                }

                // MIGRATION: Suppliers & Purchases
                try {
                    statement.execute(
                            "CREATE TABLE IF NOT EXISTS suppliers (supplier_id INTEGER PRIMARY KEY AUTOINCREMENT, company_name TEXT NOT NULL, contact_person TEXT, email TEXT, phone TEXT, address TEXT, current_balance DECIMAL(10,2) DEFAULT 0.00)");
                    statement.execute(
                            "CREATE TABLE IF NOT EXISTS purchases (purchase_id INTEGER PRIMARY KEY AUTOINCREMENT, supplier_id INTEGER, purchase_date DATETIME DEFAULT CURRENT_TIMESTAMP, total_cost DECIMAL(10,2), invoice_number TEXT, status TEXT, FOREIGN KEY(supplier_id) REFERENCES suppliers(supplier_id))");
                    statement.execute(
                            "CREATE TABLE IF NOT EXISTS purchase_items (item_id INTEGER PRIMARY KEY AUTOINCREMENT, purchase_id INTEGER, product_id INTEGER, quantity DECIMAL(10,2), unit_cost DECIMAL(10,2), FOREIGN KEY(purchase_id) REFERENCES purchases(purchase_id), FOREIGN KEY(product_id) REFERENCES products(product_id))");
                    statement.execute(
                            "CREATE TABLE IF NOT EXISTS supplier_payments (payment_id INTEGER PRIMARY KEY AUTOINCREMENT, supplier_id INTEGER, payment_date DATETIME DEFAULT CURRENT_TIMESTAMP, amount DECIMAL(10,2), payment_method TEXT, FOREIGN KEY(supplier_id) REFERENCES suppliers(supplier_id))");
                    System.out.println("Migrated: Checked/Created Supplier tables.");

                    // FIX: Ensure current_balance exists in suppliers (for older DBs)
                    try {
                        statement
                                .execute("ALTER TABLE suppliers ADD COLUMN current_balance DECIMAL(10,2) DEFAULT 0.00");
                        logger.info("Migrated: Added current_balance to suppliers.");
                    } catch (SQLException e) {
                        // Ignore if column already exists
                        if (!e.getMessage().contains("duplicate column")) {
                            // e.printStackTrace();
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                // MIGRATION: Add settings table
                try {
                    statement.execute(
                            "CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL, description TEXT, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
                    statement.execute(
                            "INSERT OR IGNORE INTO settings (key, value, description) VALUES ('vat_rate', '15.00', 'Standard VAT Rate'), ('company_name', 'Malek Enterprise', 'Company Name'), ('layby_min_deposit_pct', '20.00', 'Minimum Layby Deposit %')");
                    logger.info("Migrated: Checked/Created settings table.");
                } catch (SQLException e) {
                    logger.error("Error creating settings table", e);
                }

                // MIGRATION: Add product_code to products
                try {
                    statement.execute("ALTER TABLE products ADD COLUMN product_code TEXT");
                    logger.info("Migrated: Added product_code to products.");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("duplicate column")) {
                        // ignore
                    }
                }

                // MIGRATION: Add custom_description to transaction_items
                try {
                    statement.execute("ALTER TABLE transaction_items ADD COLUMN custom_description TEXT");
                    logger.info("Migrated: Added custom_description to transaction_items.");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("duplicate column")) {
                        // ignore
                    }
                }

                // MIGRATION: Add cost_price_at_sale to transaction_items (Task 1: Data
                // Integrity)
                try {
                    statement.execute(
                            "ALTER TABLE transaction_items ADD COLUMN cost_price_at_sale DECIMAL(10,2) DEFAULT 0.00");
                    logger.info("Migrated: Added cost_price_at_sale to transaction_items.");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("duplicate column")) {
                        // ignore
                    }
                }

                // MIGRATION: Add tender_bank to transactions
                try {
                    statement.execute("ALTER TABLE transactions ADD COLUMN tender_bank DECIMAL(10,2) DEFAULT 0");
                    logger.info("Migrated: Added tender_bank to transactions.");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("duplicate column")) {
                        // ignore
                    }
                }

                // MIGRATION: Insert General Item Product (0000)
                try {
                    // Check if exists using product_code
                    java.sql.ResultSet rs = statement
                            .executeQuery("SELECT count(*) FROM products WHERE product_code = '0000'");
                    if (rs.next() && rs.getInt(1) == 0) {
                        statement.execute(
                                "INSERT INTO products (description, price_retail, stock_on_hand, category, tax_rate, is_service_item, product_code) "
                                        +
                                        "VALUES ('General Item', 0.00, 0, 'General', 15.00, 1, '0000')");
                        logger.info("Migrated: Inserted General Item (0000).");

                        // Link barcode
                        try (java.sql.ResultSet rsId = statement
                                .executeQuery("SELECT product_id FROM products WHERE product_code = '0000'")) {
                            if (rsId.next()) {
                                int pid = rsId.getInt(1);
                                statement.execute("INSERT OR IGNORE INTO barcodes (product_id, barcode) VALUES (" + pid
                                        + ", '0000')");
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.error("Error inserting General Item", e);
                }

                // MIGRATION: Performance Indexes (Phase 3)
                try {
                    statement.execute(
                            "CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(transaction_date)");
                    statement.execute(
                            "CREATE INDEX IF NOT EXISTS idx_transactions_type ON transactions(transaction_type)");
                    statement.execute(
                            "CREATE INDEX IF NOT EXISTS idx_transaction_items_pid ON transaction_items(product_id)");
                    logger.info("Migrated: Checked/Created Performance Indexes.");
                } catch (SQLException e) {
                    logger.error("Error creating indexes", e);
                }

                ensureSystemProducts(statement);

                logger.info("Database initialized successfully.");
            }
        } catch (Exception e) {
            logger.error("Error initializing database", e);
        }
    }

    private void ensureSystemProducts(Statement statement) {
        // Ensure General Item (0000) exists
        try {
            boolean exists = false;
            try (java.sql.ResultSet rs = statement.executeQuery(
                    "SELECT count(*) FROM products WHERE product_code = '0000'")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    exists = true;
                }
            }

            if (!exists) {
                // Determine Category (Default to 'General')
                String catName = "General";

                // Insert without barcode column
                String sql = "INSERT INTO products (description, price_retail, price_trade, cost_price, stock_on_hand, category, tax_rate, is_service_item, product_code) "
                        +
                        "VALUES ('General Item', 0.00, 0.00, 0.00, 0, '" + catName + "', 15.00, 1, '0000')";
                statement.execute(sql);
                logger.info("System Product: Created General Item (0000).");

                // Ensure barcode table entry also exists
                try (java.sql.ResultSet rsId = statement
                        .executeQuery("SELECT product_id FROM products WHERE product_code = '0000'")) {
                    if (rsId.next()) {
                        int pid = rsId.getInt(1);
                        statement.execute("INSERT OR IGNORE INTO barcodes (product_id, barcode) VALUES (" + pid
                                + ", '0000')");
                    }
                }

            } else {
                // Verify Barcode Table linkage even if product exists
                try (java.sql.ResultSet rsId = statement
                        .executeQuery("SELECT product_id FROM products WHERE product_code = '0000'")) {
                    if (rsId.next()) {
                        int pid = rsId.getInt(1);
                        // Ensure it has a barcode entry
                        try (java.sql.ResultSet rsB = statement
                                .executeQuery("SELECT count(*) FROM barcodes WHERE barcode = '0000'")) {
                            if (rsB.next() && rsB.getInt(1) == 0) {
                                statement.execute(
                                        "INSERT INTO barcodes (product_id, barcode) VALUES (" + pid + ", '0000')");
                                logger.info("System Product: Linked 0000 barcode.");
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error ensuring system products", e);
        }
    }

    public boolean restoreBackup(java.io.File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) {
            logger.error("Restore failed: Invalid source file.");
            return false;
        }

        try {
            // 1. Force Close Connection Pool
            closeConnection();

            // 2. Create Safety Backup of current DB
            java.io.File currentDb = new java.io.File("pos_enterprise.db");
            if (currentDb.exists()) {
                String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
                java.io.File safetyBackup = new java.io.File("pos_enterprise_SAFETY_" + timestamp + ".db");
                java.nio.file.Files.copy(currentDb.toPath(), safetyBackup.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                logger.info("Safety backup created: {}", safetyBackup.getAbsolutePath());
            }

            // 3. Overwrite current DB with source backup
            java.nio.file.Files.copy(sourceFile.toPath(), currentDb.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            logger.info("Database restored from: {}", sourceFile.getAbsolutePath());

            // 4. Re-Initialize Connection Pool ONLY
            // NOTE: Do NOT call initializeDatabase() because the backup is already a
            // complete,
            // initialized database. Running schema.sql would cause errors on an existing
            // DB.
            reconnect();

            return true;
        } catch (Exception e) {
            logger.error("Restore failed", e);
            // Try to reconnect if failed
            try {
                reconnect();
            } catch (SQLException ex) {
                logger.error("Failed to reconnect after restore failure", ex);
            }
            return false;
        }
    }
}
