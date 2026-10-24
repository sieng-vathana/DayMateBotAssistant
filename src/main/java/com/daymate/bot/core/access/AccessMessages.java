package com.daymate.bot.core.access;

import com.daymate.bot.i18n.Texts;

public final class AccessMessages {
    private AccessMessages() {
    }

    public static String moduleUnavailable(String displayName) {
        return Texts.t(
                "🚧 " + displayName + " is currently unavailable.",
                "🚧 " + displayName + " មិនទាន់បើកទេ។");
    }

    public static String actionUnavailable() {
        return Texts.t(
                "🚧 That action is currently unavailable.",
                "🚧 សកម្មភាពនោះមិនទាន់បើកទេ។");
    }
}
