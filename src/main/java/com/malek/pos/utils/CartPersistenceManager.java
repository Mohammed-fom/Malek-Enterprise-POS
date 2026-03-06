package com.malek.pos.utils;

import com.malek.pos.database.ProductDAO;
import com.malek.pos.models.Product;
import com.malek.pos.models.TransactionItem;
import java.io.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CartPersistenceManager {
    private static final String CACHE_FILE = "cache/cart.dat";

    public static void saveCart(List<TransactionItem> items) {
        try {
            File file = new File(CACHE_FILE);
            if (!file.getParentFile().exists())
                file.getParentFile().mkdirs();

            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                for (TransactionItem item : items) {
                    // Format: productId|quantity
                    writer.println(item.getProductId() + "|" + item.getQuantity());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<TransactionItem> loadCart() {
        List<TransactionItem> items = new ArrayList<>();
        File file = new File(CACHE_FILE);
        if (!file.exists())
            return items;

        ProductDAO productDAO = new ProductDAO();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                String[] parts = line.split("\\|");
                if (parts.length >= 2) {
                    try {
                        int pid = Integer.parseInt(parts[0]);
                        BigDecimal qty = new BigDecimal(parts[1]);

                        Product p = productDAO.getProductById(pid);
                        if (p != null) {
                            // Reconstruct item
                            TransactionItem item = new TransactionItem(p, qty, false);
                            items.add(item);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return items;
    }

    public static void clearCart() {
        try {
            File file = new File(CACHE_FILE);
            if (file.exists())
                file.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
