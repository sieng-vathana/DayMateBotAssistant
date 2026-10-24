package com.daymate.bot.portal;

import com.daymate.bot.core.access.AccessDecision;
import com.daymate.bot.core.access.AccessDeniedException;
import com.daymate.bot.core.access.FeatureAccessGuard;
import com.daymate.bot.domain.Recurrence;
import com.daymate.bot.domain.TaskCategory;
import com.daymate.bot.domain.TaskDue;
import com.daymate.bot.domain.TaskPriority;
import com.daymate.bot.dto.response.TaskResponse;
import com.daymate.bot.dto.response.TodayResponse;
import com.daymate.bot.entity.PortalUserEntity;
import com.daymate.bot.entity.TaskEntity;
import com.daymate.bot.modules.task.TasksModule;
import com.daymate.bot.save.TaskSaveService;
import com.daymate.bot.service.DueTimeParser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/portal/tasks")
public class PortalTaskController {
    private final TaskSaveService tasks;
    private final DueTimeParser dueTimeParser;
    private final FeatureAccessGuard access;

    public PortalTaskController(TaskSaveService tasks, DueTimeParser dueTimeParser, FeatureAccessGuard access) {
        this.tasks = tasks;
        this.dueTimeParser = dueTimeParser;
        this.access = access;
    }

    @GetMapping
    public List<TaskResponse> list(HttpServletRequest request) {
        Long userId = userId(request);
        require(userId, TasksModule.LIST);
        return tasks.all(userId).stream().map(TaskResponse::from).toList();
    }

    @GetMapping("/today")
    public TodayResponse today(HttpServletRequest request) {
        Long userId = userId(request);
        require(userId, TasksModule.TODAY);
        TaskSaveService.Agenda agenda = tasks.today(userId);
        return new TodayResponse(
                agenda.date(),
                agenda.doneCount(),
                agenda.totalCount(),
                agenda.remaining().stream().map(TaskResponse::from).toList(),
                agenda.completedToday().stream().map(TaskResponse::from).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(HttpServletRequest request, @RequestBody TaskBody body) {
        Long userId = userId(request);
        require(userId, TasksModule.CREATE);
        TaskCategory category = TaskCategory.parse(body.category()).orElseThrow(() ->
                new IllegalArgumentException("category must be Work, Personal, or Health"));
        TaskPriority priority = TaskPriority.parse(body.priority()).orElseThrow(() ->
                new IllegalArgumentException("priority must be High, Medium, or Low"));
        Recurrence recurrence = Recurrence.parse(body.recurrence() == null ? "NONE" : body.recurrence())
                .orElseThrow(() -> new IllegalArgumentException("recurrence must be None, Daily, or Weekly"));

        LocalDate date = parseDate(body.dueDate());
        var time = body.dueTime() == null || body.dueTime().isBlank()
                ? null
                : dueTimeParser.parseTime(body.dueTime()).orElseThrow(() ->
                        new IllegalArgumentException("dueTime must look like 17:00 or 5:00 PM"));
        TaskEntity created = tasks.create(
                userId, userId, body.title(), category, priority, new TaskDue(date, time), recurrence);
        return TaskResponse.from(created);
    }

    @PostMapping("/{publicId}/start")
    public TaskResponse start(HttpServletRequest request, @PathVariable String publicId) {
        Long userId = userId(request);
        require(userId, TasksModule.START);
        return TaskResponse.from(tasks.start(userId, publicId));
    }

    @PostMapping("/{publicId}/complete")
    public TaskResponse complete(HttpServletRequest request, @PathVariable String publicId) {
        Long userId = userId(request);
        require(userId, TasksModule.COMPLETE);
        return TaskResponse.from(tasks.complete(userId, publicId));
    }

    @PostMapping("/{publicId}/snooze")
    public TaskResponse snooze(HttpServletRequest request, @PathVariable String publicId) {
        Long userId = userId(request);
        require(userId, TasksModule.SNOOZE);
        return TaskResponse.from(tasks.snooze(userId, publicId));
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request, @PathVariable String publicId) {
        Long userId = userId(request);
        require(userId, TasksModule.DELETE);
        tasks.delete(userId, publicId);
    }

    private static Long userId(HttpServletRequest request) {
        PortalUserEntity user = (PortalUserEntity) request.getAttribute(PortalAuthFilter.USER);
        return PortalIdentity.requireLinkedTelegramUserId(user);
    }

    private void require(Long userId, String feature) {
        if (access.decide(userId, TasksModule.ID, feature) != AccessDecision.ALLOW) {
            throw new AccessDeniedException("Task management is not enabled for this account.");
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("dueDate must use YYYY-MM-DD format");
        }
    }

    public record TaskBody(String title, String category, String priority,
                           String dueDate, String dueTime, String recurrence) {
    }
}
