package com.daymate.bot.i18n;

import com.daymate.bot.domain.Language;

public final class LanguageContext {
    private static final ThreadLocal<Language> CURRENT = ThreadLocal.withInitial(() -> Language.EN);

    private LanguageContext() {
    }

    public static void set(Language language) {
        CURRENT.set(language == null ? Language.EN : language);
    }

    public static Language get() {
        return CURRENT.get();
    }
}
