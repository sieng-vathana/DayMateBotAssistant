package com.daymate.bot.notes;

import com.daymate.bot.entity.NoteEntity;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class NoteTimesTest {
    private static final ZoneId PHNOM_PENH = ZoneId.of("Asia/Phnom_Penh");

    @Test
    void parsesDateTimeInPhnomPenh() {
        LocalDateTime parsed = NoteTimes.parseDateTime("25 Oct 2026 8:00 PM").orElseThrow();
        NoteTimes times = new NoteTimes(Clock.systemUTC(), PHNOM_PENH);

        assertThat(times.toInstant(parsed)).isEqualTo(Instant.parse("2026-10-25T13:00:00Z"));
        assertThat(NoteTimes.parseDateTime("2026-10-25 20:00").orElseThrow()).isEqualTo(parsed);
        assertThat(NoteTimes.parseDateTime("25 oct 2026 8:00 pm").orElseThrow()).isEqualTo(parsed);
    }

    @Test
    void parsesDateAndTimeSeparately() {
        assertThat(NoteTimes.parseDate("2026-10-25")).contains(java.time.LocalDate.of(2026, 10, 25));
        assertThat(NoteTimes.parseDate("25 Oct 2026")).contains(java.time.LocalDate.of(2026, 10, 25));
        assertThat(NoteTimes.parseTime("20:00")).contains(java.time.LocalTime.of(20, 0));
        assertThat(NoteTimes.parseTime("8:00 PM")).contains(java.time.LocalTime.of(20, 0));
        assertThat(NoteTimes.parseDateTime("25 Oct 2026")).isEmpty();
    }

    @Test
    void todayAndTomorrowAreEightInTheEvening() {
        Clock before = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);
        NoteTimes times = new NoteTimes(before, PHNOM_PENH);

        assertThat(times.todayAtEight()).isEqualTo(Instant.parse("2026-10-25T13:00:00Z"));
        assertThat(times.tomorrowAtEight()).isEqualTo(Instant.parse("2026-10-26T13:00:00Z"));

        Clock after = Clock.fixed(Instant.parse("2026-10-25T14:00:00Z"), ZoneOffset.UTC);
        NoteTimes later = new NoteTimes(after, PHNOM_PENH);
        assertThat(later.todayAtEight()).isEqualTo(Instant.parse("2026-10-25T13:00:00Z"));
        assertThat(later.todayAtEight().isAfter(later.now())).isFalse();
    }

    @Test
    void formatsThePhnomPenhClock() {
        NoteFormatter formatter = new NoteFormatter(PHNOM_PENH);
        Instant instant = Instant.parse("2026-10-25T13:00:00Z");

        assertThat(formatter.formatDate(instant)).isEqualTo("25 Oct 2026");
        assertThat(formatter.formatTime(instant)).isEqualTo("8:00 PM");
    }

    @Test
    void noteListStretchesEachRowOnOneLine() {
        NoteFormatter formatter = new NoteFormatter(PHNOM_PENH);
        NoteEntity shortNote = new NoteEntity(1L, "Milk", "Milk", false, Instant.parse("2026-10-24T01:00:00Z"));
        NoteEntity longNote = new NoteEntity(1L, "A".repeat(80), "A".repeat(80), true, Instant.parse("2026-10-24T01:00:00Z"));

        String text = formatter.myNotes(0, 2, List.of(shortNote, longNote));
        String shortRow = text.lines().filter(line -> line.startsWith("1. ")).findFirst().orElseThrow();
        String longRow = text.lines().filter(line -> line.startsWith("2. ")).findFirst().orElseThrow();

        assertThat(shortRow).contains("Milk").endsWith("24 Oct 2026");
        assertThat(shortRow).doesNotContain("\u3164").doesNotContain("\u2800").doesNotContain("\u2003");
        assertThat(shortRow.length()).isEqualTo(60);
        assertThat(longRow).endsWith("24 Oct 2026");
        assertThat(longRow).doesNotContain("A".repeat(50));
        assertThat(longRow.indexOf('\n')).isNegative();
    }
}
