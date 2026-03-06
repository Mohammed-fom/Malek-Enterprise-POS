-- ==========================================
-- Enterprise POS Database Schema
-- Compatible with SQLite (and MySQL with minor adjustments)
-- ==========================================

-- 1. AUTHENTICATION & USERS
CREATE TABLE IF NOT EXISTS roles (
    role_id INTEGER PRIMARY KEY AUTOINCREMENT,
    role_name TEXT NOT NULL UNIQUE -- e.g., 'ADMIN', 'MANAGER', 'CASHIER'
);

CREATE TABLE IF NOT EXISTS users (
    user_id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    full_name TEXT,
    role_id INTEGER,
    pin_code TEXT, -- 4-digit PIN for quick overrides
    is_active BOOLEAN DEFAULT 1,
    FOREIGN KEY(role_id) REFERENCES roles(role_id)
);

-- 2. INVENTORY & SUPPLIERS
CREATE TABLE IF NOT EXISTS suppliers (
    supplier_id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_name TEXT NOT NULL,
    contact_person TEXT,
    phone TEXT,
    email TEXT,
    tax_number TEXT,
    address TEXT
);

CREATE TABLE IF NOT EXISTS products (
    product_id INTEGER PRIMARY KEY AUTOINCREMENT,
    description TEXT NOT NULL,
    category TEXT,
    
    -- Pricing Tiers
    cost_price DECIMAL(10,2) DEFAULT 0.00,
    price_retail DECIMAL(10,2) DEFAULT 0.00,
    price_trade DECIMAL(10,2) DEFAULT 0.00, -- Tier 2
    
    tax_rate DECIMAL(5,2) DEFAULT 15.00, -- e.g., 15% VAT
    
    -- Stock Control
    stock_on_hand DECIMAL(10,2) DEFAULT 0.00,
    low_stock_threshold DECIMAL(10,2) DEFAULT 5.00,
    is_service_item BOOLEAN DEFAULT 0, -- If true, no stock tracking
    
    supplier_id INTEGER,
    FOREIGN KEY(supplier_id) REFERENCES suppliers(supplier_id)
);

CREATE TABLE IF NOT EXISTS barcodes (
    barcode_id INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id INTEGER NOT NULL,
    barcode TEXT NOT NULL UNIQUE,
    FOREIGN KEY(product_id) REFERENCES products(product_id) ON DELETE CASCADE
);

-- 3. DEBTORS (CREDIT CUSTOMERS)
CREATE TABLE IF NOT EXISTS debtors (
    debtor_id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_no TEXT NOT NULL UNIQUE, -- Custom Account Number
    customer_name TEXT NOT NULL,
    phone TEXT,
    email TEXT,
    address TEXT,
    
    -- Credit Control
    credit_limit DECIMAL(10,2) DEFAULT 0.00,
    current_balance DECIMAL(10,2) DEFAULT 0.00,
    price_tier TEXT DEFAULT 'RETAIL' -- 'RETAIL' or 'TRADE'
);

-- 4. TRANSACTIONS (SALES & QUOTES)
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id INTEGER PRIMARY KEY AUTOINCREMENT,
    custom_transaction_id TEXT, -- e.g., SALEddmmyy01
    transaction_type TEXT NOT NULL, -- 'SALE', 'QUOTE', 'REFUND'
    transaction_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    user_id INTEGER, -- Cashier
    debtor_id INTEGER, -- NULL for Cash Sale
    
    -- Financials
    subtotal DECIMAL(10,2) DEFAULT 0.00,
    tax_total DECIMAL(10,2) DEFAULT 0.00,
    discount_total DECIMAL(10,2) DEFAULT 0.00,
    grand_total DECIMAL(10,2) DEFAULT 0.00,
    
    -- Payments
    tender_cash DECIMAL(10,2) DEFAULT 0.00,
    tender_card DECIMAL(10,2) DEFAULT 0.00,
    tender_account DECIMAL(10,2) DEFAULT 0.00,
    change_due DECIMAL(10,2) DEFAULT 0.00,
    
    status TEXT DEFAULT 'COMPLETED', -- 'COMPLETED', 'VOID', 'SAVED' (for quotes)
    shift_id INTEGER,
    
    FOREIGN KEY(user_id) REFERENCES users(user_id),
    FOREIGN KEY(debtor_id) REFERENCES debtors(debtor_id)
);

