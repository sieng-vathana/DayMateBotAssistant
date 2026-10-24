package com.daymate.bot.notes;

import com.daymate.bot.core.access.AccessDecision;
import com.daymate.bot.core.access.FeatureAccessGuard;
import com.daymate.bot.domain.Language;
import com.daymate.bot.entity.NoteEntity;
import com.daymate.bot.entity.NoteReminderEntity;
import com.daymate.bot.i18n.LanguageContext;
import com.daymate.bot.modules.notes.NotesModule;
import com.daymate.bot.service.LanguageService;
import com.daymate.bot.telegram.TelegramApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotesReminderSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-10-25T13:00:00Z");

    @Mock
    private NoteService notes;
    @Mock
    private LanguageService languages;
    @Mock
    private TelegramApiClient telegram;
    @Mock
    private FeatureAccessGuard guard;

    @Test
    void doesNothingWhenTelegramIsNotConfigured() {
        when(telegram.isConfigured()).thenReturn(false);

        scheduler().sendDueReminders();

        verify(notes, never()).dueReminders(any());
    }

    @Test
    void sendsAReminderThatIsExactlyOneDayLate() {
        NoteReminderEntity reminder = reminder(NOW.minus(Duration.ofHours(24)), 9L);
        NoteEntity note = note();
        when(telegram.isConfigured()).thenReturn(true);
        when(notes.dueReminders(NOW)).thenReturn(List.of(reminder));
        when(guard.decide(1L, NotesModule.ID, NotesModule.REMINDER_CREATE)).thenReturn(AccessDecision.ALLOW);
        when(notes.activeNote(1L, 5L)).thenReturn(Optional.of(note));
        when(languages.get(1L)).thenReturn(Language.KM);

        scheduler().sendDueReminders();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Map<String, String>>>> keyboard = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(telegram).sendMessage(eq(9L), text.capture(), keyboard.capture());
        assertThat(text.getValue()).contains("ការរំលឹក").contains("Milk prices");
        assertThat(keyboard.getValue().get(0).get(0)).containsEntry("callback_data", "note:open:5:l0");
        verify(notes).markSent(1L, 8L, NOW);
        verify(telegram, never()).sendHtml(any(), any(), any());
        assertThat(LanguageContext.get()).isEqualTo(Language.EN);
    }

    @Test
    void skipsAncientRemindersEvenWhenTheFeatureIsOff() {
        NoteReminderEntity reminder = reminder(NOW.minus(Duration.ofHours(24)).minusSeconds(1), 9L);
        when(telegram.isConfigured()).thenReturn(true);
        when(notes.dueReminders(NOW)).thenReturn(List.of(reminder));

        scheduler().sendDueReminders();

        verify(notes).markSkipped(8L);
        verify(telegram, never()).sendMessage(any(), any(), any());
        verify(notes, never()).markSent(any(), any(), any());
    }

    @Test
    void leavesACurrentReminderPendingWhenTheFeatureIsOff() {
        NoteReminderEntity reminder = reminder(NOW, 9L);
        when(telegram.isConfigured()).thenReturn(true);
        when(notes.dueReminders(NOW)).thenReturn(List.of(reminder));
        when(guard.decide(1L, NotesModule.ID, NotesModule.REMINDER_CREATE)).thenReturn(AccessDecision.FEATURE_OFF);

        scheduler().sendDueReminders();

        verify(notes, never()).markSkipped(any());
        verify(notes, never()).markSent(any(), any(), any());
        verify(telegram, never()).sendMessage(any(), any(), any());
    }

    @Test
    void skipsADeletedNote() {
        NoteReminderEntity reminder = reminder(NOW, null);
        when(telegram.isConfigured()).thenReturn(true);
        when(notes.dueReminders(NOW)).thenReturn(List.of(reminder));
        when(guard.decide(1L, NotesModule.ID, NotesModule.REMINDER_CREATE)).thenReturn(AccessDecision.ALLOW);
        when(notes.activeNote(1L, 5L)).thenReturn(Optional.empty());

        scheduler().sendDueReminders();

        verify(notes).markSkipped(8L);
        verify(telegram, never()).sendMessage(any(), any(), any());
    }

    @Test
    void doesNotMarkSentWhenDeliveryThrows() {
        NoteReminderEntity reminder = reminder(NOW, 9L);
        when(telegram.isConfigured()).thenReturn(true);
        when(notes.dueReminders(NOW)).thenReturn(List.of(reminder));
        when(guard.decide(1L, NotesModule.ID, NotesModule.REMINDER_CREATE)).thenReturn(AccessDecision.ALLOW);
        when(notes.activeNote(1L, 5L)).thenReturn(Optional.of(note()));
        when(languages.get(1L)).thenReturn(Language.EN);
        doThrow(new IllegalStateException("down")).when(telegram).sendMessage(eq(9L), any(), any());

        scheduler().sendDueReminders();

        verify(notes, never()).markSent(any(), any(), any());
        assertThat(LanguageContext.get()).isEqualTo(Language.EN);
    }

    private NotesReminderScheduler scheduler() {
        return new NotesReminderScheduler(
                notes, new NoteFormatter(ZoneId.of("Asia/Phnom_Penh")), languages, telegram, guard,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static NoteReminderEntity reminder(Instant remindAt, Long chatId) {
        NoteReminderEntity reminder = new NoteReminderEntity(5L, 1L, chatId, remindAt, NOW);
        NoteFixtures.setId(reminder, 8L);
        return reminder;
    }

    private static NoteEntity note() {
        NoteEntity note = new NoteEntity(1L, "Milk", "Milk prices", false, NOW);
        NoteFixtures.setId(note, 5L);
        return note;
    }
}
