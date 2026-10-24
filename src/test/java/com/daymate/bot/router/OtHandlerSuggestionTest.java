package com.daymate.bot.router;

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
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtHandlerSuggestionTest {
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
    void weekendDayOffersThreeRangesAndNoHoliday() {
        when(ot.salary(1L)).thenReturn(Optional.of(profile(5)));

        handler.handleCallback("cb", 9L, 1L, "ot:day:2026-10-10");

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Map<String, String>>>> keyboard = ArgumentCaptor.forClass(List.class);
        verify(telegram).sendHtml(eq(9L), text.capture(), keyboard.capture());
        assertThat(text.getValue()).doesNotContain("holiday");
        assertThat(labels(keyboard.getValue())).containsExactly(
                "07:30-16:30 · Weekend",
                "08:30-17:30 · Weekend",
                "09:30-18:30 · Weekend",
                "Another day",
                "❌ Cancel");
        assertThat(keyboard.getValue().get(0).get(0).get("callback_data")).isEqualTo("ot:go:20261010:0730:1630:W");
        assertThat(keyboard.getValue().get(2).get(0).get("callback_data")).isEqualTo("ot:go:20261010:0930:1830:W");
    }

    @Test
    void weekdayKeepsAHolidayChoice() {
        when(ot.salary(1L)).thenReturn(Optional.of(profile(5)));

        handler.handleCallback("cb", 9L, 1L, "ot:day:2026-10-09");

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Map<String, String>>>> keyboard = ArgumentCaptor.forClass(List.class);
        verify(telegram).sendHtml(eq(9L), text.capture(), keyboard.capture());
        assertThat(text.getValue()).contains("08:30-17:30 holiday");
        assertThat(labels(keyboard.getValue())).containsExactly(
                "07:30-16:30 · Evening",
                "08:30-17:30 · Evening",
                "09:30-18:30 · Evening",
                "08:30-17:30 · Holiday",
                "Another day",
                "❌ Cancel");
        assertThat(keyboard.getValue().get(3).get(0).get("callback_data")).isEqualTo("ot:go:20261009:0830:1730:H");
    }

    @Test
    void sixDaySaturdayStillOffersHoliday() {
        when(ot.salary(1L)).thenReturn(Optional.of(profile(6)));

        handler.handleCallback("cb", 9L, 1L, "ot:day:2026-10-10");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Map<String, String>>>> keyboard = ArgumentCaptor.forClass(List.class);
        verify(telegram).sendHtml(eq(9L), anyString(), keyboard.capture());
        assertThat(labels(keyboard.getValue())).contains("08:30-17:30 · Holiday", "07:30-16:30 · Evening");
    }

    @Test
    void homeSuggestsTheSameThreeRanges() {
        when(ot.salary(1L)).thenReturn(Optional.of(profile(5)));
        when(ot.today()).thenReturn(LocalDate.of(2026, 10, 9));

        handler.startRecord(9L, 1L, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Map<String, String>>>> keyboard = ArgumentCaptor.forClass(List.class);
        verify(telegram).sendHtml(eq(9L), anyString(), keyboard.capture());
        assertThat(labels(keyboard.getValue())).startsWith(
                "Today 07:30-16:30",
                "Today 08:30-17:30",
                "Today 09:30-18:30");
    }

    private static List<String> labels(List<List<Map<String, String>>> keyboard) {
        return keyboard.stream().flatMap(List::stream).map(button -> button.get("text")).toList();
    }

    private static SalaryProfileEntity profile(int workDays) {
        return new SalaryProfileEntity(1L, workDays == 5, 2, 12, 26, workDays,
                new BigDecimal("400.00"), Instant.EPOCH);
    }
}
