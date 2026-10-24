package com.daymate.bot.core.module;

public final class BotContexts {
    private static final ThreadLocal<BotContext> CURRENT = new ThreadLocal<>();

    private BotContexts() {
    }

    public static void set(BotContext context) {
        CURRENT.set(context);
    }

    public static BotContext current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
