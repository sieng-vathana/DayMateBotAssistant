package com.daymate.bot.entity;

import com.daymate.bot.domain.Recurrence;
import com.daymate.bot.domain.TaskCategory;
import com.daymate.bot.domain.TaskPriority;
import com.daymate.bot.domain.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
        name = "tasks",
        uniqueConstraints = @UniqueConstraint(name = "uk_task_user_public_id", columnNames = {"telegram_user_id", "public_id"})
)
public class TaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 16)
    private String publicId;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(nullable = false, length = 32)
    private String priority;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, length = 32)
    private String recurrence;

    private LocalDate dueDate;
    private LocalTime dueTime;
    private Instant createdAt;
    private Instant completedAt;
    private Instant updatedAt;

    protected TaskEntity() {
    }

    public static TaskEntity create(String publicId, Long telegramUserId, Long chatId, String title,
                                    TaskCategory category, TaskPriority priority, LocalDate dueDate,
                                    LocalTime dueTime, Recurrence recurrence, Instant now) {
        TaskEntity task = new TaskEntity();
        task.publicId = publicId;
        task.telegramUserId = telegramUserId;
        task.chatId = chatId;
        task.title = title;
        task.category = category.name();
        task.priority = priority.name();
        task.status = TaskStatus.PENDING.name();
        task.recurrence = recurrence == null ? Recurrence.NONE.name() : recurrence.name();
        task.dueDate = dueDate;
        task.dueTime = dueTime;
        task.createdAt = now;
        task.updatedAt = now;
        return task;
    }

    public void markInProgress(Instant now) {
        this.status = TaskStatus.IN_PROGRESS.name();
        this.updatedAt = now;
    }

    public void markCompleted(Instant now) {
        this.status = TaskStatus.COMPLETED.name();
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void snoozeTo(LocalDate date, LocalTime time, Instant now) {
        this.dueDate = date;
        this.dueTime = time;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public Long getTelegramUserId() {
        return telegramUserId;
    }

    public Long getChatId() {
        return chatId;
    }

    public String getTitle() {
        return title;
    }

    public TaskCategory getCategoryEnum() {
        return TaskCategory.valueOf(category);
    }

    public TaskPriority getPriorityEnum() {
        return TaskPriority.valueOf(priority);
    }

    public TaskStatus getStatusEnum() {
        return TaskStatus.valueOf(status);
    }

    public Recurrence getRecurrenceEnum() {
        return Recurrence.valueOf(recurrence);
    }

    public String getCategory() {
        return category;
    }

    public String getPriority() {
        return priority;
    }

    public String getStatus() {
        return status;
    }

    public String getRecurrence() {
        return recurrence;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalTime getDueTime() {
        return dueTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
