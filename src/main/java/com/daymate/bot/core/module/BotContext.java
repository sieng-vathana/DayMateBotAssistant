package com.daymate.bot.core.module;

import com.daymate.bot.entity.ConversationStateEntity;

import java.util.Set;

public final class BotContext {
    private final Long chatId;
    private final Long userId;
    private final String callbackId;
    private final Long messageId;
    private final ConversationStateEntity state;
    private final String moduleId;
    private final String featureKey;
    private final Set<String> allowedFeatures;

    public BotContext(Long chatId, Long userId, String callbackId, Long messageId, ConversationStateEntity state,
                      String moduleId, String featureKey, Set<String> allowedFeatures) {
        this.chatId = chatId;
        this.userId = userId;
        this.callbackId = callbackId;
        this.messageId = messageId;
        this.state = state;
        this.moduleId = moduleId;
        this.featureKey = featureKey;
        this.allowedFeatures = allowedFeatures == null ? Set.of() : Set.copyOf(allowedFeatures);
    }

    public boolean allows(String feature) {
        return feature != null && allowedFeatures.contains(feature);
    }

    public Long chatId() {
        return chatId;
    }

    public Long userId() {
        return userId;
    }

    public String callbackId() {
        return callbackId;
    }

    public Long messageId() {
        return messageId;
    }

    public ConversationStateEntity state() {
        return state;
    }

    public String moduleId() {
        return moduleId;
    }

    public String featureKey() {
        return featureKey;
    }
}
