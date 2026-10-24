package com.daymate.bot.controller;

import com.daymate.bot.core.access.AccessDecision;
import com.daymate.bot.core.access.AccessDeniedException;
import com.daymate.bot.core.access.FeatureAccessGuard;
import com.daymate.bot.domain.Recurrence;
import com.daymate.bot.domain.TaskCategory;
import com.daymate.bot.domain.TaskDue;
import com.daymate.bot.domain.TaskPriority;
import com.daymate.bot.modules.task.TasksModule;
import com.daymate.bot.dto.request.CreateTaskRequest;
import com.daymate.bot.dto.response.TaskResponse;
import com.daymate.bot.dto.response.TodayResponse;
import com.daymate.bot.entity.TaskEntity;
import com.daymate.bot.save.TaskSaveService;
import com.daymate.bot.service.DueTimeParser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskSaveService tasks;
    private final DueTimeParser dueTimeParser;
    private final FeatureAccessGuard guard;

    public TaskController(TaskSaveService tasks, DueTimeParser dueTimeParser, FeatureAccessGuard guard) {
        this.tasks = tasks;
        this.dueTimeParser = dueTimeParser;
        this.guard = guard;
    }

    @GetMapping
    public List<TaskResponse> list(@RequestParam Long telegramUserId) {
        require(telegramUserId, TasksModule.LIST);
        return tasks.all(telegramUserId).stream().map(TaskResponse::from).toList();
    }

    @GetMapping("/today")
    public TodayResponse today(@RequestParam Long telegramUserId) {
        require(telegramUserId, TasksModule.TODAY);
        TaskSaveService.Agenda agenda = tasks.today(telegramUserId);
        return new TodayResponse(
                agenda.date(),
                agenda.doneCount(),
                agenda.totalCount(),
                agenda.remaining().stream().map(TaskResponse::from).toList(),
                agenda.completedToday().stream().map(TaskResponse::from).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@RequestBody CreateTaskRequest request) {
        if (request.telegramUserId() == null || request.chatId() == null) {
            throw new IllegalArgumentException("telegramUserId and chatId are required");
        }
        require(request.telegramUserId(), TasksModule.CREATE);
        TaskCategory category = TaskCategory.parse(request.category())
                .orElseThrow(() -> new IllegalArgumentException("category must be Work, Personal, or Health"));
        TaskPriority priority = TaskPriority.parse(request.priority())
                .orElseThrow(() -> new IllegalArgumentException("priority must be High, Medium, or Low"));
        Recurrence recurrence = Recurrence.parse(request.recurrence() == null ? "NONE" : request.recurrence())
                .orElseThrow(() -> new IllegalArgumentException("recurrence must be None, Daily, or Weekly"));
        TaskEntity saved = tasks.create(
                request.telegramUserId(),
                request.chatId(),
                request.title(),
                category,
                priority,
                parseDue(request),
                recurrence);
        return TaskResponse.from(saved);
    }

    @PostMapping("/{publicId}/start")
    public TaskResponse start(@PathVariable String publicId, @RequestParam Long telegramUserId) {
        require(telegramUserId, TasksModule.START);
        return TaskResponse.from(tasks.start(telegramUserId, publicId));
    }

    @PostMapping("/{publicId}/complete")
    public TaskResponse complete(@PathVariable String publicId, @RequestParam Long telegramUserId) {
        require(telegramUserId, TasksModule.COMPLETE);
        return TaskResponse.from(tasks.complete(telegramUserId, publicId));
    }

    @PostMapping("/{publicId}/snooze")
    public TaskResponse snooze(@PathVariable String publicId, @RequestParam Long telegramUserId) {
        require(telegramUserId, TasksModule.SNOOZE);
        return TaskResponse.from(tasks.snooze(telegramUserId, publicId));
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String publicId, @RequestParam Long telegramUserId) {
        require(telegramUserId, TasksModule.DELETE);
        tasks.delete(telegramUserId, publicId);
    }

    private void require(Long telegramUserId, String feature) {
        if (guard.decide(telegramUserId, TasksModule.ID, feature) != AccessDecision.ALLOW) {
            throw new AccessDeniedException("That action is currently unavailable.");
        }
    }

    private TaskDue parseDue(CreateTaskRequest request) {
        if (request.dueDate() == null && request.dueTime() == null) {
            return TaskDue.none();
        }
        LocalDate date = null;
        LocalTime time = null;
        if (request.dueDate() != null && !request.dueDate().isBlank()) {
            try {
                date = LocalDate.parse(request.dueDate().trim());
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("dueDate must be ISO-8601, for example 2026-10-23");
            }
        }
        if (request.dueTime() != null && !request.dueTime().isBlank()) {
            time = dueTimeParser.parseTime(request.dueTime())
                    .orElseThrow(() -> new IllegalArgumentException("dueTime must look like 17:00 or 5:00 PM"));
        }
        return new TaskDue(date, time);
    }
}
