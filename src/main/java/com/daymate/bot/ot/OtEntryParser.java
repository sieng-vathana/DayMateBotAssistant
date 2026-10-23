package com.daymate.bot.ot;

import com.daymate.bot.domain.OtShift;
import com.daymate.bot.domain.OtTimeRange;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OtEntryParser {
    private static final Pattern ENTRY = Pattern.compile(
            "^(today|yesterday|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}(?:/\\d{1,2}(?:/\\d{4})?)?)\\s+(\\d{1,2}:\\d{2}\\s*[-–—]\\s*\\d{1,2}:\\d{2})(?:\\s+(\\S+))?$",
            Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private OtEntryParser() {
    }

    public static Optional<ParsedOt> parse(String raw, LocalDate today) {
        if (raw == null || today == null) {
            return Optional.empty();
        }
        Matcher matcher = ENTRY.matcher(raw.trim().replace('.', ':'));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        Optional<LocalDate> date = parseDate(matcher.group(1), today);
        Optional<OtTimeRange> range = OtTimeParser.parse(matcher.group(2));
        if (date.isEmpty() || range.isEmpty()) {
            return Optional.empty();
        }
        OtShift shift = matcher.group(3) == null ? null : OtShift.parse(matcher.group(3)).orElse(null);
        if (matcher.group(3) != null && shift == null) {
            return Optional.empty();
        }
        return Optional.of(new ParsedOt(date.get(), range.get(), shift));
    }

    public static Optional<LocalDate> parseDay(String raw, LocalDate today) {
        if (raw == null || raw.isBlank() || today == null) {
            return Optional.empty();
        }
        return parseDate(raw.trim(), today);
    }

    private static Optional<LocalDate> parseDate(String token, LocalDate today) {
        String value = token.toLowerCase(Locale.ROOT);
        if (value.equals("today")) {
            return Optional.of(today);
        }
        if (value.equals("yesterday")) {
            return Optional.of(today.minusDays(1));
        }
        try {
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return Optional.of(LocalDate.parse(value, ISO));
            }
            String[] parts = value.split("/");
            int day = Integer.parseInt(parts[0]);
            int month = parts.length > 1 ? Integer.parseInt(parts[1]) : today.getMonthValue();
            int year = parts.length > 2 ? Integer.parseInt(parts[2]) : today.getYear();
            return Optional.of(LocalDate.of(year, month, day));
        } catch (NumberFormatException | java.time.DateTimeException exception) {
            return Optional.empty();
        }
    }

    public record ParsedOt(LocalDate date, OtTimeRange range, OtShift shift) {
    }
}
