package com.daymate.bot.notes;

import com.daymate.bot.core.access.AccessDecision;
import com.daymate.bot.core.access.AccessDeniedException;
import com.daymate.bot.entity.NoteEntity;
import com.daymate.bot.entity.NoteReminderEntity;
import com.daymate.bot.entity.NoteTagEntity;
import com.daymate.bot.modules.notes.NotesModule;
import com.daymate.bot.repository.NoteReminderRepository;
import com.daymate.bot.repository.NoteRepository;
import com.daymate.bot.repository.NoteTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-24T01:00:00Z");

    @Mock
    private com.daymate.bot.core.access.FeatureAccessGuard guard;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private NoteTagRepository tags;
    @Mock
    private NoteReminderRepository reminders;

    private NoteService service;

    @BeforeEach
    void setUp() {
        service = new NoteService(guard, noteRepository, tags, reminders, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void twoIntentionalSavesBothPersist() {
        allow(NotesModule.CREATE);
        when(noteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(1L, 9L, "same");
        service.create(1L, 9L, "same");

        verify(noteRepository, times(2)).save(any(NoteEntity.class));
    }

    @Test
    void deniedCreateDoesNotSave() {
        when(guard.decide(1L, "notes", NotesModule.CREATE)).thenReturn(AccessDecision.MODULE_OFF);

        assertThatThrownBy(() -> service.create(1L, 9L, "secret"))
                .isInstanceOf(AccessDeniedException.class)
                .isNotInstanceOf(IllegalArgumentException.class);
        verify(noteRepository, never()).save(any());
    }

    @Test
    void updateWithoutAHashtagKeepsTagsAndPin() {
        allow(NotesModule.EDIT);
        NoteEntity note = note(true);
        when(noteRepository.findByIdAndTelegramUserIdAndDeletedAtIsNull(5L, 1L)).thenReturn(Optional.of(note));
        when(noteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(1L, 5L, "no tags here");

        assertThat(note.getContent()).isEqualTo("no tags here");
        assertThat(note.isPinned()).isTrue();
        verify(tags, never()).deleteAll(any());
    }

    @Test
    void updateWithHashtagsReplacesTags() {
        allow(NotesModule.EDIT);
        NoteEntity note = note(true);
        when(noteRepository.findByIdAndTelegramUserIdAndDeletedAtIsNull(5L, 1L)).thenReturn(Optional.of(note));
        when(noteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(1L, 5L, "hello #Java #java #Work");

        verify(tags).deleteAll(any());
        ArgumentCaptor<NoteTagEntity> saved = ArgumentCaptor.forClass(NoteTagEntity.class);
        verify(tags, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(NoteTagEntity::getTagName).containsExactly("java", "work");
        assertThat(note.isPinned()).isTrue();
    }

    @Test
    void secondDeleteSaysTheNoteIsGone() {
        allow(NotesModule.DELETE);
        NoteEntity note = note(false);
        when(noteRepository.findByIdAndTelegramUserIdAndDeletedAtIsNull(5L, 1L))
                .thenReturn(Optional.of(note), Optional.empty());

        service.softDelete(1L, 5L);

        assertThat(note.getDeletedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> service.softDelete(1L, 5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("That note is no longer available.");
    }

    @Test
    void scheduleCancelsThePreviousPendingReminder() {
        allow(NotesModule.REMINDER_CREATE);
        NoteEntity note = note(false);
        Instant future = NOW.plus(Duration.ofHours(2));
        NoteReminderEntity previous = new NoteReminderEntity(5L, 1L, 9L, future, NOW);
        when(noteRepository.findByIdAndTelegramUserIdAndDeletedAtIsNull(5L, 1L)).thenReturn(Optional.of(note));
        when(reminders.findByNoteIdAndTelegramUserIdAndStatus(5L, 1L, NoteReminderEntity.PENDING))
                .thenReturn(List.of(previous));
        when(reminders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NoteService.Scheduled scheduled = service.schedule(1L, 9L, 5L, future);

        assertThat(previous.getStatus()).isEqualTo(NoteReminderEntity.CANCELLED);
        assertThat(scheduled.reminder().getStatus()).isEqualTo(NoteReminderEntity.PENDING);
        assertThat(scheduled.reminder().getRemindAt()).isEqualTo(future);
        assertThat(scheduled.reminder().getChatId()).isEqualTo(9L);
    }

    @Test
    void scheduleRejectsATimeThatIsNotInTheFuture() {
        allow(NotesModule.REMINDER_CREATE);
        when(noteRepository.findByIdAndTelegramUserIdAndDeletedAtIsNull(5L, 1L)).thenReturn(Optional.of(note(false)));

        assertThatThrownBy(() -> service.schedule(1L, 9L, 5L, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("That time has already passed.");
        verify(reminders, never()).save(any());
    }

    @Test
    void markSentUpdatesOnlyAPendingReminder() {
        NoteReminderEntity row = new NoteReminderEntity(5L, 1L, 9L, NOW.plusSeconds(60), NOW);
        when(reminders.findByIdAndTelegramUserId(8L, 1L)).thenReturn(Optional.of(row));

        assertThat(service.markSent(1L, 8L, NOW)).isTrue();
        assertThat(row.getStatus()).isEqualTo(NoteReminderEntity.SENT);
        assertThat(row.getSentAt()).isEqualTo(NOW);
        assertThat(service.markSent(1L, 8L, NOW)).isFalse();
    }

    @Test
    void searchClampsToTheLastPageAndEscapesTheKeyword() {
        allow(NotesModule.SEARCH);
        Page<NoteEntity> far = new PageImpl<>(List.of(), PageRequest.of(200, 5), 6);
        Page<NoteEntity> last = new PageImpl<>(List.of(note(false)), PageRequest.of(1, 5), 6);
        when(noteRepository.search(eq(1L), eq("%ab%"), any())).thenReturn(far, last);

        Page<NoteEntity> result = service.search(1L, " Ab ", 500);

        assertThat(result.getNumber()).isEqualTo(1);
        assertThatThrownBy(() -> service.search(1L, "100%", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Send a search of 2 to 40 characters.");
    }

    @Test
    void activeDeliveryRequiresTheReminderFeature() {
        when(guard.decide(1L, "notes", NotesModule.REMINDER_CREATE)).thenReturn(AccessDecision.FEATURE_OFF);
        NoteReminderEntity reminder = new NoteReminderEntity(5L, 1L, 9L, NOW.plusSeconds(60), NOW);

        assertThat(service.activeForDelivery(reminder)).isEmpty();
        verify(noteRepository, never()).findByIdAndTelegramUserIdAndDeletedAtIsNull(any(), any());
    }

    private void allow(String feature) {
        when(guard.decide(1L, "notes", feature)).thenReturn(AccessDecision.ALLOW);
    }

    private static NoteEntity note(boolean pinned) {
        NoteEntity note = new NoteEntity(1L, "Title", "Title", pinned, NOW);
        NoteFixtures.setId(note, 5L);
        return note;
    }
}
