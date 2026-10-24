package com.daymate.bot.router;

import com.daymate.bot.config.BotProperties;
import com.daymate.bot.core.access.AccessDecision;
import com.daymate.bot.core.access.AccessMessages;
import com.daymate.bot.core.access.FeatureAccessGuard;
import com.daymate.bot.core.module.BotContext;
import com.daymate.bot.core.module.BotContexts;
import com.daymate.bot.core.module.BotModule;
import com.daymate.bot.core.module.FeatureDeclaration;
import com.daymate.bot.core.module.MenuAction;
import com.daymate.bot.core.module.ModuleRegistry;
import com.daymate.bot.core.security.UserRateLimiter;
import com.daymate.bot.domain.Language;
import com.daymate.bot.dto.request.TelegramUpdateRequest;
import com.daymate.bot.entity.ConversationStateEntity;
import com.daymate.bot.i18n.LanguageContext;
import com.daymate.bot.modules.notes.NotesModule;
import com.daymate.bot.modules.overtime.OvertimeModule;
import com.daymate.bot.service.ConversationLookup;
import com.daymate.bot.service.ConversationService;
import com.daymate.bot.portal.PortalAccountService;
import com.daymate.bot.service.LanguageService;
import com.daymate.bot.service.PlannerFormatter;
import com.daymate.bot.telegram.TelegramApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CommandRouter {
    private static final Logger log = LoggerFactory.getLogger(CommandRouter.class);
    private static final int MAX_TEXT = 1000;
    private static final int MAX_CALLBACK = 64;

    private final TelegramApiClient telegram;
    private final ConversationService conversations;
    private final LanguageService languages;
    private final PortalAccountService portalAccounts;
    private final PlannerFormatter formatter;
    private final ModuleRegistry registry;
    private final FeatureAccessGuard guard;
    private final UserRateLimiter rateLimiter;
    private final Set<Long> allowedUserIds;

    public CommandRouter(TelegramApiClient telegram, ConversationService conversations, LanguageService languages,
                         PortalAccountService portalAccounts, PlannerFormatter formatter, ModuleRegistry registry,
                         FeatureAccessGuard guard, UserRateLimiter rateLimiter, BotProperties properties) {
        this.telegram = telegram;
        this.conversations = conversations;
        this.languages = languages;
        this.portalAccounts = portalAccounts;
        this.formatter = formatter;
        this.registry = registry;
        this.guard = guard;
        this.rateLimiter = rateLimiter;
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
        try {
            LanguageContext.set(Language.EN);
            BotContexts.clear();
            if (update == null) {
                return;
            }
            if (update.callbackQuery() != null) {
                processCallback(update.callbackQuery());
            } else if (update.message() != null) {
                processMessage(update.message());
            }
        } finally {
            LanguageContext.set(Language.EN);
            BotContexts.clear();
        }
    }

    private void processMessage(TelegramUpdateRequest.Message message) {
        if (message.chat() == null || message.from() == null || message.text() == null) {
            return;
        }
        Long chatId = message.chat().id();
        Long userId = message.from().id();
        portalAccounts.rememberTelegramUser(userId, message.from().username());
        LanguageContext.set(languages.get(userId));
        try {
            if (!rateLimiter.allow(userId)) {
                telegram.sendMessage(chatId, formatter.slowDown());
                return;
            }
            String text = message.text().trim();
            if (text.equalsIgnoreCase("/id") || text.toLowerCase(Locale.ROOT).startsWith("/id@")) {
                telegram.sendMessage(chatId, formatter.userId(userId));
                return;
            }
            if (!isAllowed(userId)) {
                telegram.sendMessage(chatId, formatter.notAllowed(userId));
                return;
            }
            if (message.text().length() > MAX_TEXT) {
                telegram.sendMessage(chatId, formatter.shorterMessage());
                return;
            }
            if (isCommand(text)) {
                conversations.clear(chatId);
                handleCommand(chatId, userId, text);
                return;
            }
            handlePlainText(chatId, userId, text);
        } catch (IllegalArgumentException exception) {
            telegram.sendMessage(chatId, exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("Message failed for user {} ({})", userId, exception.getClass().getSimpleName());
            telegram.sendMessage(chatId, formatter.didNotWork());
        }
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
        if (userId != null && callback.from() != null) {
            portalAccounts.rememberTelegramUser(userId, callback.from().username());
        }
        try {
            if (userId != null && !rateLimiter.allow(userId)) {
                telegram.answerCallback(callback.id(), formatter.slowDown(), true);
                return;
            }
            if (userId == null || !isAllowed(userId)) {
                telegram.answerCallback(callback.id(), "You are not authorized to use this bot.", true);
                return;
            }
            if (chatId == null || callback.data() == null) {
                telegram.answerCallback(callback.id(), "This button is no longer available.", true);
                return;
            }
            String data = callback.data();
            if (data.length() > MAX_CALLBACK) {
                telegram.answerCallback(callback.id(), "This button is no longer available.", true);
                return;
            }
            if (data.startsWith("lang:")) {
                Language chosen = Language.from(data.substring("lang:".length()));
                languages.set(userId, chosen);
                LanguageContext.set(chosen);
                telegram.answerCallback(callback.id(), "OK", false);
                telegram.sendMessage(chatId, formatter.languageSaved());
                return;
            }
            if (data.equals("menu:home") || data.equals("menu")) {
                conversations.clear(chatId);
                telegram.answerCallback(callback.id(), "Menu", false);
                showMainMenu(chatId, userId);
                return;
            }
            if (data.startsWith("menu:")) {
                handleMenu(callback.id(), chatId, messageId, userId, data);
                return;
            }
            if (data.equals("ot:cancel")) {
                handleCancel(callback.id(), chatId, messageId, userId);
                return;
            }
            dispatchModuleCallback(callback.id(), chatId, messageId, userId, data);
        } catch (IllegalArgumentException exception) {
            telegram.answerCallback(callback.id(), exception.getMessage(), true);
        } catch (RuntimeException exception) {
            log.warn("Callback failed for user {} ({})", userId, exception.getClass().getSimpleName());
            telegram.answerCallback(callback.id(), formatter.didNotWork(), true);
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
            case "/start", "/menu" -> showMainMenu(chatId, userId);
            case "/help" -> showHelp(chatId, userId);
            case "/language", "/lang" -> telegram.sendMessage(chatId, formatter.languagePrompt(), languageKeyboard());
            default -> dispatchCommand(chatId, userId, command, argument);
        }
    }

    private void dispatchCommand(Long chatId, Long userId, String command, String argument) {
        Optional<BotModule> found = registry.findByCommand(command);
        if (found.isEmpty()) {
            telegram.sendMessage(chatId, formatter.unknownCommand());
            return;
        }
        BotModule module = found.get();
        String feature = module.featureForCommand(command);
        AccessDecision decision = guard.decide(userId, module.moduleId(), feature);
        if (decision != AccessDecision.ALLOW) {
            telegram.sendMessage(chatId, denialText(decision, module));
            return;
        }
        BotContext context = contextFor(chatId, userId, null, null, null, module, feature);
        BotContexts.set(context);
        invokeMessage(module, userId, chatId, () -> module.handleCommand(context, command, argument));
    }

    private void showHelp(Long chatId, Long userId) {
        boolean overtimeAllowed = registry.find(OvertimeModule.ID)
                .map(module -> guard.visible(userId, module))
                .orElse(false);
        String text = overtimeAllowed ? formatter.help() : formatter.helpWhenOvertimeHidden();
        if (notesVisible(userId)) {
            text = formatter.withNotesCommands(text);
        }
        telegram.sendMessage(chatId, text);
    }

    private void showMainMenu(Long chatId, Long userId) {
        List<BotModule> visible = registry.all().stream()
                .filter(module -> guard.visible(userId, module))
                .sorted(Comparator.comparingInt(BotModule::displayOrder))
                .toList();
        if (visible.isEmpty()) {
            telegram.sendMessage(chatId, formatter.welcome());
            return;
        }
        boolean notesOn = visible.stream().anyMatch(module -> NotesModule.ID.equals(module.moduleId()));
        String text = notesOn ? formatter.withNotesCommands(formatter.welcome()) : formatter.welcome();
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (BotModule module : visible) {
            rows.add(List.of(button(module.displayName(), "menu:" + module.moduleId())));
            if (notesOn && NotesModule.ID.equals(module.moduleId()) && module instanceof NotesModule notes) {
                rows.addAll(notes.optionRows(feature ->
                        guard.decide(userId, module.moduleId(), feature) == AccessDecision.ALLOW));
            }
        }
        telegram.sendMessage(chatId, text, rows);
    }

    private boolean notesVisible(Long userId) {
        return registry.find(NotesModule.ID)
                .map(module -> guard.visible(userId, module))
                .orElse(false);
    }

    private void handleMenu(String callbackId, Long chatId, Long messageId, Long userId, String data) {
        String rest = data.substring("menu:".length());
        int colon = rest.indexOf(':');
        String moduleId = colon < 0 ? rest : rest.substring(0, colon);
        String action = colon < 0 ? null : rest.substring(colon + 1);
        Optional<BotModule> found = registry.find(moduleId);
        if (found.isEmpty()) {
            telegram.answerCallback(callbackId, AccessMessages.actionUnavailable(), true);
            return;
        }
        BotModule module = found.get();
        if (action == null) {
            AccessDecision decision = guard.decide(userId, module.moduleId(), null);
            if (decision != AccessDecision.ALLOW || !guard.visible(userId, module)) {
                clearOwnedConversation(chatId, userId);
                telegram.answerCallback(callbackId, denialText(decision == AccessDecision.ALLOW
                        ? AccessDecision.MODULE_OFF : decision, module), true);
                return;
            }
            telegram.answerCallback(callbackId, module.displayName(), false);
            BotContext context = contextFor(chatId, userId, callbackId, messageId, null, module, null);
            BotContexts.set(context);
            invokeMessage(module, userId, chatId, () -> module.showMenu(context));
            return;
        }
        MenuAction menuAction = module.menuActions().stream()
                .filter(candidate -> candidate.action().equals(action))
                .findFirst()
                .orElse(null);
        String command = module.commandForMenuAction(action);
        if (menuAction == null || command == null) {
            telegram.answerCallback(callbackId, "This button is no longer available.", true);
            return;
        }
        AccessDecision decision = guard.decide(userId, module.moduleId(), menuAction.featureKey());
        if (decision != AccessDecision.ALLOW) {
            clearOwnedConversation(chatId, userId);
            telegram.answerCallback(callbackId, denialText(decision, module), true);
            return;
        }
        telegram.answerCallback(callbackId, menuAction.label(), false);
        conversations.clear(chatId);
        BotContext context = contextFor(chatId, userId, callbackId, messageId, null, module, menuAction.featureKey());
        BotContexts.set(context);
        invokeMessage(module, userId, chatId, () -> module.handleCommand(context, command, ""));
    }

    private void handleCancel(String callbackId, Long chatId, Long messageId, Long userId) {
        Optional<BotModule> overtime = registry.find(OvertimeModule.ID);
        boolean moduleDisabled = overtime.isEmpty() || !guard.isModuleEnabled(OvertimeModule.ID);
        AccessDecision decision = guard.decide(userId, OvertimeModule.ID, null);
        if (decision == AccessDecision.ALLOW || moduleDisabled) {
            if (overtime.isEmpty()) {
                conversations.clear(chatId);
                telegram.answerCallback(callbackId, "Cancelled", false);
                return;
            }
            BotModule module = overtime.get();
            BotContext context = contextFor(chatId, userId, callbackId, messageId, null, module, null);
            BotContexts.set(context);
            boolean handled = invokeCallback(module, userId, callbackId, chatId,
                    () -> module.handleCallback(context, "ot:cancel"));
            if (!handled) {
                conversations.clear(chatId);
                telegram.answerCallback(callbackId, "Cancelled", false);
            }
            return;
        }
        telegram.answerCallback(callbackId, AccessMessages.moduleUnavailable(overtime.get().displayName()), true);
    }

    private void dispatchModuleCallback(String callbackId, Long chatId, Long messageId, Long userId, String data) {
        Optional<BotModule> found = registry.findByCallback(data);
        if (found.isEmpty()) {
            telegram.answerCallback(callbackId, "This button is no longer available.", true);
            return;
        }
        BotModule module = found.get();
        String feature = module.featureForCallback(data);
        AccessDecision decision = guard.decide(userId, module.moduleId(), feature);
        if (decision != AccessDecision.ALLOW) {
            clearOwnedConversation(chatId, userId);
            telegram.answerCallback(callbackId, denialText(decision, module), true);
            return;
        }
        BotContext context = contextFor(chatId, userId, callbackId, messageId, null, module, feature);
        BotContexts.set(context);
        boolean handled = invokeCallback(module, userId, callbackId, chatId,
                () -> module.handleCallback(context, data));
        if (!handled) {
            telegram.answerCallback(callbackId, "This button is no longer available.", true);
        }
    }

    private void handlePlainText(Long chatId, Long userId, String text) {
        ConversationLookup lookup = conversations.read(chatId);
        if (lookup.kind() == ConversationLookup.Kind.EXPIRED) {
            telegram.sendMessage(chatId, formatter.stepExpired());
            return;
        }
        if (lookup.kind() != ConversationLookup.Kind.FOUND) {
            telegram.sendMessage(chatId, formatter.unknownCommand());
            return;
        }
        ConversationStateEntity state = lookup.state();
        if (state.getUserId() == null || !state.getUserId().equals(userId)) {
            telegram.sendMessage(chatId, formatter.unknownCommand());
            return;
        }
        String moduleId = state.getModuleKey();
        if (moduleId == null || moduleId.isBlank()) {
            moduleId = state.getStep().isOtSetup() ? OvertimeModule.ID : "tasks";
        }
        Optional<BotModule> found = registry.find(moduleId);
        if (found.isEmpty()) {
            conversations.clear(chatId);
            telegram.sendMessage(chatId, AccessMessages.actionUnavailable());
            return;
        }
        BotModule module = found.get();
        String feature = module.featureForState(state);
        AccessDecision decision = guard.decide(userId, module.moduleId(), feature);
        if (decision != AccessDecision.ALLOW) {
            conversations.clear(chatId);
            telegram.sendMessage(chatId, denialText(decision, module));
            return;
        }
        BotContext context = contextFor(chatId, userId, null, null, state, module, feature);
        BotContexts.set(context);
        invokeMessage(module, userId, chatId, () -> module.handleMessage(context, text));
    }

    private void clearOwnedConversation(Long chatId, Long userId) {
        ConversationLookup lookup = conversations.read(chatId);
        if (lookup.kind() == ConversationLookup.Kind.FOUND
                && lookup.state().getUserId() != null
                && lookup.state().getUserId().equals(userId)) {
            conversations.clear(chatId);
        }
    }

    private BotContext contextFor(Long chatId, Long userId, String callbackId, Long messageId,
                                  ConversationStateEntity state, BotModule module, String feature) {
        return new BotContext(chatId, userId, callbackId, messageId, state, module.moduleId(), feature,
                allowedFeatures(userId, module));
    }

    private Set<String> allowedFeatures(Long userId, BotModule module) {
        Set<String> allowed = new LinkedHashSet<>();
        for (FeatureDeclaration feature : module.features()) {
            if (guard.decide(userId, module.moduleId(), feature.key()) == AccessDecision.ALLOW) {
                allowed.add(feature.key());
            }
        }
        return Set.copyOf(allowed);
    }

    private void invokeMessage(BotModule module, Long userId, Long chatId, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            telegram.sendMessage(chatId, exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("Module {} failed for user {} ({})", module.moduleId(), userId, exception.getClass().getSimpleName());
            telegram.sendMessage(chatId, formatter.didNotWork());
        }
    }

    private boolean invokeCallback(BotModule module, Long userId, String callbackId, Long chatId,
                                   CallbackAction action) {
        try {
            return action.run();
        } catch (IllegalArgumentException exception) {
            telegram.answerCallback(callbackId, exception.getMessage(), true);
            return true;
        } catch (RuntimeException exception) {
            log.warn("Module {} failed for user {} ({})", module.moduleId(), userId, exception.getClass().getSimpleName());
            telegram.answerCallback(callbackId, formatter.didNotWork(), true);
            return true;
        }
    }

    private static String denialText(AccessDecision decision, BotModule module) {
        if (decision == AccessDecision.MODULE_OFF) {
            return AccessMessages.moduleUnavailable(module.displayName());
        }
        return AccessMessages.actionUnavailable();
    }

    private static List<List<Map<String, String>>> languageKeyboard() {
        return List.of(List.of(
                Map.of("text", "English", "callback_data", "lang:en"),
                Map.of("text", "ខ្មែរ", "callback_data", "lang:km")));
    }

    private static Map<String, String> button(String text, String data) {
        return Map.of("text", text, "callback_data", data);
    }

    private boolean isAllowed(Long userId) {
        return allowedUserIds.isEmpty() || allowedUserIds.contains(userId);
    }

    private static boolean isCommand(String text) {
        return text.startsWith("/");
    }

    @FunctionalInterface
    private interface CallbackAction {
        boolean run();
    }
}