CREATE TABLE IF NOT EXISTS transaction_items (
    item_id INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    
    quantity DECIMAL(10,2) NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL, -- Price at moment of sale
    discount_percent DECIMAL(5,2) DEFAULT 0.00,
    tax_percent DECIMAL(5,2) DEFAULT 0.00,
    line_total DECIMAL(10,2) NOT NULL,
    
    cost_price_at_sale DECIMAL(10,2), -- For profit reporting
    
    FOREIGN KEY(transaction_id) REFERENCES transactions(transaction_id),
    FOREIGN KEY(product_id) REFERENCES products(product_id)
);

-- 5. SHIFT MANAGEMENT
CREATE TABLE IF NOT EXISTS shifts (
    shift_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    start_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    end_time DATETIME,
    
    opening_float DECIMAL(10,2) DEFAULT 0.00,
    
    -- Cash Up Totals
    declared_cash DECIMAL(10,2) DEFAULT 0.00,
    declared_card DECIMAL(10,2) DEFAULT 0.00,
    system_cash DECIMAL(10,2) DEFAULT 0.00,
    system_card DECIMAL(10,2) DEFAULT 0.00,
    
    status TEXT DEFAULT 'OPEN', -- 'OPEN', 'CLOSED'
    FOREIGN KEY(user_id) REFERENCES users(user_id)
);

-- 6. AUDIT LOG
CREATE TABLE IF NOT EXISTS audit_logs (
    log_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER,
    action_type TEXT NOT NULL, -- 'DELETE_ITEM', 'PRICE_CHANGE', 'REFUND', 'OVERRIDE'
    description TEXTR,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    supervisor_id INTEGER, -- If override was needed
    FOREIGN KEY(user_id) REFERENCES users(user_id)
);

-- 7. PARKED TRANSACTIONS
CREATE TABLE IF NOT EXISTS parked_transactions (
    parked_id INTEGER PRIMARY KEY AUTOINCREMENT,
    parked_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    user_id INTEGER,
    debtor_id INTEGER,
    items_json TEXT, -- Storing items as JSON for simplicity
    reference_note TEXT -- Customer Name or Note
);

