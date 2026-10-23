package com.daymate.bot.domain;

import java.util.Locale;
import java.util.Optional;

public enum TaskPriority {
    HIGH("High", "🔴"),
    MEDIUM("Medium", "🟡"),
    LOW("Low", "🟢");

    private final String label;
    private final String emoji;

    TaskPriority(String label, String emoji) {
        this.label = label;
        this.emoji = emoji;
    }

    public String label() {
        return label;
    }

    public String emoji() {
        return emoji;
    }

    public static Optional<TaskPriority> parse(String raw) {
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
            return Optional.of(TaskPriority.valueOf(normalized));
        } catch (IllegalArgumentException ignored) {
            for (TaskPriority priority : values()) {
                if (priority.label.equalsIgnoreCase(value.trim())) {
                    return Optional.of(priority);
                }
            }
            return Optional.empty();
        }
    }
}
