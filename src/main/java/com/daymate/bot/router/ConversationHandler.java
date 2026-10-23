package com.daymate.bot.router;

import com.daymate.bot.domain.ConversationStep;
import com.daymate.bot.domain.Recurrence;
import com.daymate.bot.domain.TaskCategory;
import com.daymate.bot.domain.TaskDue;
import com.daymate.bot.domain.TaskPriority;
import com.daymate.bot.entity.ConversationStateEntity;
import com.daymate.bot.entity.TaskEntity;
import com.daymate.bot.save.TaskSaveService;
import com.daymate.bot.service.ConversationService;
import com.daymate.bot.service.DueTimeParser;
import com.daymate.bot.service.PlannerFormatter;
import com.daymate.bot.telegram.InlineKeyboards;
import com.daymate.bot.telegram.TelegramApiClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ConversationHandler {
    private final ConversationService conversations;
    private final TaskSaveService tasks;
    private final DueTimeParser dueTimeParser;
    private final PlannerFormatter formatter;
    private final TelegramApiClient telegram;

    public ConversationHandler(ConversationService conversations, TaskSaveService tasks,
                               DueTimeParser dueTimeParser, PlannerFormatter formatter,
                               TelegramApiClient telegram) {
        this.conversations = conversations;
        this.tasks = tasks;
        this.dueTimeParser = dueTimeParser;
        this.formatter = formatter;
        this.telegram = telegram;
    }

    public void startAdd(Long chatId, Long userId) {
        conversations.start(chatId, userId);
        telegram.sendMessage(chatId, formatter.addTitlePrompt());
    }

    public void handleText(ConversationStateEntity state, Long chatId, String text) {
        switch (state.getStep()) {
            case TITLE -> acceptTitle(state, chatId, text);
            case CATEGORY -> acceptCategory(state, chatId, text);
            case PRIORITY -> acceptPriority(state, chatId, text);
            case DUE -> acceptDue(state, chatId, text);
            case RECURRENCE -> acceptRecurrence(state, chatId, text);
        }
    }

    public boolean handleFlowCallback(ConversationStateEntity state, Long chatId, String data) {
        if (data.startsWith("flow:cat:")) {
            acceptCategory(state, chatId, data);
            return true;
        }
        if (data.startsWith("flow:pri:")) {
            acceptPriority(state, chatId, data);
            return true;
        }
        if (data.startsWith("flow:recur:")) {
            acceptRecurrence(state, chatId, data);
            return true;
        }
        return false;
    }

    private void acceptTitle(ConversationStateEntity state, Long chatId, String text) {
        String title = text == null ? "" : text.trim();
        if (title.length() < 2) {
            telegram.sendMessage(chatId, formatter.titleTooShort());
            return;
        }
        state.setTitle(title);
        state.setStep(ConversationStep.CATEGORY);
        conversations.save(state);
        telegram.sendMessage(chatId, formatter.addCategoryPrompt(title), InlineKeyboards.categories());
    }

    private void acceptCategory(ConversationStateEntity state, Long chatId, String raw) {
        Optional<TaskCategory> category = TaskCategory.parse(raw);
        if (category.isEmpty()) {
            telegram.sendMessage(chatId, formatter.chooseCategory(), InlineKeyboards.categories());
            return;
        }
        state.setCategory(category.get().name());
        state.setStep(ConversationStep.PRIORITY);
        conversations.save(state);
        telegram.sendMessage(chatId, formatter.addPriorityPrompt(category.get().label()), InlineKeyboards.priorities());
    }

    private void acceptPriority(ConversationStateEntity state, Long chatId, String raw) {
        Optional<TaskPriority> priority = TaskPriority.parse(raw);
        if (priority.isEmpty()) {
            telegram.sendMessage(chatId, formatter.choosePriority(), InlineKeyboards.priorities());
            return;
        }
        state.setPriority(priority.get().name());
        state.setStep(ConversationStep.DUE);
        conversations.save(state);
        telegram.sendMessage(chatId, formatter.addDuePrompt(priority.get().label()));
    }

    private void acceptDue(ConversationStateEntity state, Long chatId, String raw) {
        Optional<TaskDue> due = dueTimeParser.parse(raw, tasks.todayDate());
        if (due.isEmpty()) {
            telegram.sendMessage(chatId, formatter.invalidDue());
            return;
        }
        state.setDueDate(due.get().date());
        state.setDueTime(due.get().time());
        state.setStep(ConversationStep.RECURRENCE);
        conversations.save(state);
        telegram.sendMessage(chatId, formatter.addRecurrencePrompt(), InlineKeyboards.recurrences());
    }

    private void acceptRecurrence(ConversationStateEntity state, Long chatId, String raw) {
        Optional<Recurrence> recurrence = Recurrence.parse(raw);
        if (recurrence.isEmpty()) {
            telegram.sendMessage(chatId, formatter.chooseRecurrence(), InlineKeyboards.recurrences());
            return;
        }
        TaskEntity saved = tasks.create(
                state.getUserId(),
                chatId,
                state.getTitle(),
                TaskCategory.valueOf(state.getCategory()),
                TaskPriority.valueOf(state.getPriority()),
                new TaskDue(state.getDueDate(), state.getDueTime()),
                recurrence.get());
        conversations.clear(chatId);
        telegram.sendMessage(chatId, formatter.taskSaved(saved), InlineKeyboards.afterSave());
    }
}