-- 8. LAYBYS
CREATE TABLE IF NOT EXISTS laybys (
    layby_id INTEGER PRIMARY KEY AUTOINCREMENT,
    custom_layby_id TEXT, -- LAYddmmyy001
    customer_name TEXT NOT NULL,
    customer_phone TEXT NOT NULL,
    customer_address TEXT,
    
    total_amount DECIMAL(10,2) NOT NULL,
    amount_paid DECIMAL(10,2) DEFAULT 0.00,
    duration_months INTEGER NOT NULL, -- 3, 4, 5, 6
    
    start_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    expiry_date DATETIME,
    
    status TEXT DEFAULT 'ACTIVE', -- ACTIVE, COMPLETED, CANCELLED, COLLECTED
    user_id INTEGER,
    FOREIGN KEY(user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS layby_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    layby_id INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    quantity DECIMAL(10,2) NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    line_total DECIMAL(10,2) NOT NULL,
    FOREIGN KEY(layby_id) REFERENCES laybys(layby_id),
    FOREIGN KEY(product_id) REFERENCES products(product_id)
);

CREATE TABLE IF NOT EXISTS layby_payments (
    payment_id INTEGER PRIMARY KEY AUTOINCREMENT,
    layby_id INTEGER NOT NULL,
    payment_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    amount DECIMAL(10,2) NOT NULL,
    payment_method TEXT, -- CASH, CARD
    user_id INTEGER,
    FOREIGN KEY(layby_id) REFERENCES laybys(layby_id)
);

-- 9. SYSTEM CONFIGURATION
CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    description TEXT,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 10. SUPPLIERS & PURCHASING
CREATE TABLE IF NOT EXISTS suppliers (
    supplier_id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_name TEXT NOT NULL,
    contact_person TEXT,
    email TEXT,
    phone TEXT,
    address TEXT,
    current_balance DECIMAL(10,2) DEFAULT 0.00
);

CREATE TABLE IF NOT EXISTS purchases (
    purchase_id INTEGER PRIMARY KEY AUTOINCREMENT,
    supplier_id INTEGER,
    purchase_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    total_cost DECIMAL(10,2),
    invoice_number TEXT,
    status TEXT, -- PENDING, RECEIVED
    FOREIGN KEY(supplier_id) REFERENCES suppliers(supplier_id)
);

CREATE TABLE IF NOT EXISTS purchase_items (
    item_id INTEGER PRIMARY KEY AUTOINCREMENT,
    purchase_id INTEGER,
    product_id INTEGER,
    quantity DECIMAL(10,2),
    unit_cost DECIMAL(10,2),
    FOREIGN KEY(purchase_id) REFERENCES purchases(purchase_id),
    FOREIGN KEY(product_id) REFERENCES products(product_id)
);

CREATE TABLE IF NOT EXISTS supplier_payments (
    payment_id INTEGER PRIMARY KEY AUTOINCREMENT,
    supplier_id INTEGER,
    payment_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    amount DECIMAL(10,2),
    payment_method TEXT,
    FOREIGN KEY(supplier_id) REFERENCES suppliers(supplier_id)
);

-- Initial Config Seeding
INSERT OR IGNORE INTO settings (key, value, description) VALUES 
('vat_rate', '15.00', 'Standard VAT Rate in %'),
('company_name', 'Malek Enterprise', 'Company Name for Receipts'),
('layby_min_deposit_pct', '20.00', 'Minimum Layby Deposit %'),
('currency_symbol', 'R', 'Currency Symbol'),
('receipt_footer', 'Thank you for your support!', 'Footer text for receipts');

-- Initial Data Seeding
INSERT OR IGNORE INTO roles (role_name) VALUES ('ADMIN'), ('CASHIER');
INSERT OR IGNORE INTO users (username, password_hash, full_name, role_id, pin_code) 
VALUES ('admin', '$2a$10$vcYuDHifSi2EWAliVaZ7DTPBf7z8bHmQbdQbzq9V', 'Developer Admin', 1, '1234');

-- 11. FINANCIALS & EXPENSES
CREATE TABLE IF NOT EXISTS expenses (
    expense_id INTEGER PRIMARY KEY AUTOINCREMENT,
    description TEXT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    category TEXT, -- 'PETTY_CASH', 'UTILITIES', 'SUPPLIES'
    expense_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    user_id INTEGER,
    FOREIGN KEY(user_id) REFERENCES users(user_id)
);

-- 12. INVENTORY LOGS (Wastage / Shrinkage)
CREATE TABLE IF NOT EXISTS inventory_logs (
    log_id INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id INTEGER NOT NULL,
    quantity_change DECIMAL(10,2), -- Negative for wastage
    reason TEXT, -- 'WASTAGE', 'DAMAGED', 'THEFT', 'EXPIRED'
    log_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    user_id INTEGER,
    FOREIGN KEY(user_id) REFERENCES users(user_id)
);

-- 13. PERFORMANCE INDEXES
CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(transaction_date);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON transactions(transaction_type);
CREATE INDEX IF NOT EXISTS idx_transaction_items_pid ON transaction_items(product_id);
