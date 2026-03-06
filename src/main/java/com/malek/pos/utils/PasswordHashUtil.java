package com.malek.pos.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility for secure password hashing and verification.
 * Uses BCrypt algorithm with salt for production-grade security.
 */
public class PasswordHashUtil {

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12); // 12 rounds for security

    /**
     * Hash a plain text password.
     * 
     * @param plainPassword The plain text password
     * @return Hashed password string
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return encoder.encode(plainPassword);
    }

    /**
     * Verify a plain password against a hashed password.
     * 
     * @param plainPassword  The plain text password to verify
     * @param hashedPassword The hashed password to check against
     * @return true if password matches, false otherwise
     */
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        return encoder.matches(plainPassword, hashedPassword);
    }

    /**
     * Check if a password looks like it's already hashed (starts with BCrypt
     * prefix).
     * 
     * @param password The password to check
     * @return true if appears to be hashed, false if plaintext
     */
    public static boolean isHashed(String password) {
        return password != null && password.startsWith("$2a$") || password.startsWith("$2b$");
    }
}
