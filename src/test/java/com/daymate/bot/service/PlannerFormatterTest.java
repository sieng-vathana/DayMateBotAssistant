package com.daymate.bot.service;

import com.daymate.bot.domain.Recurrence;
import com.daymate.bot.domain.TaskCategory;
import com.daymate.bot.domain.TaskPriority;
import com.daymate.bot.entity.TaskEntity;
import com.daymate.bot.save.TaskSaveService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerFormatterTest {
    private final PlannerFormatter formatter = new PlannerFormatter();

    @Test
    void welcomeMatchesDayMateCopy() {
        String text = formatter.welcome();
        assertThat(text).contains("Welcome to DayMate — Your Daily Planner Bot");
        assertThat(text).contains("/today — View today's tasks");
        assertThat(text).contains("/add — Add a new task");
        assertThat(text).contains("Let's make today productive!");
    }

    @Test
    void todayShowsProgressAndTaskRows() {
        LocalDate date = LocalDate.of(2026, 10, 10);
        TaskEntity remaining = TaskEntity.create(
                "T-001", 1L, 1L, "Review Q4 budget",
                TaskCategory.WORK, TaskPriority.HIGH,
                date, LocalTime.of(10, 0), Recurrence.NONE,
                Instant.parse("2026-10-10T02:00:00Z"));
        remaining.markInProgress(Instant.parse("2026-10-10T03:00:00Z"));
        TaskEntity completed = TaskEntity.create(
                "T-002", 1L, 1L, "Call dentist",
                TaskCategory.PERSONAL, TaskPriority.MEDIUM,
                date, LocalTime.of(14, 0), Recurrence.NONE,
                Instant.parse("2026-10-10T02:00:00Z"));
        completed.markCompleted(Instant.parse("2026-10-10T07:15:00Z"));

        String text = formatter.today(new TaskSaveService.Agenda(date, List.of(remaining), List.of(completed)));

        assertThat(text).contains("Today's Tasks — " + date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d", java.util.Locale.ENGLISH)));
        assertThat(text).contains("Progress: 1/2 done ✅");
        assertThat(text).contains("🔴 [Work] Review Q4 budget");
        assertThat(text).contains("Due: 10:00 AM · High · In Progress");
    }

    @Test
    void taskSavedUsesPublicIdAndMetadata() {
        TaskEntity task = TaskEntity.create(
                "T-004", 1L, 1L, "Buy groceries",
                TaskCategory.PERSONAL, TaskPriority.MEDIUM,
                LocalDate.of(2026, 10, 10), LocalTime.of(17, 0), Recurrence.NONE,
                Instant.parse("2026-10-10T02:00:00Z"));
        String text = formatter.taskSaved(task);
        assertThat(text).contains("✅ Task saved!");
        assertThat(text).contains("T-004  Buy groceries");
        assertThat(text).contains("Personal · Medium · Due 5:00 PM · Pending");
    }
}
