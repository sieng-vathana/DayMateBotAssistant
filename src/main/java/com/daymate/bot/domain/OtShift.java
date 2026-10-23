package com.daymate.bot.domain;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

public enum OtShift {
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
}
