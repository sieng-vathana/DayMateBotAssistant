package com.daymate.bot.domain;

import java.util.Locale;
import java.util.Optional;

public enum TaskCategory {
    WORK("Work"),
    PERSONAL("Personal"),
    HEALTH("Health");

    private final String label;

    TaskCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<TaskCategory> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        int colon = value.lastIndexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return Optional.of(TaskCategory.valueOf(normalized));
        } catch (IllegalArgumentException ignored) {
            for (TaskCategory category : values()) {
                if (category.label.equalsIgnoreCase(value.trim())) {
                    return Optional.of(category);
                }
            }
            return Optional.empty();
        }
    }
}
