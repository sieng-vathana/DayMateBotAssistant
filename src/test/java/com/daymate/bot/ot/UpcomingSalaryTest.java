package com.daymate.bot.ot;

import com.daymate.bot.domain.OtShift;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpcomingSalaryTest {
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
    void acledaLastPaydayAddsThisMonthsOt() {
        when(salaries.findById(1L)).thenReturn(Optional.of(acleda()));
        when(records.findByTelegramUserIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAscIdAsc(
                eq(1L), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31))))
                .thenReturn(List.of(
                        ot(new BigDecimal("7.50"), new BigDecimal("34.65")),
                        ot(new BigDecimal("2.00"), new BigDecimal("6.92"))));

        OtRecordService.UpcomingSalary upcoming = service.upcoming(1L);

        assertThat(upcoming.next()).hasSize(2);
        OtRecordService.Payday next = upcoming.next().get(0);
        assertThat(next.date()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(next.amount()).isEqualByComparingTo("200.00");
        assertThat(next.otHours()).isEqualByComparingTo("9.50");
        assertThat(next.ot()).isEqualByComparingTo("41.57");
        assertThat(next.total()).isEqualByComparingTo("241.57");
        assertThat(upcoming.next().get(1).date()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(upcoming.next().get(1).otHours()).isEqualByComparingTo("0.00");
        assertThat(upcoming.next().get(1).ot()).isEqualByComparingTo("0.00");
        assertThat(upcoming.next().get(1).total()).isEqualByComparingTo("200.00");
    }

    @Test
    void otherEmployersKeepTheBasePaydayOnly() {
        SalaryProfileEntity profile = new SalaryProfileEntity(
                1L, false, 2, 12, 26, 6, new BigDecimal("400.00"), Instant.EPOCH);
        when(salaries.findById(1L)).thenReturn(Optional.of(profile));

        OtRecordService.UpcomingSalary upcoming = service.upcoming(1L);

        assertThat(upcoming.next().get(0).date()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(upcoming.next().get(0).otHours()).isEqualByComparingTo("0.00");
        assertThat(upcoming.next().get(0).ot()).isEqualByComparingTo("0.00");
        assertThat(upcoming.next().get(0).total()).isEqualByComparingTo("200.00");
    }

    @Test
    void upcomingCopyShowsOtAndTotalForAcleda() {
        SalaryProfileEntity profile = acleda();
        OtRecordService.UpcomingSalary upcoming = new OtRecordService.UpcomingSalary(
                profile, new BigDecimal("2.31"), List.of(
                new OtRecordService.Payday(LocalDate.of(2026, 10, 26), new BigDecimal("200.00"), new BigDecimal("9.50"), new BigDecimal("41.57")),
                new OtRecordService.Payday(LocalDate.of(2026, 11, 12), new BigDecimal("200.00"), new BigDecimal("0.00"), new BigDecimal("0.00"))));

        String text = new OtFormatter().upcoming(upcoming);

        assertThat(text).contains("26 Oct 2026");
        assertThat(text).contains("Base: $200.00");
        assertThat(text).contains("OT paid: 9.50h · $41.57");
        assertThat(text).contains("Total income: $241.57");
        assertThat(text).contains("OT paid: 0.00h · $0.00");
        assertThat(text).contains("Total income: $200.00");
    }

    @Test
    void reminderIsDueTheDayBeforeTheOtPayday() {
        when(salaries.findById(1L)).thenReturn(Optional.of(acleda()));
        when(records.findByTelegramUserIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAscIdAsc(
                eq(1L), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31))))
                .thenReturn(List.of(
                        ot(new BigDecimal("7.50"), new BigDecimal("34.65")),
                        ot(new BigDecimal("2.00"), new BigDecimal("6.92"))));

        OtRecordService.PaydayReminder reminder = service.paydayReminder(1L, LocalDate.of(2026, 10, 25)).orElseThrow();

        assertThat(reminder.payday()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(reminder.base()).isEqualByComparingTo("200.00");
        assertThat(reminder.otHours()).isEqualByComparingTo("9.50");
        assertThat(reminder.ot()).isEqualByComparingTo("41.57");
        assertThat(reminder.total()).isEqualByComparingTo("241.57");
        assertThat(service.paydayReminder(1L, LocalDate.of(2026, 10, 24))).isEmpty();
    }

    @Test
    void reminderIsSentOnlyOnce() {
        SalaryProfileEntity profile = acleda();
        profile.setPaydayReminderFor(LocalDate.of(2026, 10, 26));
        when(salaries.findById(1L)).thenReturn(Optional.of(profile));

        assertThat(service.paydayReminder(1L, LocalDate.of(2026, 10, 25))).isEmpty();
    }

    @Test
    void reminderCopyIncludesHoursPayAndTotal() {
        OtRecordService.PaydayReminder reminder = new OtRecordService.PaydayReminder(
                LocalDate.of(2026, 10, 26), new BigDecimal("200.00"), new BigDecimal("39.50"), new BigDecimal("151.25"));

        String text = new OtFormatter().paydayReminder(reminder);

        assertThat(text).contains("Payday tomorrow, Mon, 26 Oct 2026");
        assertThat(text).contains("OT: 39.50h · $151.25");
        assertThat(text).contains("Base: $200.00");
        assertThat(text).contains("Total income: $351.25");
        assertThat(text).contains("/otlist");
    }

    private static SalaryProfileEntity acleda() {
        return new SalaryProfileEntity(1L, true, 2, 12, 26, 5, new BigDecimal("400.00"), Instant.EPOCH);
    }

    private static OtRecordEntity ot(BigDecimal paidHours, BigDecimal amount) {
        return new OtRecordEntity(
                1L, 1L, 1, LocalDate.of(2026, 10, 3),
                LocalTime.of(9, 0), LocalTime.of(17, 30), OtShift.WEEKEND,
                paidHours, BigDecimal.ZERO, paidHours,
                new BigDecimal("4.62"), amount, Instant.EPOCH);
    }
}
