package com.daymate.bot.dto.request;

public record CreateTaskRequest(
        Long telegramUserId,
        Long chatId,
        String title,
        String category,
        String priority,
        String dueDate,
        String dueTime,
        String recurrence) {
}
