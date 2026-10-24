package com.daymate.bot.router;

import com.daymate.bot.config.BotProperties;
import com.daymate.bot.core.access.AccessDecision;
import com.daymate.bot.core.access.FeatureAccessGuard;
import com.daymate.bot.core.module.ModuleRegistry;
import com.daymate.bot.core.security.UserRateLimiter;
import com.daymate.bot.domain.ConversationStep;
import com.daymate.bot.domain.Language;
import com.daymate.bot.domain.Recurrence;
import com.daymate.bot.domain.TaskCategory;
import com.daymate.bot.domain.TaskPriority;
import com.daymate.bot.dto.request.TelegramUpdateRequest;
import com.daymate.bot.entity.ConversationStateEntity;
import com.daymate.bot.entity.TaskEntity;
import com.daymate.bot.entity.NoteEntity;
import com.daymate.bot.modules.developer.DeveloperModule;
import com.daymate.bot.modules.finance.FinanceModule;
import com.daymate.bot.modules.notes.NotesModule;
import com.daymate.bot.modules.overtime.OvertimeModule;
import com.daymate.bot.modules.task.TasksModule;
import com.daymate.bot.notes.NoteFixtures;
import com.daymate.bot.notes.NoteFormatter;
import com.daymate.bot.notes.NoteService;
import com.daymate.bot.notes.NoteTimes;
import com.daymate.bot.save.TaskSaveService;
import com.daymate.bot.service.ConversationLookup;
import com.daymate.bot.service.ConversationService;
import com.daymate.bot.service.LanguageService;
import com.daymate.bot.service.PlannerFormatter;
import com.daymate.bot.telegram.TelegramApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandRouterTest {
    @Mock
    private TelegramApiClient telegram;
    @Mock
    private TaskSaveService tasks;
    @Mock
    private ConversationService conversations;
    @Mock
    private ConversationHandler conversationHandler;
    @Mock
    private OtHandler otHandler;
    @Mock
    private LanguageService languages;
    @Mock
    private FeatureAccessGuard guard;
    @Mock
    private UserRateLimiter rateLimiter;
    @Mock
    private NoteService noteService;

    private CommandRouter router;

    @BeforeEach
    void setUp() {
        BotProperties properties = new BotProperties();
        properties.setAllowedUserIds("");
        lenient().when(languages.get(any())).thenReturn(Language.EN);
        lenient().when(rateLimiter.allow(any())).thenReturn(true);
        lenient().when(conversations.read(any())).thenReturn(ConversationLookup.none());
        lenient().when(guard.isModuleEnabled(any())).thenReturn(true);
        lenient().when(guard.decide(any(), any(), nullable(String.class))).thenReturn(AccessDecision.MODULE_OFF);
        lenient().when(guard.decide(any(), eq("overtime"), nullable(String.class))).thenReturn(AccessDecision.ALLOW);
        lenient().when(guard.visible(any(), any())).thenAnswer(invocation ->
                invocation.getArgument(1) instanceof com.daymate.bot.core.module.BotModule module
                        && "overtime".equals(module.moduleId()));
        PlannerFormatter formatter = new PlannerFormatter();
        ModuleRegistry registry = new ModuleRegistry(List.of(
                new OvertimeModule(otHandler, telegram),
                new TasksModule(tasks, conversationHandler, conversations, formatter, telegram),
                new FinanceModule(telegram),
                new NotesModule(noteService, conversations, telegram,
                        new NoteFormatter(ZoneId.of("Asia/Phnom_Penh")),
                        new NoteTimes(Clock.systemUTC(), ZoneId.of("Asia/Phnom_Penh"))),
                new DeveloperModule(telegram)));
        router = new CommandRouter(telegram, conversations, languages, formatter, registry, guard, rateLimiter, properties);
    }

    @Test
    void startSendsWelcomeCopy() {
        router.process(message(1L, "/start"));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Map<String, String>>>> keyboard = ArgumentCaptor.forClass(List.class);
        verify(telegram).sendMessage(eq(1L), text.capture(), keyboard.capture());
        assertThat(text.getValue()).contains("Welcome to DayMate");
        assertThat(text.getValue()).contains("/upcoming_salary");
        assertThat(keyboard.getValue()).hasSize(1);
        assertThat(keyboard.getValue().get(0).get(0))
                .containsEntry("text", "Overtime")
                .containsEntry("callback_data", "menu:overtime");
        verify(conversations).clear(1L);
    }

    @Test
    void menuListsNotesCommandsAndActions() {
        lenient().when(guard.visible(any(), any())).thenAnswer(invocation ->
                invocation.getArgument(1) instanceof com.daymate.bot.core.module.BotModule module
                        && ("overtime".equals(module.moduleId()) || "notes".equals(module.moduleId())));
        when(guard.decide(any(), eq("notes"), nullable(String.class))).thenReturn(AccessDecision.ALLOW);

        router.process(message(1L, "/menu"));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Map<String, String>>>> keyboard = ArgumentCaptor.forClass(List.class);
        verify(telegram).sendMessage(eq(1L), text.capture(), keyboard.capture());
        assertThat(text.getValue()).contains("/note — Save a note");
        assertThat(text.getValue()).contains("/notes — Notes menu");
        assertThat(text.getValue()).contains("/my-notes — My saved notes");
        assertThat(keyboard.getValue())
                .extracting(row -> row.get(0).get("text"))
                .containsExactly(
                        "Overtime",
                        "📌 Notes",
                        "➕ New Note",
                        "📋 My Notes",
                        "🔍 Search Notes",
                        "📌 Pinned Notes");
        assertThat(keyboard.getValue().get(2).get(0)).containsEntry("callback_data", "note:new");
        assertThat(keyboard.getValue().get(3).get(0)).containsEntry("callback_data", "note:list:0");
    }

    @Test
    void removedTaskCommandsAreUnknown() {
        router.process(message(1L, "/today"));
        verify(telegram).sendMessage(eq(1L), eq("I don't know that command. Send /help to see what I can do."));
        verify(conversationHandler, never()).startAdd(any(), any());
    }

    @Test
    void upcomingSalaryIsRouted() {
        router.process(message(1L, "/upcoming_salary"));
        verify(otHandler).upcoming(1L, 1L);
    }

    @Test
    void completeCallbackSendsCelebration() {
        when(guard.decide(1L, "tasks", "tasks.complete")).thenReturn(AccessDecision.ALLOW);
        LocalDate date = LocalDate.of(2026, 10, 10);
        TaskEntity task = TaskEntity.create(
                "T-002", 1L, 1L, "Call dentist",
                TaskCategory.PERSONAL, TaskPriority.MEDIUM,
                date, LocalTime.of(14, 0), Recurrence.NONE,
                Instant.parse("2026-10-10T02:00:00Z"));
        task.markCompleted(Instant.parse("2026-10-10T07:15:00Z"));
        when(tasks.complete(1L, "T-002")).thenReturn(task);
        when(tasks.today(1L)).thenReturn(new TaskSaveService.Agenda(date, List.of(), List.of(task)));
        when(tasks.zoneId()).thenReturn(ZoneId.of("Asia/Bangkok"));

        router.process(callback(1L, 10L, "t:complete:T-002"));

        verify(telegram).answerCallback("cb-1", "Completed", false);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(telegram).sendMessage(eq(1L), text.capture());
        assertThat(text.getValue()).contains("Task completed!");
        assertThat(text.getValue()).contains("T-002  Call dentist");
    }

    @Test
    void nonCommandDuringIdleShowsHelpHint() {
        router.process(message(1L, "hello"));
        verify(conversationHandler, never()).handleText(any(), any(), any());
        verify(otHandler, never()).handleText(any(), any(), any());
        verify(telegram).sendMessage(eq(1L), eq("I don't know that command. Send /help to see what I can do."));
    }

    @Test
    void disabledOvertimeIsNotCalled() {
        when(guard.decide(any(), eq("overtime"), nullable(String.class))).thenReturn(AccessDecision.MODULE_OFF);

        router.process(message(1L, "/ot"));

        verify(telegram).sendMessage(eq(1L), eq("🚧 Overtime is currently unavailable."));
        verify(otHandler, never()).startRecord(any(), any(), any());
    }

    @Test
    void unknownCallbackDoesNotReachAModule() {
        router.process(callback(1L, 10L, "evil:run"));

        verify(telegram).answerCallback("cb-1", "This button is no longer available.", true);
        verify(otHandler, never()).handleCallback(any(), any(), any(), any());
        verify(tasks, never()).complete(any(), any());
        verify(tasks, never()).today(any());
    }

    @Test
    void editCallbacksStillReachOvertime() {
        when(otHandler.handleCallback(eq("cb-1"), eq(1L), eq(1L), eq("ot:edittime:202610:1:0730:1630:W"))).thenReturn(true);
        when(otHandler.handleCallback(eq("cb-1"), eq(1L), eq(1L), eq("ot:edit:2026-10:1"))).thenReturn(true);

        router.process(callback(1L, 10L, "ot:edittime:202610:1:0730:1630:W"));
        router.process(callback(1L, 10L, "ot:edit:2026-10:1"));

        verify(otHandler).handleCallback("cb-1", 1L, 1L, "ot:edittime:202610:1:0730:1630:W");
        verify(otHandler).handleCallback("cb-1", 1L, 1L, "ot:edit:2026-10:1");
        verify(telegram, never()).answerCallback(any(), any(), anyBoolean());
    }

    @Test
    void oldTaskButtonSaysTasksAreUnavailable() {
        router.process(callback(1L, 10L, "nav:today"));

        verify(telegram).answerCallback("cb-1", "🚧 Tasks is currently unavailable.", true);
        verify(tasks, never()).today(any());
    }

    @Test
    void directFinanceCommandIsDenied() {
        router.process(message(1L, "/finance"));
        verify(telegram).sendMessage(eq(1L), eq("🚧 Finance is currently unavailable."));
    }

    @Test
    void expiredStepTellsTheUserToStartAgain() {
        when(conversations.read(1L)).thenReturn(ConversationLookup.expired());

        router.process(message(1L, "08:30-17:30"));

        verify(telegram).sendMessage(eq(1L), eq("That step expired. Send /menu to start again."));
        verify(otHandler, never()).handleText(any(), any(), any());
        verify(conversations, never()).clear(any());
    }

    @Test
    void anotherUsersConversationIsLeftAlone() {
        ConversationStateEntity state = new ConversationStateEntity(
                1L, 99L, ConversationStep.OT_TIME, Instant.parse("2026-10-24T02:00:00Z"));
        when(conversations.read(1L)).thenReturn(ConversationLookup.found(state));

        router.process(message(1L, "08:30-17:30"));

        verify(telegram).sendMessage(eq(1L), eq("I don't know that command. Send /help to see what I can do."));
        verify(otHandler, never()).handleText(any(), any(), any());
        verify(conversations, never()).clear(any());
    }

    @Test
    void longMessageDoesNotClearState() {
        router.process(message(1L, "x".repeat(1001)));

        verify(telegram).sendMessage(eq(1L), eq("Send a shorter message."));
        verify(conversations, never()).clear(any());
        verify(otHandler, never()).handleText(any(), any(), any());
    }

    @Test
    void rateLimitDoesNotCallTheModule() {
        when(rateLimiter.allow(1L)).thenReturn(false);

        router.process(message(1L, "/ot"));

        verify(telegram).sendMessage(eq(1L), eq("Slow down a moment."));
        verify(conversations, never()).clear(any());
        verify(otHandler, never()).startRecord(any(), any(), any());
    }

    @Test
    void cancelStillClearsWhenOvertimeIsSwitchedOff() {
        when(guard.isModuleEnabled("overtime")).thenReturn(false);
        when(guard.decide(any(), eq("overtime"), nullable(String.class))).thenReturn(AccessDecision.MODULE_OFF);
        when(otHandler.handleCallback("cb-1", 1L, 1L, "ot:cancel")).thenReturn(true);

        router.process(callback(1L, 10L, "ot:cancel"));

        verify(otHandler).handleCallback("cb-1", 1L, 1L, "ot:cancel");
    }

    @Test
    void notesCommandIsUnavailableUntilAllowed() {
        router.process(message(1L, "/note remember"));

        verify(telegram).sendMessage(eq(1L), eq("🚧 📌 Notes is currently unavailable."));
        verify(noteService, never()).create(any(), any(), any());
    }

    @Test
    void saveCommandIsUnknown() {
        router.process(message(1L, "/save"));

        verify(telegram).sendMessage(eq(1L), eq("I don't know that command. Send /help to see what I can do."));
    }

    @Test
    void waitingNoteTextCreatesOneNote() {
        when(guard.decide(any(), eq("notes"), nullable(String.class))).thenReturn(AccessDecision.ALLOW);
        ConversationStateEntity state = new ConversationStateEntity(
                1L, 1L, ConversationStep.NOTES_WAITING_CONTENT, Instant.parse("2026-10-24T02:00:00Z"));
        state.setModuleKey("notes");
        when(conversations.read(1L)).thenReturn(ConversationLookup.found(state));
        NoteEntity note = new NoteEntity(
                1L, "Remember this", "Remember this", false, Instant.parse("2026-10-24T02:00:00Z"));
        NoteFixtures.setId(note, 4L);
        when(noteService.create(1L, 1L, "Remember this")).thenReturn(note);

        router.process(message(1L, "Remember this"));

        verify(noteService).create(1L, 1L, "Remember this");
        verify(conversations).clear(1L);
    }

    @Test
    void blankNoteTextKeepsTheConversation() {
        when(guard.decide(any(), eq("notes"), nullable(String.class))).thenReturn(AccessDecision.ALLOW);
        ConversationStateEntity state = new ConversationStateEntity(
                1L, 1L, ConversationStep.NOTES_WAITING_CONTENT, Instant.parse("2026-10-24T02:00:00Z"));
        state.setModuleKey("notes");
        when(conversations.read(1L)).thenReturn(ConversationLookup.found(state));
        when(noteService.create(1L, 1L, "")).thenThrow(new IllegalArgumentException("Send the note text."));

        router.process(message(1L, "   "));

        verify(telegram).sendMessage(eq(1L), eq("Send the note text."));
        verify(conversations, never()).clear(any());
    }

    private static TelegramUpdateRequest message(long userId, String text) {
        return new TelegramUpdateRequest(
                1L,
                new TelegramUpdateRequest.Message(
                        10L,
                        new TelegramUpdateRequest.Chat(userId),
                        new TelegramUpdateRequest.User(userId, "Alex"),
                        text),
                null);
    }

    private static TelegramUpdateRequest callback(long userId, long messageId, String data) {
        TelegramUpdateRequest.Message message = new TelegramUpdateRequest.Message(
                messageId,
                new TelegramUpdateRequest.Chat(userId),
                new TelegramUpdateRequest.User(userId, "Alex"),
                null);
        return new TelegramUpdateRequest(
                2L,
                null,
                new TelegramUpdateRequest.CallbackQuery(
                        "cb-1",
                        new TelegramUpdateRequest.User(userId, "Alex"),
                        data,
                        message));
    }
}
