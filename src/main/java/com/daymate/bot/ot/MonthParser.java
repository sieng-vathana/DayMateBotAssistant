package com.daymate.bot.ot;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Optional;

public final class MonthParser {
    private static final String[] ENGLISH = {
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december"
    };
    private static final String[] ENGLISH_SHORT = {
            "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"
    };
    private static final String[] KHMER = {
            "មករា", "កុម្ភៈ", "មីនា", "មេសា", "ឧសភា", "មិថុនា",
            "កក្កដា", "សីហា", "កញ្ញា", "តុលា", "វិច្ឆិកា", "ធ្នូ"
    };

    private MonthParser() {
    }

    public static Optional<YearMonth> parse(String raw, LocalDate today) {
        if (raw == null || raw.isBlank() || today == null) {
            return Optional.empty();
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        try {
            if (value.matches("\\d{4}[-/]\\d{1,2}")) {
                String[] parts = value.split("[-/]");
                return month(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            }
            if (value.matches("\\d{1,2}/\\d{4}")) {
                String[] parts = value.split("/");
                return month(Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
            }
            if (value.matches("\\d{1,2}")) {
                return month(today.getYear(), Integer.parseInt(value));
            }
            String[] words = value.split("\\s+");
            Integer named = monthName(words[0]);
            if (named == null) {
                return Optional.empty();
            }
            int year = words.length > 1 && words[1].matches("\\d{4}") ? Integer.parseInt(words[1]) : today.getYear();
            return month(year, named);
        } catch (NumberFormatException | java.time.DateTimeException exception) {
            return Optional.empty();
        }
    }

    private static Optional<YearMonth> month(int year, int month) {
        if (month < 1 || month > 12) {
            return Optional.empty();
        }
        return Optional.of(YearMonth.of(year, month));
    }

    private static Integer monthName(String word) {
        for (int i = 0; i < ENGLISH.length; i++) {
            if (word.equals(ENGLISH[i]) || word.equals(ENGLISH_SHORT[i]) || word.equals(KHMER[i])) {
                return i + 1;
            }
        }
        return null;
    }
}
