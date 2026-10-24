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
    void listBoldsOnlyTheMonthOtTotal() {
        OtRecordEntity record = new OtRecordEntity(
                1L, 1L, 2, LocalDate.of(2026, 10, 3),
                LocalTime.of(9, 0), LocalTime.of(17, 30), OtShift.WEEKEND,
                new BigDecimal("8.50"), new BigDecimal("1.00"), new BigDecimal("7.50"),
                new BigDecimal("4.62"), new BigDecimal("34.65"), Instant.EPOCH);
        OtRecordService.MonthSummary summary = new OtRecordService.MonthSummary(
                YearMonth.of(2026, 10), List.of(record), new BigDecimal("7.50"), new BigDecimal("34.65"));

        String text = formatter.list(summary);

        String row = text.lines().filter(line -> line.startsWith("#2")).findFirst().orElseThrow();
        assertThat(row).isEqualTo("#2 Sat 3 Oct Weekend 200% 09:00-17:30 7.50h+1h $4.62=$34.65");
        assertThat(row).doesNotContain("\n");
        assertThat(row.length()).isLessThanOrEqualTo(64);
        assertThat(text).contains("Oct 2026 so far: 7.50h · <b>$34.65</b>");
        assertThat(text).contains("Tap ✏️ to change one, 🗑 to delete, or send /del 3.");
        assertThat(text).doesNotContain("\u3164");
        assertThat(text).doesNotContain("\u2003");
    }

    @Test
    void longestRecordStillFitsOnOneRow() {
        OtRecordEntity record = new OtRecordEntity(
                1L, 1L, 12, LocalDate.of(2026, 10, 24),
                LocalTime.of(8, 0), LocalTime.of(17, 0), OtShift.WEEKEND,
                new BigDecimal("9.00"), new BigDecimal("1.00"), new BigDecimal("8.00"),
                new BigDecimal("4.62"), new BigDecimal("36.96"), Instant.EPOCH);
        String text = formatter.list(new OtRecordService.MonthSummary(
                YearMonth.of(2026, 10), List.of(record), new BigDecimal("8.00"), new BigDecimal("36.96")));
        String row = text.lines().filter(line -> line.startsWith("#12")).findFirst().orElseThrow();

        assertThat(row).isEqualTo("#12 Sat 24 Oct Weekend 200% 08:00-17:00 8.00h+1h $4.62=$36.96");
        assertThat(row.length()).isLessThanOrEqualTo(64);
    }
}
