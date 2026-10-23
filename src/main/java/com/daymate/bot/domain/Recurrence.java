package com.daymate.bot.domain;

import java.util.Locale;
import java.util.Optional;

public enum Recurrence {
    NONE("None"),
    DAILY("Daily"),
    WEEKLY("Weekly");

    private final String label;

    Recurrence(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<Recurrence> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        int colon = value.lastIndexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("NONE") || normalized.equals("NO") || normalized.equals("SKIP")) {
            return Optional.of(NONE);
        }
        try {
            return Optional.of(Recurrence.valueOf(normalized));
        } catch (IllegalArgumentException ignored) {
            for (Recurrence recurrence : values()) {
                if (recurrence.label.equalsIgnoreCase(value.trim())) {
                    return Optional.of(recurrence);
                }
            }
            return Optional.empty();
        }
    }
}
