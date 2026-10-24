package com.daymate.bot.core.module;

import com.daymate.bot.i18n.Texts;

public record MenuAction(String action, String english, String khmer, String featureKey) {
    public String label() {
        return Texts.t(english, khmer);
    }
}
