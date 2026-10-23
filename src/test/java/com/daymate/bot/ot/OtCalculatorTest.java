package com.daymate.bot.ot;

import com.daymate.bot.domain.OtShift;
import com.daymate.bot.domain.OtTimeRange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class OtCalculatorTest {
    private static final BigDecimal SALARY = new BigDecimal("480.00");

    @Test
    void fiveDayWeekUsesTwentyTwoDaysAndTreatsSaturdayAsWeekend() {
        OtCalculator.Quote friday = OtCalculator.quote(
                SALARY, LocalDate.of(2026, 10, 23), LocalTime.of(16, 30), LocalTime.of(17, 30), 5);
        OtCalculator.Quote saturday = OtCalculator.quote(
                SALARY, LocalDate.of(2026, 10, 24), LocalTime.of(9, 0), LocalTime.of(17, 30), 5);

        assertThat(OtCalculator.daysPerMonth(5)).isEqualTo(22);
        assertThat(OtCalculator.hoursPerMonth(5)).isEqualByComparingTo("176");
        assertThat(friday.shift()).isEqualTo(OtShift.EVENING);
        assertThat(friday.hourlyRate()).isEqualByComparingTo("4.09");
        assertThat(friday.amount()).isEqualByComparingTo("4.09");
        assertThat(saturday.shift()).isEqualTo(OtShift.WEEKEND);
        assertThat(saturday.paidHours()).isEqualByComparingTo("7.50");
        assertThat(saturday.hourlyRate()).isEqualByComparingTo("5.45");
        assertThat(saturday.amount()).isEqualByComparingTo("40.88");
    }

    @Test
    void sixDayWeekUsesTwentySixDaysAndTreatsSaturdayAsEvening() {
        OtCalculator.Quote saturday = OtCalculator.quote(
                SALARY, LocalDate.of(2026, 10, 24), LocalTime.of(16, 30), LocalTime.of(18, 30), 6);

        assertThat(OtCalculator.hoursPerMonth(6)).isEqualByComparingTo("208");
        assertThat(saturday.shift()).isEqualTo(OtShift.EVENING);
        assertThat(saturday.paidHours()).isEqualByComparingTo("2.00");
        assertThat(saturday.hourlyRate()).isEqualByComparingTo("3.46");
        assertThat(saturday.amount()).isEqualByComparingTo("6.92");
    }

    @Test
    void parsesDashSeparatedRange() {
        assertThat(OtTimeParser.parse("16:30-17:30"))
                .contains(new OtTimeRange(LocalTime.of(16, 30), LocalTime.of(17, 30)));
        assertThat(OtTimeParser.parse("09:00–17:00"))
                .contains(new OtTimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0)));
        assertThat(OtTimeParser.parse("5pm")).isEmpty();
    }
}
