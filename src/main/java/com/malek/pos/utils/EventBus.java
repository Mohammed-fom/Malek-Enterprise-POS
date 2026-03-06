package com.malek.pos.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EventBus {

    // Topic Constants
    public static final String REFRESH_STOCK = "REFRESH_STOCK";
    public static final String REFRESH_HISTORY = "REFRESH_HISTORY";
    public static final String REFRESH_SUPPLIERS = "REFRESH_SUPPLIERS";
    public static final String REFRESH_DEBTORS = "REFRESH_DEBTORS";
    public static final String REFRESH_REPORTS = "REFRESH_REPORTS";

    private static final Map<String, List<Consumer<Object>>> subscribers = new ConcurrentHashMap<>();

    // Subscribe to an event topic
    public static void subscribe(String topic, Consumer<Object> listener) {
        subscribers.computeIfAbsent(topic, k -> new ArrayList<>()).add(listener);
    }

    // Publish an event to a topic
    public static void publish(String topic, Object payload) {
        List<Consumer<Object>> listeners = subscribers.get(topic);
        if (listeners != null) {
            // Execute on JavaFX Thread to be safe for UI updates
            javafx.application.Platform.runLater(() -> {
                for (Consumer<Object> listener : listeners) {
                    try {
                        listener.accept(payload);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    // Publish without payload
    public static void publish(String topic) {
        publish(topic, null);
    }
}
