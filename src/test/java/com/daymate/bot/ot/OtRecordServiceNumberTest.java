package com.daymate.bot.ot;

import com.daymate.bot.domain.OtShift;
import com.daymate.bot.domain.OtTimeRange;
import com.daymate.bot.entity.OtRecordEntity;
import com.daymate.bot.entity.SalaryProfileEntity;
import com.daymate.bot.repository.OtRecordRepository;
import com.daymate.bot.repository.SalaryProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtRecordServiceNumberTest {
    @Mock
    private SalaryProfileRepository salaries;
    @Mock
    private OtRecordRepository records;

    private OtRecordService service;

    @BeforeEach
    void setUp() {
        service = new OtRecordService(salaries, records,
                Clock.fixed(Instant.parse("2026-10-24T02:00:00Z"), ZoneId.of("Asia/Phnom_Penh")),
                ZoneId.of("Asia/Phnom_Penh"));
    }

    @Test
    void monthListCountsFromOneInDateOrder() {
        OtRecordEntity insertedLater = entry(12L, 12, LocalDate.of(2026, 10, 10), LocalTime.of(13, 0));
        OtRecordEntity firstDay = entry(2L, 2, LocalDate.of(2026, 10, 3), LocalTime.of(9, 0));
        OtRecordEntity afterGap = entry(6L, 6, LocalDate.of(2026, 10, 13), LocalTime.of(16, 30));
        when(records.findByTelegramUserIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAscIdAsc(
                eq(1L), any(), any()))
                .thenReturn(new ArrayList<>(List.of(insertedLater, firstDay, afterGap)));

        OtRecordService.MonthSummary summary = service.monthSummary(1L, YearMonth.of(2026, 10));

        assertThat(summary.records()).extracting(OtRecordEntity::getWorkDate)
                .containsExactly(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 13));
        assertThat(summary.records()).extracting(OtRecordEntity::getRecordNumber)
                .containsExactly(1, 2, 3);
    }

    @Test
    void sameDayIsOrderedByStartTime() {
        OtRecordEntity evening = entry(4L, 4, LocalDate.of(2026, 10, 10), LocalTime.of(16, 30));
        OtRecordEntity morning = entry(9L, 9, LocalDate.of(2026, 10, 10), LocalTime.of(8, 0));
        when(records.findByTelegramUserIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAscIdAsc(
                eq(1L), any(), any()))
                .thenReturn(new ArrayList<>(List.of(evening, morning)));

        OtRecordService.MonthSummary summary = service.monthSummary(1L, YearMonth.of(2026, 10));

        assertThat(summary.records()).extracting(OtRecordEntity::getRecordNumber).containsExactly(1, 2);
        assertThat(summary.records().get(0).getStartTime()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    void deleteClosesTheGap() {
        OtRecordEntity first = entry(2L, 1, LocalDate.of(2026, 10, 3), LocalTime.of(9, 0));
        OtRecordEntity second = entry(6L, 2, LocalDate.of(2026, 10, 10), LocalTime.of(13, 0));
        OtRecordEntity third = entry(7L, 3, LocalDate.of(2026, 10, 13), LocalTime.of(16, 30));
        when(records.findByTelegramUserIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAscIdAsc(
                eq(1L), any(), any()))
                .thenReturn(new ArrayList<>(List.of(first, second, third)))
                .thenReturn(new ArrayList<>(List.of(first, third)));

        service.deleteRecord(1L, 2, YearMonth.of(2026, 10));

        verify(records).delete(second);
        assertThat(first.getRecordNumber()).isEqualTo(1);
        assertThat(third.getRecordNumber()).isEqualTo(2);
    }

    @Test
    void updateChangesTimeAndRateAndKeepsTheDate() {
        LocalDate date = LocalDate.of(2026, 10, 9);
        OtRecordEntity record = entry(4L, 2, date, LocalTime.of(8, 30));
        when(salaries.findById(1L)).thenReturn(Optional.of(salary()));
        when(records.findByTelegramUserIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAscIdAsc(
                eq(1L), any(), any())).thenReturn(new ArrayList<>(List.of(record)));

        OtRecordService.SavedOt saved = service.update(1L, 1, YearMonth.of(2026, 10),
                new OtTimeRange(LocalTime.of(9, 30), LocalTime.of(18, 30)), OtShift.HOLIDAY);

        OtCalculator.Quote quote = OtCalculator.quote(new BigDecimal("400.00"),
                LocalTime.of(9, 30), LocalTime.of(18, 30), OtShift.HOLIDAY, 5);
        assertThat(saved.record()).isSameAs(record);
        assertThat(record.getWorkDate()).isEqualTo(date);
        assertThat(record.getStartTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(record.getEndTime()).isEqualTo(LocalTime.of(18, 30));
        assertThat(record.getShift()).isEqualTo(OtShift.HOLIDAY);
        assertThat(record.getPaidHours()).isEqualByComparingTo(quote.paidHours());
        assertThat(record.getHourlyRate()).isEqualByComparingTo(quote.hourlyRate());
        assertThat(record.getAmount()).isEqualByComparingTo(quote.amount());
        assertThat(record.getRecordNumber()).isEqualTo(1);
        verify(records).save(record);
    }

    @Test
    void earlierStartMovesTheRecordEarlierInTheMonth() {
        LocalDate date = LocalDate.of(2026, 10, 10);
        OtRecordEntity later = entry(2L, 9, date, LocalTime.of(16, 30));
        OtRecordEntity earlier = entry(1L, 8, date, LocalTime.of(9, 0));
        when(salaries.findById(1L)).thenReturn(Optional.of(salary()));
        when(records.findByTelegramUserIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAscIdAsc(
                eq(1L), any(), any())).thenReturn(new ArrayList<>(List.of(later, earlier)));

        service.update(1L, 2, YearMonth.of(2026, 10),
                new OtTimeRange(LocalTime.of(7, 30), LocalTime.of(16, 30)), OtShift.WEEKEND);

        assertThat(later.getWorkDate()).isEqualTo(date);
        assertThat(later.getStartTime()).isEqualTo(LocalTime.of(7, 30));
        assertThat(later.getRecordNumber()).isEqualTo(1);
        assertThat(earlier.getRecordNumber()).isEqualTo(2);
    }

    @Test
    void missingRecordIsRejected() {
        when(salaries.findById(1L)).thenReturn(Optional.of(salary()));
        when(records.findByTelegramUserIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAscIdAsc(
                eq(1L), any(), any())).thenReturn(new ArrayList<>());

        assertThatThrownBy(() -> service.update(1L, 4, YearMonth.of(2026, 10),
                new OtTimeRange(LocalTime.of(8, 30), LocalTime.of(17, 30)), OtShift.EVENING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    private static SalaryProfileEntity salary() {
        return new SalaryProfileEntity(1L, true, 2, 12, 26, 5, new BigDecimal("400.00"), Instant.EPOCH);
    }

    private static OtRecordEntity entry(Long id, int number, LocalDate date, LocalTime start) {
        OtRecordEntity record = new OtRecordEntity(
                1L, 1L, number, date, start, start.plusHours(1), OtShift.EVENING,
                new BigDecimal("1.00"), BigDecimal.ZERO, new BigDecimal("1.00"),
                new BigDecimal("3.46"), new BigDecimal("3.46"), Instant.EPOCH);
        setId(record, id);
        return record;
    }

    private static void setId(OtRecordEntity record, Long id) {
        try {
            java.lang.reflect.Field field = OtRecordEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(record, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
