package com.daymate.bot.ot;

import com.daymate.bot.domain.OtShift;
import com.daymate.bot.entity.OtRecordEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OtFormatterListTest {
    private final OtFormatter formatter = new OtFormatter();

    @Test
    void listPadsTheBubblePastTheLongestRecordLine() {
        OtRecordEntity record = new OtRecordEntity(
                1L, 1L, 2, LocalDate.of(2026, 10, 3),
                LocalTime.of(9, 0), LocalTime.of(17, 30), OtShift.WEEKEND,
                new BigDecimal("8.50"), new BigDecimal("1.00"), new BigDecimal("7.50"),
                new BigDecimal("4.62"), new BigDecimal("34.65"), Instant.EPOCH);
        OtRecordService.MonthSummary summary = new OtRecordService.MonthSummary(
                YearMonth.of(2026, 10), List.of(record), new BigDecimal("7.50"), new BigDecimal("34.65"));

        String text = formatter.list(summary);
        String amountLine = text.lines().filter(line -> line.contains("$34.65")).findFirst().orElseThrow();

        assertThat(text).contains("#2");
        assertThat(text).contains("09:00-17:30");
        assertThat(amountLine).endsWith("$34.65");
        assertThat(amountLine.indexOf("\u3164")).isPositive();
        assertThat(amountLine.chars().filter(ch -> ch == 0x3164).count()).isGreaterThan(10);
        assertThat(text.lines().reduce((first, second) -> second).orElseThrow()).matches("\u3164+");
    }
}
