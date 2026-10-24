package com.daymate.bot.router;

import com.daymate.bot.domain.ConversationStep;
import com.daymate.bot.domain.OtShift;
import com.daymate.bot.domain.OtTimeRange;
import com.daymate.bot.entity.ConversationStateEntity;
import com.daymate.bot.entity.OtRecordEntity;
import com.daymate.bot.entity.SalaryProfileEntity;
import com.daymate.bot.ot.OtFormatter;
import com.daymate.bot.ot.OtRecordService;
import com.daymate.bot.service.ConversationService;
import com.daymate.bot.telegram.TelegramApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtHandlerEditTest {
    @Mock
    private ConversationService conversations;
    @Mock
    private OtRecordService ot;
    @Mock
    private TelegramApiClient telegram;

    private OtHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OtHandler(conversations, ot, new OtFormatter(), telegram,
                Clock.fixed(Instant.parse("2026-10-10T02:00:00Z"), ZoneId.of("Asia/Phnom_Penh")));
    }

    @Test
    void listPairsEditAndDeleteOnEachRecord() {
        when(ot.currentMonth()).thenReturn(YearMonth.of(2026, 10));
        OtRecordEntity first = record(1, LocalDate.of(2026, 10, 3), LocalTime.of(9, 0), LocalTime.of(17, 30), OtShift.WEEKEND);
        OtRecordEntity second = record(2, LocalDate.of(2026, 10, 10), LocalTime.of(8, 30), LocalTime.of(17, 30), OtShift.WEEKEND);
        when(ot.monthSummary(1L, YearMonth.of(2026, 10))).thenReturn(new OtRecordService.MonthSummary(
                YearMonth.of(2026, 10), List.of(first, second), new BigDecimal("15.00"), new BigDecimal("69.30")));

        handler.list(9L, 1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Map<String, String>>>> keyboard = ArgumentCaptor.forClass(List.class);
        verify(telegram).sendHtml(eq(9L), contains("Tap ✏️ to change one"), keyboard.capture());
        assertThat(callbacks(keyboard.getValue().get(0))).containsExactly("ot:edit:2026-10:1", "ot:del:2026-10:1");
        assertThat(callbacks(keyboard.getValue().get(1))).containsExactly("ot:edit:2026-10:2", "ot:del:2026-10:2");
        assertThat(keyboard.getValue()).allSatisfy(row ->
                row.forEach(button -> assertThat(button.get("callback_data").getBytes().length).isLessThanOrEqualTo(64)));
    }

    @Test
    void weekendEditHidesHoliday() {
        when(ot.findRecord(1L, 1, YearMonth.of(2026, 10))).thenReturn(Optional.of(
                record(1, LocalDate.of(2026, 10, 10), LocalTime.of(9, 0), LocalTime.of(17, 30), OtShift.WEEKEND)));
        when(ot.salary(1L)).thenReturn(Optional.of(profile(5)));

        handler.handleCallback("cb", 9L, 1L, "ot:edit:2026-10:1");

        ArgumentCaptor<ConversationStateEntity> state = ArgumentCaptor.forClass(ConversationStateEntity.class);
        verify(conversations).save(state.capture());
        assertThat(state.getValue().getPendingRange()).isEqualTo("e:2026-10:1");
        assertThat(state.getValue().getStep()).isEqualTo(ConversationStep.OT_TIME);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Map<String, String>>>> keyboard = ArgumentCaptor.forClass(List.class);
        verify(telegram).sendHtml(eq(9L), text.capture(), keyboard.capture());
        assertThat(text.getValue()).contains("Edit OT #1", "weekend OT at 200%").doesNotContain("holiday");
        assertThat(labels(keyboard.getValue())).containsExactly(
                "07:30-16:30 · Weekend",
                "08:30-17:30 · Weekend",
                "09:30-18:30 · Weekend",
                "← List",
                "❌ Cancel");
        assertThat(labels(keyboard.getValue())).doesNotContain("Evening 150%", "Weekend 200%");
        assertThat(keyboard.getValue().get(0).get(0).get("callback_data")).isEqualTo("ot:edittime:202610:1:0730:1630:W");
        assertThat(callbacks(keyboard.getValue().get(3))).containsExactly("ot:editback:2026-10", "ot:cancel");
    }

    @Test
    void weekdayEditOffersHolidayTimeAndRate() {
        when(ot.findRecord(1L, 2, YearMonth.of(2026, 10))).thenReturn(Optional.of(
                record(2, LocalDate.of(2026, 10, 9), LocalTime.of(8, 30), LocalTime.of(17, 30), OtShift.EVENING)));
        when(ot.salary(1L)).thenReturn(Optional.of(profile(5)));

        handler.handleCallback("cb", 9L, 1L, "ot:edit:2026-10:2");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Map<String, String>>>> keyboard = ArgumentCaptor.forClass(List.class);
        verify(telegram).sendHtml(eq(9L), contains("08:30-17:30 holiday"), keyboard.capture());
        assertThat(labels(keyboard.getValue())).contains(
                "08:30-17:30 · Holiday", "Holiday 100%", "Evening 150%", "Weekend 200%");
        assertThat(keyboard.getValue().get(3).get(0).get("callback_data")).isEqualTo("ot:edittime:202610:2:0830:1730:H");
    }

    @Test
    void sixDaySaturdayEditStillOffersHoliday() {
        when(ot.findRecord(1L, 1, YearMonth.of(2026, 10))).thenReturn(Optional.of(
                record(1, LocalDate.of(2026, 10, 10), LocalTime.of(8, 30), LocalTime.of(17, 30), OtShift.EVENING)));
        when(ot.salary(1L)).thenReturn(Optional.of(profile(6)));

        handler.handleCallback("cb", 9L, 1L, "ot:edit:2026-10:1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Map<String, String>>>> keyboard = ArgumentCaptor.forClass(List.class);
        verify(telegram).sendHtml(eq(9L), any(), keyboard.capture());
        assertThat(labels(keyboard.getValue())).contains("08:30-17:30 · Holiday", "07:30-16:30 · Evening");
    }

    @Test
    void suggestedTimeUpdatesThatRecord() {
        OtRecordEntity updated = record(1, LocalDate.of(2026, 10, 10), LocalTime.of(7, 30), LocalTime.of(16, 30), OtShift.WEEKEND);
        when(ot.update(eq(1L), eq(1), eq(YearMonth.of(2026, 10)),
                eq(new OtTimeRange(LocalTime.of(7, 30), LocalTime.of(16, 30))), eq(OtShift.WEEKEND)))
                .thenReturn(saved(updated));

        handler.handleCallback("cb", 9L, 1L, "ot:edittime:202610:1:0730:1630:W");

        verify(conversations).clear(9L);
        verify(telegram).sendMessage(eq(9L), contains("Updated OT #1"));
        verify(ot, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void rateButtonKeepsTheCurrentTimes() {
        OtRecordEntity current = record(1, LocalDate.of(2026, 10, 9), LocalTime.of(9, 0), LocalTime.of(17, 30), OtShift.EVENING);
        when(ot.findRecord(1L, 1, YearMonth.of(2026, 10))).thenReturn(Optional.of(current));
        when(ot.update(eq(1L), eq(1), eq(YearMonth.of(2026, 10)),
                eq(new OtTimeRange(LocalTime.of(9, 0), LocalTime.of(17, 30))), eq(OtShift.WEEKEND)))
                .thenReturn(saved(current));

        handler.handleCallback("cb", 9L, 1L, "ot:editrate:2026-10:1:W");

        verify(conversations).clear(9L);
        verify(ot, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void typedTimeKeepsTheCurrentShift() {
        OtRecordEntity holiday = record(1, LocalDate.of(2026, 10, 9), LocalTime.of(8, 30), LocalTime.of(17, 30), OtShift.HOLIDAY);
        when(ot.findRecord(1L, 1, YearMonth.of(2026, 10))).thenReturn(Optional.of(holiday));
        when(ot.update(eq(1L), eq(1), eq(YearMonth.of(2026, 10)),
                eq(new OtTimeRange(LocalTime.of(9, 30), LocalTime.of(18, 30))), eq(OtShift.HOLIDAY)))
                .thenReturn(saved(holiday));

        handler.handleText(editing("e:2026-10:1"), 9L, "09:30-18:30");

        verify(conversations).clear(9L);
        verify(ot, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void typedFullLineUpdatesInsteadOfInserting() {
        OtRecordEntity current = record(1, LocalDate.of(2026, 10, 9), LocalTime.of(8, 30), LocalTime.of(17, 30), OtShift.EVENING);
        when(ot.today()).thenReturn(LocalDate.of(2026, 10, 24));
        when(ot.findRecord(1L, 1, YearMonth.of(2026, 10))).thenReturn(Optional.of(current));
        when(ot.update(eq(1L), eq(1), eq(YearMonth.of(2026, 10)),
                eq(new OtTimeRange(LocalTime.of(9, 30), LocalTime.of(18, 30))), eq(OtShift.HOLIDAY)))
                .thenReturn(saved(current));

        handler.handleText(editing("e:2026-10:1"), 9L, "24 09:30-18:30 holiday");

        verify(ot, never()).record(any(), any(), any(), any(), any());
        verify(conversations).clear(9L);
    }

    @Test
    void invalidTypedTimeStaysOnTheEdit() {
        when(ot.findRecord(1L, 1, YearMonth.of(2026, 10))).thenReturn(Optional.of(
                record(1, LocalDate.of(2026, 10, 10), LocalTime.of(9, 0), LocalTime.of(17, 30), OtShift.WEEKEND)));
        when(ot.salary(1L)).thenReturn(Optional.of(profile(5)));

        handler.handleText(editing("e:2026-10:1"), 9L, "24");

        verify(telegram).sendHtml(eq(9L), contains("Type the time"), isNull());
        verify(conversations, never()).clear(any());
        verify(ot, never()).record(any(), any(), any(), any(), any());
        verify(ot, never()).update(any(), anyInt(), any(), any(), any());
    }

    @Test
    void rejectedRangeKeepsTheEditOpen() {
        OtRecordEntity current = record(1, LocalDate.of(2026, 10, 10), LocalTime.of(9, 0), LocalTime.of(17, 30), OtShift.WEEKEND);
        when(ot.findRecord(1L, 1, YearMonth.of(2026, 10))).thenReturn(Optional.of(current));
        when(ot.update(any(), anyInt(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Finish time must be after the start time on the same day."));

        handler.handleText(editing("e:2026-10:1"), 9L, "17:30-08:30");

        verify(telegram).sendMessage(eq(9L), contains("Finish time must be after"));
        verify(conversations, never()).clear(any());
    }

    private static ConversationStateEntity editing(String pending) {
        ConversationStateEntity state = new ConversationStateEntity(
                9L, 1L, ConversationStep.OT_TIME, Instant.EPOCH);
        state.setPendingRange(pending);
        return state;
    }

    private static OtRecordService.SavedOt saved(OtRecordEntity record) {
        return new OtRecordService.SavedOt(record, new OtRecordService.MonthSummary(
                YearMonth.of(2026, 10), List.of(record), record.getPaidHours(), record.getAmount()));
    }

    private static OtRecordEntity record(int number, LocalDate date, LocalTime start, LocalTime end, OtShift shift) {
        return new OtRecordEntity(1L, 9L, number, date, start, end, shift,
                new BigDecimal("8.50"), new BigDecimal("1.00"), new BigDecimal("7.50"),
                new BigDecimal("4.62"), new BigDecimal("34.65"), Instant.EPOCH);
    }

    private static SalaryProfileEntity profile(int workDays) {
        return new SalaryProfileEntity(1L, workDays == 5, 2, 12, 26, workDays,
                new BigDecimal("400.00"), Instant.EPOCH);
    }

    private static List<String> labels(List<List<Map<String, String>>> keyboard) {
        return keyboard.stream().flatMap(List::stream).map(button -> button.get("text")).toList();
    }

    private static List<String> callbacks(List<Map<String, String>> row) {
        return row.stream().map(button -> button.get("callback_data")).toList();
    }
}
