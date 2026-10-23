package com.daymate.bot.save;

import com.daymate.bot.domain.Recurrence;
import com.daymate.bot.domain.TaskCategory;
import com.daymate.bot.domain.TaskDue;
import com.daymate.bot.domain.TaskPriority;
import com.daymate.bot.domain.TaskStatus;
import com.daymate.bot.entity.BotStateEntity;
import com.daymate.bot.entity.TaskEntity;
import com.daymate.bot.repository.BotStateRepository;
import com.daymate.bot.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class TaskSaveService {
    private final TaskRepository taskRepository;
    private final BotStateRepository stateRepository;
    private final Clock clock;
    private final ZoneId zoneId;

    public TaskSaveService(TaskRepository taskRepository, BotStateRepository stateRepository,
                           Clock clock, ZoneId zoneId) {
        this.taskRepository = taskRepository;
        this.stateRepository = stateRepository;
        this.clock = clock;
        this.zoneId = zoneId;
    }

    @Transactional
    public TaskEntity create(Long userId, Long chatId, String title, TaskCategory category,
                             TaskPriority priority, TaskDue due, Recurrence recurrence) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (category == null) {
            throw new IllegalArgumentException("category is required");
        }
        if (priority == null) {
            throw new IllegalArgumentException("priority is required");
        }
        Instant now = Instant.now(clock);
        TaskDue resolved = due == null ? TaskDue.none() : due;
        TaskEntity task = TaskEntity.create(
                nextPublicId(userId),
                userId,
                chatId,
                title.trim(),
                category,
                priority,
                resolved.date(),
                resolved.time(),
                recurrence == null ? Recurrence.NONE : recurrence,
                now);
        return taskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public TaskEntity require(Long userId, String publicId) {
        return taskRepository.findByTelegramUserIdAndPublicId(userId, normalizePublicId(publicId))
                .orElseThrow(() -> new IllegalArgumentException("Task " + publicId + " was not found."));
    }

    @Transactional
    public TaskEntity start(Long userId, String publicId) {
        TaskEntity task = requireOpen(userId, publicId);
        task.markInProgress(Instant.now(clock));
        return taskRepository.save(task);
    }

    @Transactional
    public TaskEntity complete(Long userId, String publicId) {
        TaskEntity task = require(userId, publicId);
        if (task.getStatusEnum() == TaskStatus.COMPLETED) {
            return task;
        }
        Instant now = Instant.now(clock);
        task.markCompleted(now);
        taskRepository.save(task);
        if (task.getRecurrenceEnum() == Recurrence.DAILY || task.getRecurrenceEnum() == Recurrence.WEEKLY) {
            spawnNext(task);
        }
        return task;
    }

    @Transactional
    public List<TaskEntity> completeAllOpenToday(Long userId) {
        List<TaskEntity> completed = new ArrayList<>();
        for (TaskEntity task : openToday(userId)) {
            completed.add(complete(userId, task.getPublicId()));
        }
        return completed;
    }

    @Transactional
    public TaskEntity snooze(Long userId, String publicId) {
        TaskEntity task = requireOpen(userId, publicId);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime currentDue = currentDue(task, now);
        LocalDateTime from = currentDue.isAfter(now) ? currentDue : now;
        LocalDateTime snoozed = from.plusHours(1);
        task.snoozeTo(snoozed.toLocalDate(), snoozed.toLocalTime().withSecond(0).withNano(0), Instant.now(clock));
        return taskRepository.save(task);
    }

    @Transactional
    public void delete(Long userId, String publicId) {
        TaskEntity task = require(userId, publicId);
        taskRepository.delete(task);
    }

    @Transactional(readOnly = true)
    public Agenda today(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<TaskEntity> remaining = new ArrayList<>();
        List<TaskEntity> completedToday = new ArrayList<>();
        for (TaskEntity task : taskRepository.findByTelegramUserIdOrderByDueDateAscDueTimeAscIdAsc(userId)) {
            if (task.getStatusEnum() == TaskStatus.COMPLETED) {
                if (completedOn(task, today)) {
                    completedToday.add(task);
                }
            } else if (onAgenda(task, today)) {
                remaining.add(task);
            }
        }
        remaining.sort(agendaOrder());
        completedToday.sort(Comparator.comparing(TaskEntity::getCompletedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return new Agenda(today, remaining, completedToday);
    }

    @Transactional(readOnly = true)
    public List<TaskEntity> pending(Long userId) {
        return taskRepository.findByTelegramUserIdOrderByDueDateAscDueTimeAscIdAsc(userId).stream()
                .filter(task -> task.getStatusEnum() != TaskStatus.COMPLETED)
                .sorted(agendaOrder())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskEntity> all(Long userId) {
        return taskRepository.findByTelegramUserIdOrderByDueDateAscDueTimeAscIdAsc(userId);
    }

    @Transactional(readOnly = true)
    public List<TaskEntity> recurring(Long userId) {
        return pending(userId).stream()
                .filter(task -> task.getRecurrenceEnum() != Recurrence.NONE)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Object[]> distinctUserChats() {
        return taskRepository.findDistinctUserChats();
    }

    public LocalDate todayDate() {
        return LocalDate.now(clock);
    }

    public Instant now() {
        return Instant.now(clock);
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    private List<TaskEntity> openToday(Long userId) {
        return today(userId).remaining();
    }

    private TaskEntity requireOpen(Long userId, String publicId) {
        TaskEntity task = require(userId, publicId);
        if (task.getStatusEnum() == TaskStatus.COMPLETED) {
            throw new IllegalArgumentException(task.getPublicId() + " is already completed.");
        }
        return task;
    }

    private void spawnNext(TaskEntity completed) {
        LocalDate base = completed.getDueDate() != null ? completed.getDueDate() : LocalDate.now(clock);
        LocalDate nextDate = completed.getRecurrenceEnum() == Recurrence.DAILY ? base.plusDays(1) : base.plusWeeks(1);
        Instant now = Instant.now(clock);
        TaskEntity next = TaskEntity.create(
                nextPublicId(completed.getTelegramUserId()),
                completed.getTelegramUserId(),
                completed.getChatId(),
                completed.getTitle(),
                completed.getCategoryEnum(),
                completed.getPriorityEnum(),
                nextDate,
                completed.getDueTime(),
                completed.getRecurrenceEnum(),
                now);
        taskRepository.save(next);
    }

    private String nextPublicId(Long userId) {
        String key = "task-seq-" + userId;
        int next = stateRepository.findById(key)
                .map(state -> Integer.parseInt(state.getStateValue()) + 1)
                .orElse(1);
        stateRepository.save(new BotStateEntity(key, Integer.toString(next), Instant.now(clock)));
        return "T-" + String.format(Locale.ROOT, "%03d", next);
    }

    private static String normalizePublicId(String publicId) {
        if (publicId == null) {
            throw new IllegalArgumentException("Task id is required.");
        }
        String trimmed = publicId.trim().toUpperCase(Locale.ROOT);
        if (trimmed.matches("\\d+")) {
            return "T-" + String.format(Locale.ROOT, "%03d", Integer.parseInt(trimmed));
        }
        if (trimmed.matches("T-\\d+")) {
            String number = trimmed.substring(2);
            return "T-" + String.format(Locale.ROOT, "%03d", Integer.parseInt(number));
        }
        return trimmed;
    }

    private boolean onAgenda(TaskEntity task, LocalDate today) {
        if (task.getStatusEnum() == TaskStatus.COMPLETED) {
            return false;
        }
        if (task.getDueDate() == null) {
            return true;
        }
        return !task.getDueDate().isAfter(today);
    }

    private boolean completedOn(TaskEntity task, LocalDate today) {
        if (task.getCompletedAt() == null) {
            return false;
        }
        return LocalDate.ofInstant(task.getCompletedAt(), zoneId).equals(today);
    }

    private LocalDateTime currentDue(TaskEntity task, LocalDateTime now) {
        LocalDate date = task.getDueDate() != null ? task.getDueDate() : now.toLocalDate();
        LocalTime time = task.getDueTime() != null ? task.getDueTime() : now.toLocalTime();
        return LocalDateTime.of(date, time);
    }

    private static Comparator<TaskEntity> agendaOrder() {
        return Comparator
                .comparing((TaskEntity task) -> task.getDueDate() == null)
                .thenComparing(TaskEntity::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TaskEntity::getDueTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TaskEntity::getPublicId);
    }

    public record Agenda(LocalDate date, List<TaskEntity> remaining, List<TaskEntity> completedToday) {
        public int doneCount() {
            return completedToday.size();
        }

        public int totalCount() {
            return remaining.size() + completedToday.size();
        }
    }
}
