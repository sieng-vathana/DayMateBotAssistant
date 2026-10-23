package com.daymate.bot.ot;

import com.daymate.bot.domain.OtShift;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

public final class OtCalculator {
    public static final BigDecimal HOURS_PER_MONTH = new BigDecimal("208");
    public static final BigDecimal BREAK_AFTER_HOURS = new BigDecimal("6.00");
    public static final BigDecimal BREAK_HOURS = new BigDecimal("1.00");

    private OtCalculator() {
    }

    public static Quote quote(BigDecimal monthlySalary, LocalDate date, LocalTime start, LocalTime end) {
        if (monthlySalary == null || monthlySalary.signum() <= 0) {
            throw new IllegalArgumentException("Base salary must be greater than 0.");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Finish time must be after the start time on the same day.");
        }
        BigDecimal gross = BigDecimal.valueOf(Duration.between(start, end).toMinutes())
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        BigDecimal breakHours = gross.compareTo(BREAK_AFTER_HOURS) >= 0 ? BREAK_HOURS : BigDecimal.ZERO.setScale(2);
        BigDecimal paid = gross.subtract(breakHours).setScale(2, RoundingMode.HALF_UP);
        if (paid.signum() <= 0) {
            throw new IllegalArgumentException("That range does not leave any paid hours.");
        }
        OtShift shift = OtShift.forDate(date);
        BigDecimal hourly = monthlySalary.divide(HOURS_PER_MONTH, 10, RoundingMode.HALF_UP);
        BigDecimal rate = hourly.multiply(shift.multiplier()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = paid.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        return new Quote(shift, gross, breakHours, paid, rate, amount);
    }

    public record Quote(OtShift shift, BigDecimal grossHours, BigDecimal breakHours, BigDecimal paidHours,
                        BigDecimal hourlyRate, BigDecimal amount) {
    }
}
