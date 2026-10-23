package com.daymate.bot.service;

import com.daymate.bot.domain.Recurrence;
import com.daymate.bot.domain.TaskPriority;
import com.daymate.bot.domain.TaskStatus;
import com.daymate.bot.entity.TaskEntity;
import com.daymate.bot.save.TaskSaveService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class PlannerFormatter {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter COMPLETED_AT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    public String welcome() {
        return """
                👋 Welcome to DayMate — Your Daily Planner Bot

                I help you plan, track, and complete your daily tasks.

                Available commands:
                /today — View today's tasks
                /add — Add a new task
                /done — Mark a task as complete
                /list — List all pending tasks
                /ot — Record overtime
                /otlist — Overtime this month
                /salary — Base salary and paydays
                /help — Show this help message

                Let's make today productive! 💪""";
    }

    public String help() {
        return """
                ❓ DayMate Help

                /today — See today's agenda and progress
                /add — Create a task (title, category, priority, due time)
                /done — Mark a task complete
                /list — Pending + completed today
                /recurring — Daily and weekly repeating tasks
                /ot or /ot-record — Record overtime (set salary first)
                /otlist or /ot-list — Overtime records for this month
                /salary — Base salary, pay count, and payday
                /cancel — Cancel the current question
                /help — This message

                Overtime uses Phnom Penh time.
                ACLEDA works 5 days a week. Saturday and Sunday are Weekend at 200%.
                The hourly rate is monthly salary ÷ (5 × 52 ÷ 12 × 8). A $400 salary is $3.46/h at 150% and $4.62/h at 200%.
                A 6-day week divides by 208 hours, and only Sunday is Weekend at 200%.
                A shift of 6 hours or more includes a 1 hour break.
                ACLEDA paydays are day 12 and day 26.

                Tasks have:
                • ID (T-001)
                • Category: Work, Personal, Health
                • Priority: High, Medium, Low
                • Due time
                • Status: Pending, In Progress, Completed
                • Recurring: daily or weekly

                Tap the inline buttons under a task to Start, Complete, Snooze, or Delete it.""";
    }

    public String today(TaskSaveService.Agenda agenda) {
        if (agenda.totalCount() == 0) {
            return "📋 Today's Tasks — " + DAY.format(agenda.date())
                    + "\n\nNo tasks yet. Send /add to plan your day.";
        }
        StringBuilder text = new StringBuilder();
        text.append("📋 Today's Tasks — ").append(DAY.format(agenda.date())).append("\n\n");
        text.append("Progress: ").append(agenda.doneCount()).append("/").append(agenda.totalCount())
                .append(" done ✅\n");
        int index = 1;
        for (TaskEntity task : agenda.remaining()) {
            text.append('\n').append(index++).append(". ").append(taskLine(task)).append('\n');
            text.append("   ").append(taskMeta(task));
        }
        return text.toString();
    }

    public String list(List<TaskEntity> pending, List<TaskEntity> completedToday) {
        StringBuilder text = new StringBuilder("📝 All tasks\n");
        text.append("\nPending (").append(pending.size()).append(")");
        if (pending.isEmpty()) {
            text.append("\n• None");
        } else {
            for (TaskEntity task : pending) {
                text.append("\n• ").append(compactLine(task));
            }
        }
        text.append("\n\nCompleted today (").append(completedToday.size()).append(")");
        if (completedToday.isEmpty()) {
            text.append("\n• None");
        } else {
            for (TaskEntity task : completedToday) {
                text.append("\n• ").append(task.getPublicId()).append("  ").append(task.getTitle()).append("  ✅");
            }
        }
        return text.toString();
    }

    public String recurring(List<TaskEntity> tasks) {
        List<TaskEntity> daily = tasks.stream()
                .filter(task -> task.getRecurrenceEnum() == Recurrence.DAILY)
                .toList();
        List<TaskEntity> weekly = tasks.stream()
                .filter(task -> task.getRecurrenceEnum() == Recurrence.WEEKLY)
                .toList();
        if (daily.isEmpty() && weekly.isEmpty()) {
            return "🔁 Recurring tasks\n\nNone yet. When adding a task, choose Daily or Weekly on the last step.";
        }
        StringBuilder text = new StringBuilder("🔁 Recurring tasks\n");
        text.append("\nDaily");
        appendRecurringGroup(text, daily);
        text.append("\n\nWeekly");
        appendRecurringGroup(text, weekly);
        return text.toString();
    }

    public String addTitlePrompt() {
        return "➕ Add a new task\n\nWhat's the title of your task?";
    }

    public String addCategoryPrompt(String title) {
        return "Got it: " + title + "\n\nChoose a category:";
    }

    public String addPriorityPrompt(String categoryLabel) {
        return "Category: " + categoryLabel + "\n\nSet a priority:";
    }

    public String addDuePrompt(String priorityLabel) {
        return "Priority: " + priorityLabel + "\n\nWhen is this due? (e.g. 3:00 PM, tomorrow, or skip)";
    }

    public String addRecurrencePrompt() {
        return "Repeat this task?";
    }

    public String taskSaved(TaskEntity task) {
        return "✅ Task saved!\n\n"
                + task.getPublicId() + "  " + task.getTitle() + "\n"
                + task.getCategoryEnum().label() + " · " + task.getPriorityEnum().label()
                + " · " + dueLabel(task) + " · " + task.getStatusEnum().label();
    }

    public String taskCompleted(TaskEntity task, TaskSaveService.Agenda agenda, ZoneId zoneId) {
        String time = COMPLETED_AT.format(task.getCompletedAt().atZone(zoneId));
        return "🎉 Task completed!\n\n"
                + task.getPublicId() + "  " + task.getTitle() + "\n"
                + "Marked complete at " + time + "\n\n"
                + motivation(agenda.doneCount(), agenda.totalCount());
    }

    public String taskStarted(TaskEntity task) {
        return "▶ " + task.getPublicId() + " is now In Progress.\n" + task.getTitle();
    }

    public String taskSnoozed(TaskEntity task) {
        return "⏰ Snoozed " + task.getPublicId() + " until " + dueStamp(task.getDueDate(), task.getDueTime()) + ".";
    }

    public String taskDeleted(String publicId) {
        return "🗑 Deleted " + publicId + ".";
    }

    public String allDone(int count) {
        if (count == 0) {
            return "Nothing left to complete today.";
        }
        return "🎉 Marked " + count + " task" + (count == 1 ? "" : "s") + " complete. All done for today!";
    }

    public String donePickerEmpty() {
        return "Nothing to complete — your agenda is clear.";
    }

    public String donePicker() {
        return "Which task did you complete?";
    }

    public String cancelled() {
        return "Cancelled. Send /add when you want to create a task.";
    }

    public String unknownCommand() {
        return "I don't know that command. Send /help to see what I can do.";
    }

    public String notAllowed(Long userId) {
        return "This bot is not configured for your account yet.\n"
                + "Your user ID: " + userId + "\n"
                + "Set TELEGRAM_ALLOWED_USER_IDS to this ID and restart DayMate.";
    }

    public String userId(Long userId) {
        return "Your Telegram user ID is: " + userId;
    }

    public String invalidDue() {
        return "I could not read that time. Try 5:00 PM, 17:00, tomorrow, or skip.";
    }

    public String chooseCategory() {
        return "Please choose Work, Personal, or Health.";
    }

    public String choosePriority() {
        return "Please choose High, Medium, or Low.";
    }

    public String chooseRecurrence() {
        return "Please choose None, Daily, or Weekly.";
    }

    public String titleTooShort() {
        return "Please send a short title for the task.";
    }

    private String taskLine(TaskEntity task) {
        TaskPriority priority = task.getPriorityEnum();
        return priority.emoji() + " [" + task.getCategoryEnum().label() + "] " + task.getTitle();
    }

    private String taskMeta(TaskEntity task) {
        return "Due: " + dueStamp(task.getDueDate(), task.getDueTime())
                + " · " + task.getPriorityEnum().label()
                + " · " + task.getStatusEnum().label();
    }

    private String compactLine(TaskEntity task) {
        return task.getPublicId() + "  " + task.getTitle()
                + "  · " + task.getCategoryEnum().label()
                + " · " + task.getPriorityEnum().label()
                + " · " + dueStamp(task.getDueDate(), task.getDueTime());
    }

    private void appendRecurringGroup(StringBuilder text, List<TaskEntity> tasks) {
        if (tasks.isEmpty()) {
            text.append("\n• None");
            return;
        }
        for (TaskEntity task : tasks) {
            text.append("\n• ").append(task.getTitle())
                    .append("  · ").append(task.getCategoryEnum().label())
                    .append(" · ").append(dueStamp(task.getDueDate(), task.getDueTime()));
        }
    }

    private String dueLabel(TaskEntity task) {
        if (task.getDueDate() == null && task.getDueTime() == null) {
            return "No due time";
        }
        return "Due " + dueStamp(task.getDueDate(), task.getDueTime());
    }

    private String dueStamp(LocalDate date, LocalTime time) {
        if (date == null && time == null) {
            return "No due time";
        }
        if (time == null) {
            return DAY.format(date);
        }
        if (date == null) {
            return TIME.format(time);
        }
        return TIME.format(time);
    }

    private String motivation(int done, int total) {
        if (total > 0 && done >= total) {
            return "All done for today! 🎉";
        }
        if (done == 1 && total > 1) {
            return "Nice start — " + done + " of " + total + " done today!";
        }
        if (total > 0 && done * 2 >= total) {
            return "Halfway there — " + done + " of " + total + " done today!";
        }
        return "Keep going — " + done + " of " + total + " done today!";
    }
}
