package com.daymate.bot.domain;

public enum Language {
    EN,
    KM;

    public static Language from(String raw) {
        if (raw == null) {
            return EN;
        }
        String value = raw.trim().toLowerCase();
        if (value.equals("km") || value.equals("kh") || value.equals("khmer") || value.equals("ខ្មែរ")) {
            return KM;
        }
        return EN;
    }
}
