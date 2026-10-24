package com.daymate.bot.service;

import com.daymate.bot.entity.ConversationStateEntity;

public final class ConversationLookup {
    public enum Kind {
        NONE,
        EXPIRED,
        FOUND
    }

    private final Kind kind;
    private final ConversationStateEntity state;

    private ConversationLookup(Kind kind, ConversationStateEntity state) {
        this.kind = kind;
        this.state = state;
    }

    public static ConversationLookup none() {
        return new ConversationLookup(Kind.NONE, null);
    }

    public static ConversationLookup expired() {
        return new ConversationLookup(Kind.EXPIRED, null);
    }

    public static ConversationLookup found(ConversationStateEntity state) {
        return new ConversationLookup(Kind.FOUND, state);
    }

    public Kind kind() {
        return kind;
    }

    public ConversationStateEntity state() {
        return state;
    }
}
