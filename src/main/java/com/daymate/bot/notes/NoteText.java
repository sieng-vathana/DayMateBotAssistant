package com.daymate.bot.notes;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NoteText {
    public static final int MAX_CONTENT = 1000;
    public static final int MAX_TITLE = 80;
    public static final int MAX_TAGS = 8;

    private static final Pattern TAG = Pattern.compile("(?u)#([\\p{L}\\p{N}_]{1,32})");
    private static final Pattern SEARCH = Pattern.compile("[\\p{L}\\p{N} #_]{2,40}");

    private NoteText() {
    }

    public static String content(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException(NoteMessages.needText());
        }
        String stripped = raw.strip().replace("\r\n", "\n").replace('\r', '\n');
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(NoteMessages.needText());
        }
        if (stripped.length() > MAX_CONTENT) {
            throw new IllegalArgumentException(NoteMessages.shorter());
        }
        return stripped;
    }

    public static String title(String content) {
        int breakAt = lineBreak(content);
        String first = breakAt < 0 ? content : content.substring(0, breakAt).strip();
        String source = first.isEmpty() ? content.strip() : first;
        if (source.length() <= MAX_TITLE) {
            return source;
        }
        return source.substring(0, MAX_TITLE);
    }

    public static List<String> tags(String content) {
        Matcher matcher = TAG.matcher(content == null ? "" : content);
        Set<String> found = new LinkedHashSet<>();
        while (matcher.find() && found.size() < MAX_TAGS) {
            found.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return List.copyOf(found);
    }

    public static String keyword(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (!SEARCH.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(NoteMessages.searchLength());
        }
        return trimmed;
    }

    public static String likePattern(String keyword) {
        String escaped = keyword.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static int lineBreak(String value) {
        int newline = value.indexOf('\n');
        int carriage = value.indexOf('\r');
        if (newline < 0) {
            return carriage;
        }
        if (carriage < 0) {
            return newline;
        }
        return Math.min(newline, carriage);
    }
}
