package com.daymate.bot.service;

import com.daymate.bot.domain.Language;
import com.daymate.bot.entity.SalaryProfileEntity;
import com.daymate.bot.ot.OtFormatter;
import com.daymate.bot.ot.OtRecordService;
import com.daymate.bot.telegram.TelegramApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaydayReminderSchedulerTest {
    @Mock
    private OtRecordService ot;
    @Mock
    private LanguageService languages;
    @Mock
    private TelegramApiClient telegram;

    @Test
    void sendsTheReminderOnceWhenItIsDue() {
        SalaryProfileEntity profile = new SalaryProfileEntity(
                1L, true, 2, 12, 26, 5, new BigDecimal("400.00"), Instant.EPOCH);
        OtRecordService.PaydayReminder reminder = new OtRecordService.PaydayReminder(
                LocalDate.of(2026, 10, 26), new BigDecimal("200.00"), new BigDecimal("39.50"), new BigDecimal("151.25"));
        when(telegram.isConfigured()).thenReturn(true);
        when(ot.acledaProfiles()).thenReturn(List.of(profile));
        when(ot.today()).thenReturn(LocalDate.of(2026, 10, 25));
        when(ot.paydayReminder(1L, LocalDate.of(2026, 10, 25))).thenReturn(Optional.of(reminder));
        when(ot.chatIdFor(1L)).thenReturn(9L);
        when(languages.get(1L)).thenReturn(Language.EN);
        PaydayReminderScheduler scheduler = new PaydayReminderScheduler(ot, new OtFormatter(), languages, telegram);

        scheduler.sendReminders();

        verify(telegram).sendMessage(9L, new OtFormatter().paydayReminder(reminder));
        verify(ot).markPaydayReminderSent(1L, LocalDate.of(2026, 10, 26));
    }

    @Test
    void skipsWhenNothingIsDue() {
        SalaryProfileEntity profile = new SalaryProfileEntity(
                1L, true, 2, 12, 26, 5, new BigDecimal("400.00"), Instant.EPOCH);
        when(telegram.isConfigured()).thenReturn(true);
        when(ot.acledaProfiles()).thenReturn(List.of(profile));
        when(ot.today()).thenReturn(LocalDate.of(2026, 10, 24));
        when(ot.paydayReminder(1L, LocalDate.of(2026, 10, 24))).thenReturn(Optional.empty());
        PaydayReminderScheduler scheduler = new PaydayReminderScheduler(ot, new OtFormatter(), languages, telegram);

        scheduler.sendReminders();

        verify(telegram, never()).sendMessage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
