package com.daymate.bot.router;

import com.daymate.bot.config.BotProperties;
import com.daymate.bot.domain.Recurrence;
import com.daymate.bot.domain.TaskCategory;
import com.daymate.bot.domain.TaskPriority;
import com.daymate.bot.dto.request.TelegramUpdateRequest;
import com.daymate.bot.entity.TaskEntity;
import com.daymate.bot.save.TaskSaveService;
import com.daymate.bot.service.ConversationService;
import com.daymate.bot.service.PlannerFormatter;
import com.daymate.bot.telegram.TelegramApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private CommandRouter router;

    @BeforeEach
    void setUp() {
        BotProperties properties = new BotProperties();
        properties.setAllowedUserIds("");
        router = new CommandRouter(telegram, tasks, conversations, conversationHandler, otHandler,
                new PlannerFormatter(), properties);
    }

    @Test
    void startSendsWelcomeCopy() {
        router.process(message(1L, "/start"));
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(telegram).sendMessage(eq(1L), text.capture());
        assertThat(text.getValue()).contains("Welcome to DayMate — Your Daily Planner Bot");
        verify(conversations).clear(1L);
    }

    @Test
    void todaySendsAgendaWithButtons() {
        LocalDate date = LocalDate.of(2026, 10, 10);
        TaskEntity task = TaskEntity.create(
                "T-001", 1L, 1L, "Review Q4 budget",
                TaskCategory.WORK, TaskPriority.HIGH,
                date, LocalTime.of(10, 0), Recurrence.NONE,
                Instant.parse("2026-10-10T02:00:00Z"));
        when(tasks.today(1L)).thenReturn(new TaskSaveService.Agenda(date, List.of(task), List.of()));

        router.process(message(1L, "/today"));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(telegram).sendMessage(eq(1L), text.capture(), any());
        assertThat(text.getValue()).contains("Review Q4 budget");
        assertThat(text.getValue()).contains("Progress: 0/1 done ✅");
    }

    @Test
    void addStartsConversation() {
        router.process(message(1L, "/add"));
        verify(conversationHandler).startAdd(1L, 1L);
    }

    @Test
    void completeCallbackSendsCelebration() {
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
        when(conversations.get(1L)).thenReturn(Optional.empty());
        router.process(message(1L, "hello"));
        verify(conversationHandler, never()).handleText(any(), any(), any());
        verify(telegram).sendMessage(eq(1L), eq("I don't know that command. Send /help to see what I can do."));
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
