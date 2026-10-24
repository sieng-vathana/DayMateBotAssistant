package com.daymate.bot.core.module;

import com.daymate.bot.entity.ConversationStateEntity;

import java.util.List;

public interface BotModule {
    String moduleId();

    String displayName();

    String description();

    int displayOrder();

    boolean enabledByDefault();

    List<String> commands();

    List<String> callbackPrefixes();

    List<FeatureDeclaration> features();

    List<MenuAction> menuActions();

    String featureForCommand(String command);

    String featureForCallback(String data);

    String featureForState(ConversationStateEntity state);

    String commandForMenuAction(String action);

    void showMenu(BotContext context);

    void handleCommand(BotContext context, String command, String arguments);

    boolean handleCallback(BotContext context, String data);

    void handleMessage(BotContext context, String message);
}
