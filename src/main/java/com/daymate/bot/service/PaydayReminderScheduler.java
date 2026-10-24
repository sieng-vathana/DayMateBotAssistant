package com.daymate.bot.service;

import com.daymate.bot.core.access.AccessDecision;
import com.daymate.bot.core.access.FeatureAccessGuard;
import com.daymate.bot.domain.Language;
import com.daymate.bot.entity.SalaryProfileEntity;
import com.daymate.bot.i18n.LanguageContext;
import com.daymate.bot.modules.overtime.OvertimeModule;
import com.daymate.bot.ot.OtFormatter;
import com.daymate.bot.ot.OtRecordService;
import com.daymate.bot.telegram.TelegramApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PaydayReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(PaydayReminderScheduler.class);

    private final OtRecordService ot;
    private final OtFormatter formatter;
    private final LanguageService languages;
    private final TelegramApiClient telegram;
    private final FeatureAccessGuard guard;

    public PaydayReminderScheduler(OtRecordService ot, OtFormatter formatter, LanguageService languages,
                                   TelegramApiClient telegram, FeatureAccessGuard guard) {
        this.ot = ot;
        this.formatter = formatter;
        this.languages = languages;
        this.telegram = telegram;
        this.guard = guard;
    }

    @Scheduled(cron = "${telegram.bot.payday-reminder-cron:0 0 8-20 * * *}", zone = "${telegram.bot.timezone:Asia/Phnom_Penh}")
    public void sendReminders() {
        if (!telegram.isConfigured()) {
            return;
        }
        for (SalaryProfileEntity profile : ot.acledaProfiles()) {
            Long userId = profile.getTelegramUserId();
            try {
                if (guard.decide(userId, FeatureAccessGuard.OVERTIME, OvertimeModule.REMINDER) != AccessDecision.ALLOW) {
                    continue;
                }
                Optional<OtRecordService.PaydayReminder> reminder = ot.paydayReminder(userId, ot.today());
                if (reminder.isEmpty()) {
                    continue;
                }
                LanguageContext.set(languages.get(userId));
                telegram.sendMessage(ot.chatIdFor(userId), formatter.paydayReminder(reminder.get()));
                ot.markPaydayReminderSent(userId, reminder.get().payday());
            } catch (RuntimeException exception) {
                log.warn("Could not send payday reminder to user {}: {}", userId, exception.getMessage());
            } finally {
                LanguageContext.set(Language.EN);
            }
        }
    }
}
