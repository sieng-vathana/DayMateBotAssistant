package com.daymate.bot.modules.prepared;

import com.daymate.bot.core.access.AccessMessages;
import com.daymate.bot.core.module.BotContext;
import com.daymate.bot.core.module.BotModule;
import com.daymate.bot.core.module.FeatureDeclaration;
import com.daymate.bot.core.module.MenuAction;
import com.daymate.bot.entity.ConversationStateEntity;
import com.daymate.bot.i18n.Texts;
import com.daymate.bot.telegram.TelegramApiClient;

import java.util.List;
import java.util.Map;

public abstract class PreparedModule implements BotModule {
    private final String id;
    private final String displayName;
    private final String description;
    private final int displayOrder;
    private final List<String> commands;
    private final String featureKey;
    private final TelegramApiClient telegram;

    protected PreparedModule(String id, String displayName, String description, int displayOrder,
                             List<String> commands, String featureKey, TelegramApiClient telegram) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.displayOrder = displayOrder;
        this.commands = List.copyOf(commands);
        this.featureKey = featureKey;
        this.telegram = telegram;
    }

    @Override
    public String moduleId() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public int displayOrder() {
        return displayOrder;
    }

    @Override
    public boolean enabledByDefault() {
        return false;
    }

    @Override
    public List<String> commands() {
        return commands;
    }

    @Override
    public List<String> callbackPrefixes() {
        return List.of(id);
    }

    @Override
    public List<FeatureDeclaration> features() {
        return List.of(new FeatureDeclaration(featureKey, false));
    }

    @Override
    public List<MenuAction> menuActions() {
        return List.of();
    }

    @Override
    public String featureForCommand(String command) {
        return featureKey;
    }

    @Override
    public String featureForCallback(String data) {
        return featureKey;
    }

    @Override
    public String featureForState(ConversationStateEntity state) {
        return featureKey;
    }

    @Override
    public String commandForMenuAction(String action) {
        return null;
    }

    @Override
    public void showMenu(BotContext context) {
        telegram.sendMessage(context.chatId(), AccessMessages.moduleUnavailable(displayName),
                List.of(List.of(mainMenu())));
    }

    @Override
    public void handleCommand(BotContext context, String command, String arguments) {
        telegram.sendMessage(context.chatId(), AccessMessages.moduleUnavailable(displayName));
    }

    @Override
    public boolean handleCallback(BotContext context, String data) {
        return false;
    }

    @Override
    public void handleMessage(BotContext context, String message) {
        telegram.sendMessage(context.chatId(), AccessMessages.moduleUnavailable(displayName));
    }

    private static Map<String, String> mainMenu() {
        return Map.of("text", Texts.t("Main menu", "ម៉ឺនុយ"), "callback_data", "menu:home");
    }
}
