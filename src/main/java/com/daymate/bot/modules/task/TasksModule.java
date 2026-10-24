package com.daymate.bot.modules.task;

import com.daymate.bot.core.module.BotContext;
import com.daymate.bot.core.module.BotModule;
import com.daymate.bot.core.module.FeatureDeclaration;
import com.daymate.bot.core.module.MenuAction;
import com.daymate.bot.entity.ConversationStateEntity;
import com.daymate.bot.entity.TaskEntity;
import com.daymate.bot.i18n.Texts;
import com.daymate.bot.router.ConversationHandler;
import com.daymate.bot.save.TaskSaveService;
import com.daymate.bot.service.ConversationService;
import com.daymate.bot.service.PlannerFormatter;
import com.daymate.bot.telegram.InlineKeyboards;
import com.daymate.bot.telegram.TelegramApiClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TasksModule implements BotModule {
    public static final String ID = "tasks";
    public static final String TODAY = "tasks.today";
    public static final String LIST = "tasks.list";
    public static final String CREATE = "tasks.create";
    public static final String START = "tasks.start";
    public static final String COMPLETE = "tasks.complete";
    public static final String SNOOZE = "tasks.snooze";
    public static final String DELETE = "tasks.delete";
    public static final String DIGEST = "tasks.digest";

    private final TaskSaveService tasks;
    private final ConversationHandler conversations;
    private final ConversationService states;
    private final PlannerFormatter formatter;
    private final TelegramApiClient telegram;

    public TasksModule(TaskSaveService tasks, ConversationHandler conversations, ConversationService states,
                       PlannerFormatter formatter, TelegramApiClient telegram) {
        this.tasks = tasks;
        this.conversations = conversations;
        this.states = states;
        this.formatter = formatter;
        this.telegram = telegram;
    }

    @Override
    public String moduleId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Tasks";
    }

    @Override
    public String description() {
        return "Tasks and reminders.";
    }

    @Override
    public int displayOrder() {
        return 20;
    }

    @Override
    public boolean enabledByDefault() {
        return false;
    }

    @Override
    public List<String> commands() {
        return List.of();
    }

    @Override
    public List<String> callbackPrefixes() {
        return List.of("nav", "t", "flow");
    }

    @Override
    public List<FeatureDeclaration> features() {
        return List.of(
                closed(TODAY), closed(LIST), closed(CREATE), closed(START),
                closed(COMPLETE), closed(SNOOZE), closed(DELETE), closed(DIGEST));
    }

    @Override
    public List<MenuAction> menuActions() {
        return List.of(
                new MenuAction("today", "Today", "ថ្ងៃនេះ", TODAY),
                new MenuAction("add", "Add a task", "បន្ថែមកិច្ចការ", CREATE),
                new MenuAction("list", "Task list", "បញ្ជីកិច្ចការ", LIST));
    }

    @Override
    public String featureForCommand(String command) {
        return null;
    }

    @Override
    public String featureForCallback(String data) {
        if (data == null) {
            return TODAY;
        }
        if (data.equals("nav:today") || data.equals("nav:refresh")) {
            return TODAY;
        }
        if (data.equals("nav:add") || data.startsWith("flow:")) {
            return CREATE;
        }
        if (data.equals("nav:list")) {
            return LIST;
        }
        if (data.equals("nav:all_done")) {
            return COMPLETE;
        }
        if (data.startsWith("t:start:")) {
            return START;
        }
        if (data.startsWith("t:complete:") || data.startsWith("t:pick:")) {
            return COMPLETE;
        }
        if (data.startsWith("t:snooze:")) {
            return SNOOZE;
        }
        if (data.startsWith("t:delete:")) {
            return DELETE;
        }
        return TODAY;
    }

    @Override
    public String featureForState(ConversationStateEntity state) {
        return CREATE;
    }

    @Override
    public String commandForMenuAction(String action) {
        return null;
    }

    @Override
    public void showMenu(BotContext context) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (MenuAction action : menuActions()) {
            if (!context.allows(action.featureKey())) {
                continue;
            }
            String data = switch (action.action()) {
                case "today" -> "nav:today";
                case "add" -> "nav:add";
                case "list" -> "nav:list";
                default -> "menu:home";
            };
            rows.add(List.of(button(action.label(), data)));
        }
        rows.add(List.of(button(Texts.t("Main menu", "ម៉ឺនុយ"), "menu:home")));
        telegram.sendMessage(context.chatId(), Texts.t("Tasks", "កិច្ចការ"), rows);
    }

    @Override
    public void handleCommand(BotContext context, String command, String arguments) {
        telegram.sendMessage(context.chatId(), formatter.unknownCommand());
    }

    @Override
    public boolean handleCallback(BotContext context, String data) {
        String callbackId = context.callbackId();
        Long chatId = context.chatId();
        Long messageId = context.messageId();
        Long userId = context.userId();
        if (data.equals("nav:today") || data.equals("nav:refresh")) {
            telegram.answerCallback(callbackId, "Today", false);
            sendToday(chatId, userId, messageId, data.equals("nav:refresh"));
            return true;
        }
        if (data.equals("nav:add")) {
            telegram.answerCallback(callbackId, "Add a task", false);
            conversations.startAdd(chatId, userId);
            return true;
        }
        if (data.equals("nav:all_done")) {
            List<TaskEntity> completed = tasks.completeAllOpenToday(userId);
            telegram.answerCallback(callbackId, "All done", false);
            reply(chatId, messageId, formatter.allDone(completed.size()), InlineKeyboards.emptyToday());
            return true;
        }
        if (data.equals("nav:list")) {
            telegram.answerCallback(callbackId, "List", false);
            sendList(chatId, userId);
            return true;
        }
        if (data.startsWith("t:")) {
            return handleTaskCallback(callbackId, chatId, messageId, userId, data);
        }
        if (data.startsWith("flow:")) {
            var state = states.get(chatId);
            if (state.isEmpty()) {
                telegram.answerCallback(callbackId, "That add-task step expired. Send /add to start again.", true);
                return true;
            }
            telegram.answerCallback(callbackId, "Saved", false);
            conversations.handleFlowCallback(state.get(), chatId, data);
            return true;
        }
        return false;
    }

    @Override
    public void handleMessage(BotContext context, String message) {
        conversations.handleText(context.state(), context.chatId(), message);
    }

    private boolean handleTaskCallback(String callbackId, Long chatId, Long messageId, Long userId, String data) {
        String[] parts = data.split(":", 3);
        if (parts.length < 3) {
            telegram.answerCallback(callbackId, "This button is no longer available.", true);
            return true;
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
        return true;
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

    private void reply(Long chatId, Long messageId, String text, List<List<Map<String, String>>> keyboard) {
        if (messageId != null) {
            telegram.editMessage(chatId, messageId, text, keyboard);
        } else {
            telegram.sendMessage(chatId, text, keyboard);
        }
    }

    private static FeatureDeclaration closed(String key) {
        return new FeatureDeclaration(key, false);
    }

    private static Map<String, String> button(String text, String data) {
        return Map.of("text", text, "callback_data", data);
    }
}
