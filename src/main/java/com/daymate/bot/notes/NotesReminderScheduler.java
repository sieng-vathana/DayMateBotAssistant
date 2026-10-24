package com.daymate.bot.notes;

import com.daymate.bot.core.access.AccessDecision;
import com.daymate.bot.core.access.FeatureAccessGuard;
import com.daymate.bot.domain.Language;
import com.daymate.bot.entity.NoteEntity;
import com.daymate.bot.entity.NoteReminderEntity;
import com.daymate.bot.i18n.LanguageContext;
import com.daymate.bot.i18n.Texts;
import com.daymate.bot.modules.notes.NotesModule;
import com.daymate.bot.service.LanguageService;
import com.daymate.bot.telegram.TelegramApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NotesReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(NotesReminderScheduler.class);

    private final NoteService notes;
    private final NoteFormatter formatter;
    private final LanguageService languages;
    private final TelegramApiClient telegram;
    private final FeatureAccessGuard guard;
    private final Clock clock;

    public NotesReminderScheduler(NoteService notes, NoteFormatter formatter, LanguageService languages,
                                  TelegramApiClient telegram, FeatureAccessGuard guard, Clock clock) {
        this.notes = notes;
        this.formatter = formatter;
        this.languages = languages;
        this.telegram = telegram;
        this.guard = guard;
        this.clock = clock;
    }

    @Scheduled(cron = "0 * * * * *", zone = "${telegram.bot.timezone:Asia/Phnom_Penh}")
    public void sendDueReminders() {
        if (!telegram.isConfigured()) {
            return;
        }
        Instant now = Instant.now(clock);
        for (NoteReminderEntity reminder : notes.dueReminders(now)) {
            Long userId = reminder.getTelegramUserId();
            try {
                deliver(reminder, userId, now);
            } catch (RuntimeException exception) {
                log.warn("Could not send note reminder for user {} reminder {} note {} ({})",
                        userId, reminder.getId(), reminder.getNoteId(), exception.getClass().getSimpleName());
            } finally {
                LanguageContext.set(Language.EN);
            }
        }
    }

    private void deliver(NoteReminderEntity reminder, Long userId, Instant now) {
        if (reminder.getRemindAt() != null && reminder.getRemindAt().isBefore(now.minus(Duration.ofHours(24)))) {
            notes.markSkipped(reminder.getId());
            return;
        }
        if (guard.decide(userId, NotesModule.ID, NotesModule.REMINDER_CREATE) != AccessDecision.ALLOW) {
            return;
        }
        Optional<NoteEntity> note = notes.activeNote(userId, reminder.getNoteId());
        if (note.isEmpty()) {
            notes.markSkipped(reminder.getId());
            return;
        }
        LanguageContext.set(languages.get(userId));
        Long chatId = reminder.getChatId() != null ? reminder.getChatId() : userId;
        telegram.sendMessage(chatId, formatter.fired(note.get()), viewKeyboard(note.get().getId()));
        notes.markSent(userId, reminder.getId(), now);
    }

    private static List<List<Map<String, String>>> viewKeyboard(Long noteId) {
        return List.of(List.of(Map.of(
                "text", Texts.t("View Note", "មើលកំណត់ចំណាំ"),
                "callback_data", "note:open:" + noteId + ":l0")));
    }
}
