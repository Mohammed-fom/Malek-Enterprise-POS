package com.malek.pos.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logger factory for the POS application.
 * Provides consistent logging across all components.
 */
public class LoggerManager {

    /**
     * Get a logger for the specified class.
     * 
     * @param clazz The class requesting the logger
     * @return Logger instance
     */
    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }

    /**
     * Get a logger with the specified name.
     * 
     * @param name The logger name
     * @return Logger instance
     */
    public static Logger getLogger(String name) {
        return LoggerFactory.getLogger(name);
    }
}
