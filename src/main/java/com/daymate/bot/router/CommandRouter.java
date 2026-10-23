package com.daymate.bot.router;

import com.daymate.bot.config.BotProperties;
import com.daymate.bot.domain.Language;
import com.daymate.bot.dto.request.TelegramUpdateRequest;
import com.daymate.bot.i18n.LanguageContext;
import com.daymate.bot.entity.TaskEntity;
import com.daymate.bot.save.TaskSaveService;
import com.daymate.bot.service.ConversationService;
import com.daymate.bot.service.LanguageService;
import com.daymate.bot.service.PlannerFormatter;
import com.daymate.bot.telegram.InlineKeyboards;
import com.daymate.bot.telegram.TelegramApiClient;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CommandRouter {
    private final TelegramApiClient telegram;
    private final TaskSaveService tasks;
    private final ConversationService conversations;
    private final ConversationHandler conversationHandler;
    private final OtHandler otHandler;
    private final LanguageService languages;
    private final PlannerFormatter formatter;
    private final Set<Long> allowedUserIds;

    public CommandRouter(TelegramApiClient telegram, TaskSaveService tasks, ConversationService conversations,
                         ConversationHandler conversationHandler, OtHandler otHandler, LanguageService languages,
                         PlannerFormatter formatter, BotProperties properties) {
        this.telegram = telegram;
        this.tasks = tasks;
        this.conversations = conversations;
        this.conversationHandler = conversationHandler;
        this.otHandler = otHandler;
        this.languages = languages;
        this.formatter = formatter;
        this.allowedUserIds = Arrays.stream(properties.getAllowedUserIds().split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    try {
                        return Long.valueOf(value);
                    } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException(
                                "TELEGRAM_ALLOWED_USER_IDS must contain Telegram numeric user IDs.");
                    }
                })
                .collect(Collectors.toUnmodifiableSet());
    }

    public void process(TelegramUpdateRequest update) {
        if (update == null) {
            return;
        }
        if (update.callbackQuery() != null) {
            processCallback(update.callbackQuery());
        } else if (update.message() != null) {
            processMessage(update.message());
        }
    }

    private void processMessage(TelegramUpdateRequest.Message message) {
        if (message.chat() == null || message.from() == null || message.text() == null) {
            return;
        }
        Long chatId = message.chat().id();
        Long userId = message.from().id();
        LanguageContext.set(languages.get(userId));
        String text = message.text().trim();
        if (text.equalsIgnoreCase("/id") || text.toLowerCase(Locale.ROOT).startsWith("/id@")) {
            telegram.sendMessage(chatId, formatter.userId(userId));
            return;
        }
        if (!isAllowed(userId)) {
            telegram.sendMessage(chatId, formatter.notAllowed(userId));
            return;
        }
        if (isCommand(text)) {
            conversations.clear(chatId);
            handleCommand(chatId, userId, text);
            return;
        }
        conversations.get(chatId).ifPresentOrElse(
                state -> {
                    if (state.getStep().isOtSetup()) {
                        otHandler.handleText(state, chatId, text);
                    } else {
                        conversationHandler.handleText(state, chatId, text);
                    }
                },
                () -> telegram.sendMessage(chatId, formatter.unknownCommand()));
    }

    private void processCallback(TelegramUpdateRequest.CallbackQuery callback) {
        Long userId = callback.from() == null ? null : callback.from().id();
        Long chatId = callback.message() == null || callback.message().chat() == null
                ? null : callback.message().chat().id();
        Long messageId = callback.message() == null ? null : callback.message().messageId();
        if (callback.id() == null) {
            return;
        }
        LanguageContext.set(languages.get(userId));
        if (userId == null || !isAllowed(userId)) {
            telegram.answerCallback(callback.id(), "You are not authorized to use this bot.", true);
            return;
        }
        if (chatId == null || callback.data() == null) {
            telegram.answerCallback(callback.id(), "This button is no longer available.", true);
            return;
        }
        String data = callback.data();
        try {
            if (data.startsWith("lang:")) {
                Language chosen = Language.from(data.substring("lang:".length()));
                languages.set(userId, chosen);
                LanguageContext.set(chosen);
                telegram.answerCallback(callback.id(), "OK", false);
                telegram.sendMessage(chatId, formatter.languageSaved());
                return;
            }
            if (data.startsWith("ot:")) {
                if (!otHandler.handleCallback(callback.id(), chatId, userId, data)) {
                    telegram.answerCallback(callback.id(), "This button is no longer available.", true);
                }
                return;
            }
            if (data.startsWith("flow:")) {
                var state = conversations.get(chatId);
                if (state.isEmpty()) {
                    telegram.answerCallback(callback.id(), "That add-task step expired. Send /add to start again.", true);
                    return;
                }
                telegram.answerCallback(callback.id(), "Saved", false);
                conversationHandler.handleFlowCallback(state.get(), chatId, data);
                return;
            }
            dispatchCallback(callback.id(), chatId, messageId, userId, data);
        } catch (IllegalArgumentException exception) {
            telegram.answerCallback(callback.id(), exception.getMessage(), true);
        }
    }

    private void dispatchCallback(String callbackId, Long chatId, Long messageId, Long userId, String data) {
        if (data.equals("nav:today") || data.equals("nav:refresh")) {
            telegram.answerCallback(callbackId, "Today", false);
            sendToday(chatId, userId, messageId, data.equals("nav:refresh"));
            return;
        }
        if (data.equals("nav:add")) {
            telegram.answerCallback(callbackId, "Add a task", false);
            conversationHandler.startAdd(chatId, userId);
            return;
        }
        if (data.equals("nav:all_done")) {
            List<TaskEntity> completed = tasks.completeAllOpenToday(userId);
            telegram.answerCallback(callbackId, "All done", false);
            reply(chatId, messageId, formatter.allDone(completed.size()), InlineKeyboards.emptyToday());
            return;
        }
        if (data.equals("nav:list")) {
            telegram.answerCallback(callbackId, "List", false);
            sendList(chatId, userId);
            return;
        }
        if (data.startsWith("t:")) {
            handleTaskCallback(callbackId, chatId, messageId, userId, data);
            return;
        }
        telegram.answerCallback(callbackId, "This button is no longer available.", true);
    }

    private void handleTaskCallback(String callbackId, Long chatId, Long messageId, Long userId, String data) {
        String[] parts = data.split(":", 3);
        if (parts.length < 3) {
            telegram.answerCallback(callbackId, "This button is no longer available.", true);
            return;
        }
        String action = parts[1];
        String publicId = parts[2];
        switch (action) {
            case "start" -> {
                tasks.start(userId, publicId);
                telegram.answerCallback(callbackId, "In progress", false);
                sendToday(chatId, userId, messageId, true);
            }
            case "complete", "pick" -> {
                TaskEntity task = tasks.complete(userId, publicId);
                TaskSaveService.Agenda agenda = tasks.today(userId);
                telegram.answerCallback(callbackId, "Completed", false);
                telegram.sendMessage(chatId, formatter.taskCompleted(task, agenda, tasks.zoneId()));
                sendToday(chatId, userId, messageId, true);
            }
            case "snooze" -> {
                TaskEntity task = tasks.snooze(userId, publicId);
                telegram.answerCallback(callbackId, "Snoozed 1 hour", false);
                sendToday(chatId, userId, messageId, true);
                telegram.sendMessage(chatId, formatter.taskSnoozed(task));
            }
            case "delete" -> {
                tasks.delete(userId, publicId);
                telegram.answerCallback(callbackId, "Deleted", false);
                sendToday(chatId, userId, messageId, true);
            }
            default -> telegram.answerCallback(callbackId, "This button is no longer available.", true);
        }
    }

    private void handleCommand(Long chatId, Long userId, String text) {
        String[] parts = text.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        int at = command.indexOf('@');
        if (at > 0) {
            command = command.substring(0, at);
        }
        String argument = parts.length > 1 ? parts[1].trim() : "";
        switch (command) {
            case "/start", "/menu" -> telegram.sendMessage(chatId, formatter.welcome());
            case "/help" -> telegram.sendMessage(chatId, formatter.help());
            case "/language", "/lang" -> telegram.sendMessage(chatId, formatter.languagePrompt(), languageKeyboard());
            case "/ot", "/ot-record" -> otHandler.startRecord(chatId, userId, argument);
            case "/otlist", "/ot-list" -> otHandler.list(chatId, userId);
            case "/ot-history", "/othistory", "/ot_history" -> otHandler.history(chatId, userId, argument);
            case "/del", "/delete" -> otHandler.deleteCommand(chatId, userId, argument);
            case "/salary" -> otHandler.startSalary(chatId, userId);
            case "/upcoming_salary", "/upcomingsalary", "/upcoming-salary" -> otHandler.upcoming(chatId, userId);
            default -> telegram.sendMessage(chatId, formatter.unknownCommand());
        }
    }

    private void handleDone(Long chatId, Long userId, String argument) {
        if (!argument.isBlank()) {
            TaskEntity task = tasks.complete(userId, argument);
            TaskSaveService.Agenda agenda = tasks.today(userId);
            telegram.sendMessage(chatId, formatter.taskCompleted(task, agenda, tasks.zoneId()));
            return;
        }
        List<TaskEntity> open = tasks.today(userId).remaining();
        if (open.isEmpty()) {
            telegram.sendMessage(chatId, formatter.donePickerEmpty());
            return;
        }
        telegram.sendMessage(chatId, formatter.donePicker(), InlineKeyboards.donePicker(open));
    }

    private void sendToday(Long chatId, Long userId, Long messageId, boolean edit) {
        TaskSaveService.Agenda agenda = tasks.today(userId);
        var keyboard = agenda.remaining().isEmpty()
                ? InlineKeyboards.emptyToday()
                : InlineKeyboards.today(agenda.remaining());
        reply(chatId, edit ? messageId : null, formatter.today(agenda), keyboard);
    }

    private void sendList(Long chatId, Long userId) {
        TaskSaveService.Agenda agenda = tasks.today(userId);
        telegram.sendMessage(chatId, formatter.list(tasks.pending(userId), agenda.completedToday()));
    }

    private void reply(Long chatId, Long messageId, String text, java.util.List<java.util.List<java.util.Map<String, String>>> keyboard) {
        if (messageId != null) {
            telegram.editMessage(chatId, messageId, text, keyboard);
        } else {
            telegram.sendMessage(chatId, text, keyboard);
        }
    }

    private static List<List<Map<String, String>>> languageKeyboard() {
        return List.of(List.of(
                Map.of("text", "English", "callback_data", "lang:en"),
                Map.of("text", "ខ្មែរ", "callback_data", "lang:km")));
    }

    private boolean isAllowed(Long userId) {
        return allowedUserIds.isEmpty() || allowedUserIds.contains(userId);
    }

    private static boolean isCommand(String text) {
        return text.startsWith("/");
    }
}
