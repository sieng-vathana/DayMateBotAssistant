package com.daymate.bot.dto.response;

import com.daymate.bot.entity.TaskEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record TaskResponse(
        Long id,
        String publicId,
        Long telegramUserId,
        Long chatId,
        String title,
        String category,
        String priority,
        String status,
        String recurrence,
        LocalDate dueDate,
        LocalTime dueTime,
        Instant createdAt,
        Instant completedAt) {

    public static TaskResponse from(TaskEntity task) {
        return new TaskResponse(
                task.getId(),
                task.getPublicId(),
                task.getTelegramUserId(),
                task.getChatId(),
                task.getTitle(),
                task.getCategory(),
                task.getPriority(),
                task.getStatus(),
                task.getRecurrence(),
                task.getDueDate(),
                task.getDueTime(),
                task.getCreatedAt(),
                task.getCompletedAt());
    }
}
