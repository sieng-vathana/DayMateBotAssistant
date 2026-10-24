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
    void listKeepsEachRecordOnTwoPlainLines() {
        OtRecordEntity record = new OtRecordEntity(
                1L, 1L, 2, LocalDate.of(2026, 10, 3),
                LocalTime.of(9, 0), LocalTime.of(17, 30), OtShift.WEEKEND,
                new BigDecimal("8.50"), new BigDecimal("1.00"), new BigDecimal("7.50"),
                new BigDecimal("4.62"), new BigDecimal("34.65"), Instant.EPOCH);
        OtRecordService.MonthSummary summary = new OtRecordService.MonthSummary(
                YearMonth.of(2026, 10), List.of(record), new BigDecimal("7.50"), new BigDecimal("34.65"));

        String text = formatter.list(summary);

        assertThat(text).contains("#2 · Sat, 3 Oct 2026 · Weekend (200%)");
        assertThat(text).contains("09:00-17:30 → 7.50h (incl. 1.00h break) × $4.62/h = $34.65");
        assertThat(text).contains("Oct 2026 so far: 7.50h · $34.65");
        assertThat(text.lines().filter(line -> line.contains("#2") || line.contains("09:00") || line.contains("so far"))
                .noneMatch(line -> line.indexOf('\u3164') >= 0)).isTrue();
        assertThat(text.lines().reduce((first, second) -> second).orElseThrow())
                .isEqualTo("\u3164".repeat(56));
    }
}
