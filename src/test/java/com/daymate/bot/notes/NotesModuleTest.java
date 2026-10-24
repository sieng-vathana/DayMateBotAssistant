package com.daymate.bot.notes;

import com.daymate.bot.core.access.AccessDeniedException;
import com.daymate.bot.core.access.AccessMessages;
import com.daymate.bot.core.module.BotContext;
import com.daymate.bot.domain.ConversationStep;
import com.daymate.bot.entity.ConversationStateEntity;
import com.daymate.bot.entity.NoteEntity;
import com.daymate.bot.modules.notes.NotesModule;
import com.daymate.bot.service.ConversationService;
import com.daymate.bot.telegram.TelegramApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.domain.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotesModuleTest {
    @Mock
    private NoteService noteService;
    @Mock
    private ConversationService conversations;
    @Mock
    private TelegramApiClient telegram;

    private NotesModule module;

    @BeforeEach
    void setUp() {
        lenient().when(conversations.get(any())).thenReturn(Optional.empty());
        module = new NotesModule(
                noteService, conversations, telegram,
                new NoteFormatter(ZoneId.of("Asia/Phnom_Penh")),
                new NoteTimes(Clock.fixed(Instant.parse("2026-10-24T01:00:00Z"), ZoneOffset.UTC),
                        ZoneId.of("Asia/Phnom_Penh")));
    }

    @Test
    void registersNotesCommandsAndOpenFeatures() {
        assertThat(module.moduleId()).isEqualTo("notes");
        assertThat(module.displayName()).isEqualTo("📌 Notes");
        assertThat(module.displayOrder()).isEqualTo(40);
        assertThat(module.enabledByDefault()).isTrue();
        assertThat(module.commands()).containsExactly(
                "/note", "/notes", "/my-notes", "/mynotes", "/my_notes", "/search", "/pinned");
        assertThat(module.features()).allMatch(feature -> feature.openByDefault());
        assertThat(module.featureForCommand("/my-notes")).isEqualTo(NotesModule.VIEW);
        assertThat(module.featureForCommand("/my_notes")).isEqualTo(NotesModule.VIEW);
        assertThat(module.commandForMenuAction("list")).isEqualTo("/my-notes");
        assertThat(module.commandForMenuAction("search")).isEqualTo("/search");
    }

    @Test
    void callbackFeaturesKeepTheLongerPrefix() {
        assertThat(module.featureForCallback("note:cancel")).isNull();
        assertThat(module.featureForCallback("note:delok:3")).isEqualTo(NotesModule.DELETE);
        assertThat(module.featureForCallback("note:del:3")).isEqualTo(NotesModule.DELETE);
        assertThat(module.featureForCallback("note:pinlist:0")).isEqualTo(NotesModule.PIN);
        assertThat(module.featureForCallback("note:pin:3")).isEqualTo(NotesModule.PIN);
        assertThat(module.featureForCallback("note:remclr:3")).isEqualTo(NotesModule.REMINDER_CANCEL);
        assertThat(module.featureForCallback("note:rem:3")).isEqualTo(NotesModule.REMINDER_CREATE);
        assertThat(module.featureForCallback("note:tags:1")).isEqualTo(NotesModule.SEARCH);
        assertThat(module.featureForCallback("note:tag:1:0")).isEqualTo(NotesModule.SEARCH);
        assertThat(module.featureForState(state(ConversationStep.NOTES_WAITING_CONTENT))).isEqualTo(NotesModule.CREATE);
    }

    @Test
    void myNotesCommandOpensTheSavedList() {
        when(noteService.list(1L, 0)).thenReturn(Page.empty());

        module.handleCommand(context(null, Set.of(NotesModule.VIEW), null, null), "/my-notes", "extra");

        verify(noteService).list(1L, 0);
    }

    @Test
    void menuHidesFeaturesTheUserCannotUse() {
        BotContext context = context(null, Set.of(NotesModule.VIEW, NotesModule.CREATE), "cb", 10L);

        module.showMenu(context);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Map<String, String>>>> keyboard = ArgumentCaptor.forClass(List.class);
        verify(telegram).editMessage(eq(1L), eq(10L), any(), keyboard.capture());
        assertThat(labels(keyboard.getValue())).contains("➕ New Note", "📋 My Notes", "⬅️ Main Menu")
                .doesNotContain("🔍 Search Notes", "📌 Pinned Notes");
    }

    @Test
    void blankTextDoesNotClearTheDraft() {
        ConversationStateEntity state = state(ConversationStep.NOTES_WAITING_CONTENT);
        when(noteService.create(1L, 1L, "")).thenThrow(new IllegalArgumentException("Send the note text."));

        module.handleMessage(context(state, Set.of(NotesModule.CREATE), null, null), "");

        verify(telegram).sendMessage(1L, "Send the note text.");
        verify(conversations, never()).clear(any());
    }

    @Test
    void savedTextClearsTheDraft() {
        ConversationStateEntity state = state(ConversationStep.NOTES_WAITING_CONTENT);
        NoteEntity note = new NoteEntity(1L, "Remember this", "Remember this", false, Instant.parse("2026-10-24T01:00:00Z"));
        NoteFixtures.setId(note, 4L);
        when(noteService.create(1L, 1L, "Remember this")).thenReturn(note);

        module.handleMessage(context(state, Set.of(NotesModule.CREATE), null, null), "Remember this");

        verify(noteService).create(1L, 1L, "Remember this");
        verify(conversations).clear(1L);
    }

    @Test
    void searchResultsDoNotTreatTheNextSentenceAsAQuery() {
        ConversationStateEntity state = state(ConversationStep.NOTES_WAITING_SEARCH_QUERY);
        state.setAction("page");
        state.setTitle("hello");

        module.handleMessage(context(state, Set.of(NotesModule.SEARCH), null, null), "something else");

        verify(noteService, never()).search(any(), any(), anyInt());
        verify(noteService, never()).create(any(), any(), any());
        verify(telegram).sendMessage(1L, "Send /search to look for something else.");
    }

    @Test
    void malformedCallbackIsAnswered() {
        assertThat(module.handleCallback(context(null, Set.of(NotesModule.VIEW), "cb", 10L), "note:open:nope:l0"))
                .isTrue();

        verify(telegram).answerCallback("cb", "This button is no longer available.", true);
        verify(noteService, never()).get(any(), anyLong());
    }

    @Test
    void confirmedDeleteSoftDeletes() {
        module.handleCallback(context(null, Set.of(NotesModule.DELETE, NotesModule.VIEW), "cb", 10L), "note:delok:5");

        verify(telegram).answerCallback("cb", "Delete", false);
        verify(noteService).softDelete(1L, 5L);
    }

    @Test
    void deniedServiceCallStaysAFeatureMessage() {
        ConversationStateEntity state = state(ConversationStep.NOTES_WAITING_CONTENT);
        when(noteService.create(1L, 1L, "secret"))
                .thenThrow(new AccessDeniedException(AccessMessages.actionUnavailable()));

        module.handleMessage(context(state, Set.of(NotesModule.CREATE), null, null), "secret");

        verify(telegram).sendMessage(1L, "🚧 That action is currently unavailable.");
        verify(conversations, never()).clear(any());
    }

    private static ConversationStateEntity state(ConversationStep step) {
        ConversationStateEntity state = new ConversationStateEntity(1L, 1L, step, Instant.parse("2026-10-24T01:00:00Z"));
        state.setModuleKey("notes");
        return state;
    }

    private static BotContext context(ConversationStateEntity state, Set<String> allowed, String callbackId, Long messageId) {
        return new BotContext(1L, 1L, callbackId, messageId, state, "notes", null, allowed);
    }

    private static List<String> labels(List<List<Map<String, String>>> keyboard) {
        return keyboard.stream().flatMap(List::stream).map(button -> button.get("text")).toList();
    }
}
