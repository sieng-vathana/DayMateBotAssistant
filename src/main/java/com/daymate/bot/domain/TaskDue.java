package com.daymate.bot.domain;

import java.time.LocalDate;
import java.time.LocalTime;

public record TaskDue(LocalDate date, LocalTime time) {
    public static TaskDue none() {
        return new TaskDue(null, null);
    }

    public boolean isEmpty() {
        return date == null && time == null;
    }
}
