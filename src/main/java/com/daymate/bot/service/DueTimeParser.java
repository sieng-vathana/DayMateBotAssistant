package com.daymate.bot.service;

import com.daymate.bot.domain.TaskDue;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DueTimeParser {
    private static final Pattern DAY_AND_TIME = Pattern.compile("(?i)^(today|tomorrow)(?:\\s+(.+))?$");
    private static final List<DateTimeFormatter> TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("h a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("ha", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("H:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("H", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("h", Locale.ENGLISH)
    );

    public Optional<TaskDue> parse(String raw, LocalDate today) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if (text.equalsIgnoreCase("skip") || text.equalsIgnoreCase("none")
                || text.equals("-") || text.equalsIgnoreCase("no")) {
            return Optional.of(TaskDue.none());
        }
        Matcher dayMatch = DAY_AND_TIME.matcher(text);
        if (dayMatch.matches()) {
            LocalDate date = dayMatch.group(1).equalsIgnoreCase("tomorrow") ? today.plusDays(1) : today;
            String timePart = dayMatch.group(2);
            if (timePart == null || timePart.isBlank()) {
                return Optional.of(new TaskDue(date, null));
            }
            return parseTime(timePart).map(time -> new TaskDue(date, time));
        }
        return parseTime(text).map(time -> new TaskDue(today, time));
    }

    public Optional<LocalTime> parseTime(String raw) {
        String normalized = normalizeTime(raw);
        for (DateTimeFormatter formatter : TIME_FORMATS) {
            try {
                return Optional.of(LocalTime.parse(normalized, formatter));
            } catch (DateTimeParseException ignored) {
                // try the next pattern
            }
        }
        return Optional.empty();
    }

    private static String normalizeTime(String raw) {
        String value = raw.trim().replace('.', ':').replaceAll("\\s+", " ");
        value = value.replaceAll("(?i)\\s*(a\\.m\\.|am)\\s*$", " AM");
        value = value.replaceAll("(?i)\\s*(p\\.m\\.|pm)\\s*$", " PM");
        if (value.matches("(?i).*[AP]M") && !value.contains(" ")) {
            value = value.replaceAll("(?i)(AM|PM)$", " $1");
        }
        return value.toUpperCase(Locale.ENGLISH);
    }
}
