package com.daymate.bot.domain;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

public enum OtShift {
    HOLIDAY("Holiday", new BigDecimal("1")),
    EVENING("Evening", new BigDecimal("1.5")),
    WEEKEND("Weekend", new BigDecimal("2"));

    private final String label;
    private final BigDecimal multiplier;

    OtShift(String label, BigDecimal multiplier) {
        this.label = label;
        this.multiplier = multiplier;
    }

    public String label() {
        return label;
    }

    public BigDecimal multiplier() {
        return multiplier;
    }

    public int percent() {
        return multiplier.multiply(BigDecimal.valueOf(100)).intValue();
    }

    public static OtShift forDate(LocalDate date, int workDaysPerWeek) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SUNDAY) {
            return WEEKEND;
        }
        if (day == DayOfWeek.SATURDAY && workDaysPerWeek <= 5) {
            return WEEKEND;
        }
        return EVENING;
    }

    public static java.util.Optional<OtShift> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.Optional.empty();
        }
        String value = raw.trim().toUpperCase(java.util.Locale.ROOT);
        int colon = value.lastIndexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        if (value.startsWith("100") || value.equals("HOLIDAY")) {
            return java.util.Optional.of(HOLIDAY);
        }
        if (value.startsWith("150") || value.equals("EVENING")) {
            return java.util.Optional.of(EVENING);
        }
        if (value.startsWith("200") || value.equals("WEEKEND")) {
            return java.util.Optional.of(WEEKEND);
        }
        try {
            return java.util.Optional.of(OtShift.valueOf(value));
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }
}
