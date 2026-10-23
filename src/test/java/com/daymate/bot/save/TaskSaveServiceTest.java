package com.daymate.bot.save;

import com.daymate.bot.domain.Recurrence;
import com.daymate.bot.domain.TaskCategory;
import com.daymate.bot.domain.TaskDue;
import com.daymate.bot.domain.TaskPriority;
import com.daymate.bot.domain.TaskStatus;
import com.daymate.bot.entity.TaskEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TaskSaveServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");
    private static final Instant NOW = Instant.parse("2026-10-10T03:00:00Z");

    @Autowired
    private TaskSaveService tasks;

    @Test
    void assignsIncrementalPublicIdsAndCompletesWithProgress() {
        TaskDue due = new TaskDue(LocalDate.of(2026, 10, 10), LocalTime.of(10, 0));
        TaskEntity first = tasks.create(7L, 7L, "Review Q4 budget", TaskCategory.WORK, TaskPriority.HIGH, due, Recurrence.NONE);
        TaskEntity second = tasks.create(7L, 7L, "Call dentist", TaskCategory.PERSONAL, TaskPriority.MEDIUM, due, Recurrence.NONE);

        assertThat(first.getPublicId()).isEqualTo("T-001");
        assertThat(second.getPublicId()).isEqualTo("T-002");

        tasks.complete(7L, "T-002");
        TaskSaveService.Agenda agenda = tasks.today(7L);

        assertThat(agenda.doneCount()).isEqualTo(1);
        assertThat(agenda.totalCount()).isEqualTo(2);
        assertThat(agenda.remaining()).extracting(TaskEntity::getPublicId).containsExactly("T-001");
        assertThat(agenda.completedToday()).extracting(TaskEntity::getPublicId).containsExactly("T-002");
    }

    @Test
    void completingDailyTaskSpawnsTomorrow() {
        TaskDue due = new TaskDue(LocalDate.of(2026, 10, 10), LocalTime.of(9, 0));
        tasks.create(8L, 8L, "Morning standup", TaskCategory.WORK, TaskPriority.HIGH, due, Recurrence.DAILY);
        tasks.complete(8L, "T-001");

        assertThat(tasks.require(8L, "T-001").getStatusEnum()).isEqualTo(TaskStatus.COMPLETED);
        TaskEntity next = tasks.require(8L, "T-002");
        assertThat(next.getTitle()).isEqualTo("Morning standup");
        assertThat(next.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(next.getStatusEnum()).isEqualTo(TaskStatus.PENDING);
        assertThat(next.getRecurrenceEnum()).isEqualTo(Recurrence.DAILY);
    }

    @Test
    void snoozeMovesDueTimeForwardOneHour() {
        TaskDue due = new TaskDue(LocalDate.of(2026, 10, 10), LocalTime.of(10, 0));
        tasks.create(9L, 9L, "Evening run", TaskCategory.HEALTH, TaskPriority.LOW, due, Recurrence.NONE);
        TaskEntity snoozed = tasks.snooze(9L, "T-001");
        assertThat(snoozed.getDueTime()).isEqualTo(LocalTime.of(11, 0));
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(NOW, ZONE);
        }

        @Bean
        @Primary
        ZoneId testZone() {
            return ZONE;
        }
    }
}
