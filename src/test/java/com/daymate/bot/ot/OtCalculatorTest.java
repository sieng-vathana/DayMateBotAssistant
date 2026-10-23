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
    void weekdayEveningMatchesOneHourAtOneHundredFiftyPercent() {
        OtCalculator.Quote quote = OtCalculator.quote(
                SALARY, LocalDate.of(2026, 10, 23), LocalTime.of(16, 30), LocalTime.of(17, 30));

        assertThat(quote.shift()).isEqualTo(OtShift.EVENING);
        assertThat(quote.paidHours()).isEqualByComparingTo("1.00");
        assertThat(quote.breakHours()).isEqualByComparingTo("0.00");
        assertThat(quote.hourlyRate()).isEqualByComparingTo("3.46");
        assertThat(quote.amount()).isEqualByComparingTo("3.46");
    }

    @Test
    void weekendLongShiftDeductsOneHourBreak() {
        OtCalculator.Quote full = OtCalculator.quote(
                SALARY, LocalDate.of(2026, 10, 3), LocalTime.of(9, 0), LocalTime.of(17, 30));
        OtCalculator.Quote afternoon = OtCalculator.quote(
                SALARY, LocalDate.of(2026, 10, 10), LocalTime.of(13, 0), LocalTime.of(19, 0));

        assertThat(full.shift()).isEqualTo(OtShift.WEEKEND);
        assertThat(full.paidHours()).isEqualByComparingTo("7.50");
        assertThat(full.breakHours()).isEqualByComparingTo("1.00");
        assertThat(full.hourlyRate()).isEqualByComparingTo("4.62");
        assertThat(full.amount()).isEqualByComparingTo("34.65");

        assertThat(afternoon.paidHours()).isEqualByComparingTo("5.00");
        assertThat(afternoon.amount()).isEqualByComparingTo("23.10");
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
