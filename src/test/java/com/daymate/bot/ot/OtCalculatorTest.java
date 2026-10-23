package com.daymate.bot.ot;

import com.daymate.bot.domain.OtShift;
import com.daymate.bot.domain.OtTimeRange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class OtCalculatorTest {
    private static final BigDecimal SALARY = new BigDecimal("400.00");

    @Test
    void fourHundredOnFiveDayWeekMatchesTheOtherBot() {
        OtCalculator.Quote friday = OtCalculator.quote(
                SALARY, LocalDate.of(2026, 10, 23), LocalTime.of(16, 30), LocalTime.of(17, 30), 5);
        OtCalculator.Quote saturday = OtCalculator.quote(
                SALARY, LocalDate.of(2026, 10, 24), LocalTime.of(9, 0), LocalTime.of(17, 30), 5);

        assertThat(OtCalculator.hoursPerMonth(5)).isEqualByComparingTo("173.3333333333");
        assertThat(friday.shift()).isEqualTo(OtShift.EVENING);
        assertThat(friday.paidHours()).isEqualByComparingTo("1.00");
        assertThat(friday.hourlyRate()).isEqualByComparingTo("3.46");
        assertThat(friday.amount()).isEqualByComparingTo("3.46");
        assertThat(saturday.shift()).isEqualTo(OtShift.WEEKEND);
        assertThat(saturday.paidHours()).isEqualByComparingTo("7.50");
        assertThat(saturday.hourlyRate()).isEqualByComparingTo("4.62");
        assertThat(saturday.amount()).isEqualByComparingTo("34.65");
    }

    @Test
    void sixDayWeekUsesTwoHundredEightHours() {
        OtCalculator.Quote saturday = OtCalculator.quote(
                SALARY, LocalDate.of(2026, 10, 24), LocalTime.of(16, 30), LocalTime.of(18, 30), 6);

        assertThat(OtCalculator.hoursPerMonth(6)).isEqualByComparingTo("208");
        assertThat(saturday.shift()).isEqualTo(OtShift.EVENING);
        assertThat(saturday.paidHours()).isEqualByComparingTo("2.00");
        assertThat(saturday.hourlyRate()).isEqualByComparingTo("2.88");
        assertThat(saturday.amount()).isEqualByComparingTo("5.76");
    }

    @Test
    void holidayPaysTheBaseHour() {
        OtCalculator.Quote quote = OtCalculator.quote(
                SALARY, LocalTime.of(8, 30), LocalTime.of(17, 30), OtShift.HOLIDAY, 5);

        assertThat(quote.shift()).isEqualTo(OtShift.HOLIDAY);
        assertThat(quote.paidHours()).isEqualByComparingTo("8.00");
        assertThat(quote.hourlyRate()).isEqualByComparingTo("2.31");
        assertThat(quote.amount()).isEqualByComparingTo("18.48");
    }

    @Test
    void oneLineStartsWithTheDay() {
        LocalDate today = LocalDate.of(2026, 10, 24);
        assertThat(OtEntryParser.parse("24 16:30-17:30", today))
                .get()
                .satisfies(parsed -> {
                    assertThat(parsed.date()).isEqualTo(LocalDate.of(2026, 10, 24));
                    assertThat(parsed.range().start()).isEqualTo(LocalTime.of(16, 30));
                    assertThat(parsed.range().end()).isEqualTo(LocalTime.of(17, 30));
                    assertThat(parsed.shift()).isNull();
                });
        assertThat(OtEntryParser.parse("3/10 09:00-17:30 holiday", today))
                .get()
                .satisfies(parsed -> {
                    assertThat(parsed.date()).isEqualTo(LocalDate.of(2026, 10, 3));
                    assertThat(parsed.shift()).isEqualTo(OtShift.HOLIDAY);
                });
        assertThat(OtEntryParser.parseDay("yesterday", today)).contains(LocalDate.of(2026, 10, 23));
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
