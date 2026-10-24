package com.daymate.bot.notes;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class NoteTimes {
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TEXT_DATE = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d MMM yyyy")
            .toFormatter(Locale.ENGLISH);
    private static final DateTimeFormatter TIME_24 = new DateTimeFormatterBuilder()
            .appendPattern("H:mm")
            .toFormatter(Locale.ENGLISH);
    private static final DateTimeFormatter TIME_12 = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("h:mm")
            .appendLiteral(' ')
            .appendText(ChronoField.AMPM_OF_DAY, Map.of(0L, "AM", 1L, "PM"))
            .toFormatter(Locale.ENGLISH);
    private static final DateTimeFormatter[] DATE_TIMES = {
            dateTime(ISO_DATE, TIME_24),
            dateTime(ISO_DATE, TIME_12),
            dateTime(TEXT_DATE, TIME_24),
            dateTime(TEXT_DATE, TIME_12)
    };

    private final Clock clock;
    private final ZoneId zone;

    public NoteTimes(Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    public Instant now() {
        return Instant.now(clock);
    }

    public ZoneId zone() {
        return zone;
    }

    public Instant todayAtEight() {
        return atEight(0);
    }

    public Instant tomorrowAtEight() {
        return atEight(1);
    }

    public Instant toInstant(LocalDateTime local) {
        return local.atZone(zone).toInstant();
    }

    public static Optional<LocalDate> parseDate(String text) {
        String value = normalize(text);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        Optional<LocalDate> iso = tryDate(value, ISO_DATE);
        if (iso.isPresent()) {
            return iso;
        }
        return tryDate(value, TEXT_DATE);
    }

    public static Optional<LocalTime> parseTime(String text) {
        String value = normalize(text);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        Optional<LocalTime> twelve = tryTime(value, TIME_12);
        if (twelve.isPresent()) {
            return twelve;
        }
        return tryTime(value, TIME_24);
    }

    public static Optional<LocalDateTime> parseDateTime(String text) {
        String value = normalize(text);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        for (DateTimeFormatter formatter : DATE_TIMES) {
            try {
                return Optional.of(LocalDateTime.parse(value, formatter));
            } catch (DateTimeParseException ignored) {
                // The next pattern may match.
            }
        }
        return Optional.empty();
    }

    private Instant atEight(int plusDays) {
        LocalDate day = now().atZone(zone).toLocalDate().plusDays(plusDays);
        return day.atTime(LocalTime.of(20, 0)).atZone(zone).toInstant();
    }

    private static DateTimeFormatter dateTime(DateTimeFormatter date, DateTimeFormatter time) {
        return new DateTimeFormatterBuilder()
                .append(date)
                .appendLiteral(' ')
                .append(time)
                .toFormatter(Locale.ENGLISH);
    }

    private static Optional<LocalDate> tryDate(String value, DateTimeFormatter formatter) {
        try {
            return Optional.of(LocalDate.parse(value, formatter));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private static Optional<LocalTime> tryTime(String value, DateTimeFormatter formatter) {
        try {
            return Optional.of(LocalTime.parse(value, formatter));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replace('\u202f', ' ').replace('\u00a0', ' ');
    }
}
