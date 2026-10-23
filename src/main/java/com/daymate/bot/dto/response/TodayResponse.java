package com.daymate.bot.dto.response;

import java.time.LocalDate;
import java.util.List;

public record TodayResponse(
        LocalDate date,
        int doneCount,
        int totalCount,
        List<TaskResponse> remaining,
        List<TaskResponse> completedToday) {
}
