package com.malek.pos.utils;

import com.malek.pos.database.ConfigManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class LicenseManager {

    private static final String SECRET_SALT = "M4l3k_En73rpR1s3_P0S_S3cr3t!";
    private static final String LICENSE_KEY_NAME = "sub_key";

    /**
     * Validates the currently stored license key.
     * 
     * @return true if valid and not expired, false otherwise.
     */
    public static boolean isLicenseValid() {
        String storedKey = ConfigManager.getInstance().getString(LICENSE_KEY_NAME, "");
        if (storedKey == null || storedKey.isEmpty()) {
            return false;
        }
        return validateKey(storedKey);
    }

    /**
     * Validates a provided license key string.
     * Expected format: Base64( "YYYY-MM-DD:HASH" )
     * where HASH = SHA-256("YYYY-MM-DD" + SECRET_SALT)
     * 
     * @param encodedKey the base64 encoded license key
     * @return true if valid and not expired, false otherwise.
     */
    public static boolean validateKey(String encodedKey) {
        try {
            String decodedString = new String(Base64.getDecoder().decode(encodedKey), StandardCharsets.UTF_8);
            String[] parts = decodedString.split(":");
            if (parts.length != 2) {
                return false;
            }

            String dateStr = parts[0];
            String providedHash = parts[1];

            // 1. Validate Hash Signature
            String computedHash = generateHash(dateStr);
            if (!computedHash.equals(providedHash)) {
                return false; // Signature mismatch
            }

            // 2. Validate Expiry Date
            LocalDate expiryDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            if (LocalDate.now().isAfter(expiryDate)) {
                return false; // Expired
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Saves a valid license key to the configuration.
     * 
     * @param encodedKey the base64 encoded license key
     */
    public static void saveLicenseKey(String encodedKey) {
        ConfigManager.getInstance().updateSetting(LICENSE_KEY_NAME, encodedKey);
    }

    /**
     * Helper to generate a SHA-256 hash for the date string.
     */
    private static String generateHash(String dateStr) {
        try {
            String data = dateStr + SECRET_SALT;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedhash);
        } catch (Exception e) {
            throw new RuntimeException("Error generating hash", e);
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Development helper: Generates a valid license key for a given date.
     * 
     * @param expiryDate The expiry date string (YYYY-MM-DD)
     * @return The base64 encoded license key
     */
    public static String generateKeyForTesting(String expiryDate) {
        String hash = generateHash(expiryDate);
        String raw = expiryDate + ":" + hash;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
