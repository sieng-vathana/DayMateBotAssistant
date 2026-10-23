package com.daymate.bot.ot;

import com.daymate.bot.domain.OtTimeRange;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OtTimeParser {
    private static final Pattern RANGE = Pattern.compile("^(\\d{1,2}:\\d{2})\\s*[-–—]\\s*(\\d{1,2}:\\d{2})$");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("H:mm");

    private OtTimeParser() {
    }

    public static Optional<OtTimeRange> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.trim().replace('.', ':');
        Matcher matcher = RANGE.matcher(text);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            LocalTime start = LocalTime.parse(matcher.group(1), HOUR);
            LocalTime end = LocalTime.parse(matcher.group(2), HOUR);
            return Optional.of(new OtTimeRange(start, end));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }
}
