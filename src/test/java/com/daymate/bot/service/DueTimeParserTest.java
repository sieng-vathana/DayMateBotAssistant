package com.daymate.bot.service;

import com.daymate.bot.domain.TaskDue;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class DueTimeParserTest {
    private final DueTimeParser parser = new DueTimeParser();
    private final LocalDate today = LocalDate.of(2026, 10, 23);

    @Test
    void parsesSkipWordsAsEmptyDue() {
        assertThat(parser.parse("skip", today)).contains(TaskDue.none());
        assertThat(parser.parse("none", today)).contains(TaskDue.none());
        assertThat(parser.parse("-", today)).contains(TaskDue.none());
    }

    @Test
    void parsesTwelveHourTimeOnToday() {
        assertThat(parser.parse("5:00 PM", today))
                .contains(new TaskDue(today, LocalTime.of(17, 0)));
        assertThat(parser.parse("10:00 AM", today))
                .contains(new TaskDue(today, LocalTime.of(10, 0)));
        assertThat(parser.parse("5pm", today))
                .contains(new TaskDue(today, LocalTime.of(17, 0)));
    }

    @Test
    void parsesTwentyFourHourTime() {
        assertThat(parser.parse("17:00", today))
                .contains(new TaskDue(today, LocalTime.of(17, 0)));
    }

    @Test
    void parsesTodayAndTomorrow() {
        assertThat(parser.parse("tomorrow", today))
                .contains(new TaskDue(today.plusDays(1), null));
        assertThat(parser.parse("tomorrow 3:00 PM", today))
                .contains(new TaskDue(today.plusDays(1), LocalTime.of(15, 0)));
        assertThat(parser.parse("today 9:00 AM", today))
                .contains(new TaskDue(today, LocalTime.of(9, 0)));
    }

    @Test
    void rejectsUnknownText() {
        assertThat(parser.parse("next week sometime", today)).isEmpty();
        assertThat(parser.parse("", today)).isEmpty();
    }
}
