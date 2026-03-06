# Malek Enterprise POS

A comprehensive, JavaFX-based Point of Sale (POS) system designed for retail environments. This application provides robust features for sales processing, inventory management, customer tracking, and financial reporting.

## 🚀 Key Features

### 🛒 Sales & Transactions
* **Barcode Scanning:** Rapidly add items to the cart using barcode scanners.
* **Transaction Management:** Handle standard sales, product returns, and refunds.
* **Quotations:** Create, save, and print customer quotes. Convert quotes back into active sales.
* **Laybys / Layaways:** Manage layby agreements, track deposits, partial payments, and expirations.
* **Suspend/Resume:** Park an active sale and resume it later without losing the cart data.
* **Discounts & Overrides:** Apply line-item or global discounts (requires Manager/Admin approval for specific limits).

### 📦 Inventory & Stock Control
* **Product Master:** Add, edit, and delete products. Track cost prices, selling prices, and stock levels.
* **Barcode Labels:** Generate and print barcode labels for products.
* **Supplier Management:** Track suppliers and link them to inventory.

### 👥 Customer & Debtor Management
* **Account Tracking:** Maintain a database of customers and trade accounts.
* **Account Sales:** Assign sales to specific debtors for tracking terms and balances.

### 🔐 Security & Access Control
* **Role-Based Access Control (RBAC):** Restrict system features based on user roles (Admin, Manager, Cashier).
* **Manager Overrides:** Require supervisor pins/passwords for sensitive actions like Voiding sales or high-value refunds.
* **Subscription Licensing System:** Validates software activation keys on startup using cryptographic hashing to support multi-client deployment.

### 💰 Financials & Shifts
* **Shift Management:** Open and close shifts, track cash drawer floats, and reconcile expected vs. actual totals.
* **Z-Reads:** Generate and print End-of-Day Z-Read reports.
* **Expense Tracking:** Record daily operational expenses (petty cash, utilities, etc.) directly from the POS.

### 🖨️ Hardware Integration
* **Receipt Printing:** Supports multiple receipt formats (Thermal 80mm, A4, A5).
* **Cash Drawer Kicks:** Automatically triggers cash drawer opening for cash transactions.

### 🛠️ System Maintenance
* **Database Backups:** One-click SQLite database backups to a chosen directory.
* **Database Restoration:** Easily restore previous data backups.

---

## 🛠️ Installation & Setup

### Prerequisites
* **Java:** JDK 21 or higher.
* **Maven:** For building and dependency management.

### Running the Application Structure
This project uses Maven. You can run the application directly from the source code.

1. **Clone or Download the Repository**
2. **Compile the Code**
   Open your terminal in the project directory and run:
   ```bash
   mvn clean compile
   ```
3. **Run the Application**
   ```bash
   mvn javafx:run
   ```
   *Alternatively, if running the compiled jar or exec:*
   ```bash
   mvn exec:java -Dexec.mainClass="com.malek.pos.Launcher"
   ```

### 🔑 Activation & Licensing
Upon first launch, the software will prompt for a **Subscription Key**. 

To generate a key for testing or a new client:
1. Run the `GenerateLicense.java` utility script provided in the root directory.
2. Provide the newly generated Base64 license string to the activation prompt.

---

## 💻 How to Use

### Logging In
Use your assigned Username and Password. Alternatively, use your numeric PIN code if configured by your administrator. 

### The Sales Screen (Main Dashboard)
The primary screen is optimized for keyboard use.

**Keyboard Shortcuts:**
* `F1` / `F10` - Checkout / Tender Sale
* `F2` - Open Debtors Screen
* `F3` - Open Stock Screen
* `F4` - Open Shift Manager
* `F5` - Save Cart as Quote
* `F6` - Recall a Quote
* `F7` - Toggle Refund Mode
* `F8` - Void Current Transaction
* `F9` - Transaction History
* `F11` - Suspend/Park Sale
* `F12` - Resume Parked Sale
* `CTRL + R` - Process a Matched Refund
* `CTRL + F` - Universal Search
* `+` / `-` - Adjust Quantity of Selected Item
* `*` - Set exact Quantity for Selected Item
* `DELETE` - Remove Selected Item from Cart
* `ESC` - Clear the current transaction

### Admin Control Center
Accessed via the "Admin" button on the sales screen (Requires **Manager** or **Admin** privileges).
* **User Management (Admin Only):** Create cashiers, reset passwords, and assign roles.
* **Financials:** Record daily expenses and adjust system-wide Tax (VAT) rate.
* **Maintenance:** Execute manual database backups or restore from previous points.
