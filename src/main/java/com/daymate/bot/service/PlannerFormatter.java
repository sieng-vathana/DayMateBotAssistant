package com.daymate.bot.service;

import com.daymate.bot.i18n.Texts;
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
        return Texts.t("""
                👋 Welcome to DayMate

                I record overtime and show your next payday.

                /ot — Record overtime
                /otlist — Overtime this month
                /ot-history — OT by month
                /salary — Hourly pay and base salary
                /upcoming_salary — Next payday
                /language — Switch language
                /help — Show this help message

                Let's make today productive! 💪""", """
                👋 សូមស្វាគមន៍មកកាន់ DayMate

                ខ្ញុំកត់ត្រាម៉ោងបន្ថែម និងបង្ហាញថ្ងៃបើកប្រាក់ខែបន្ទាប់។

                /ot — កត់ម៉ោងបន្ថែម
                /otlist — ម៉ោងបន្ថែមខែនេះ
                /ot-history — ម៉ោងបន្ថែមតាមខែ
                /salary — ប្រាក់ម៉ោង និងប្រាក់ខែគោល
                /upcoming_salary — ថ្ងៃបើកប្រាក់ខែបន្ទាប់
                /language — ប្ដូរភាសា
                /help — ជំនួយ

                សូមធ្វើឱ្យថ្ងៃនេះមានផលិតភាព! 💪""");
    }

    public String help() {
        return Texts.t("""
                ❓ DayMate Help

                /ot or /ot-record — Record overtime (set salary first)
                /otlist or /ot-list — Overtime records for this month
                /ot-history — Pick a month, or /ot-history 2026-10
                /del 3 — Delete OT #3
                /salary — Hourly pay and base salary
                /upcoming_salary — Next payday
                /language — English or Khmer
                /help — This message

                /ot saves in one line. Day first:
                24 08:30-17:30
                24 08:30-17:30 holiday

                Weekday = Evening 150%. Sat/Sun = Weekend 200%.
                Add holiday for Holiday 100%.
                Or tap a day, then one time button.

                Overtime uses Phnom Penh time.
                A 5-day week treats Saturday and Sunday as weekend.
                A shift of 6 hours or more includes a 1 hour break.
                Paydays for the suggested schedule are day 12 and day 26.""", """
                ❓ ជំនួយ DayMate

                /ot ឬ /ot-record — កត់ម៉ោងបន្ថែម (កំណត់ប្រាក់ខែសិន)
                /otlist ឬ /ot-list — កំណត់ត្រាខែនេះ
                /ot-history — ជ្រើសខែ ឬ /ot-history 2026-10
                /del 3 — លុប OT #3
                /salary — ប្រាក់ម៉ោង និងប្រាក់ខែគោល
                /upcoming_salary — ថ្ងៃបើកប្រាក់ខែបន្ទាប់
                /language — អង់គ្លេស ឬ ខ្មែរ
                /help — សារនេះ

                /ot រក្សាទុកក្នុងមួយជួរ។ ថ្ងៃមុនគេ៖
                24 08:30-17:30
                24 08:30-17:30 holiday

                ថ្ងៃធ្វើការ = ល្ងាច 150%។ សៅរ៍/អាទិត្យ = ចុងសប្តាហ៍ 200%។
                បន្ថែម holiday សម្រាប់ថ្ងៃឈប់សម្រាក 100%។
                ឬចុចថ្ងៃ បន្ទាប់មកចុចម៉ោងមួយ។

                ម៉ោងតាមម៉ោងភ្នំពេញ។
                សប្តាហ៍ 5 ថ្ងៃ ចាត់សៅរ៍ និងអាទិត្យជាចុងសប្តាហ៍។
                ការងារ 6 ម៉ោងឡើងទៅ រួមម៉ោងសម្រាក 1 ម៉ោង។
                ថ្ងៃបើកប្រាក់ខែដែលស្នើគឺថ្ងៃទី 12 និង 26។""");
    }

    public String languagePrompt() {
        return "🌐 Language / ភាសា\n\nChoose one / ជ្រើសមួយ៖";
    }

    public String languageSaved() {
        return Texts.t("Language set to English.", "បានប្ដូរទៅភាសាខ្មែរ។");
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
        return Texts.t(
                "I don't know that command. Send /help to see what I can do.",
                "ខ្ញុំមិនស្គាល់ពាក្យបញ្ជានេះទេ។ ផ្ញើ /help ដើម្បីមើលអ្វីដែលខ្ញុំធ្វើបាន។");
    }

    public String notAllowed(Long userId) {
        return Texts.t(
                "This bot is not configured for your account yet.\nYour user ID: " + userId,
                "បូតនេះមិនទាន់អនុញ្ញាតគណនីអ្នកទេ។\nលេខសម្គាល់របស់អ្នក៖ " + userId);
    }

    public String userId(Long userId) {
        return Texts.t(
                "Your Telegram user ID is: " + userId,
                "លេខសម្គាល់ Telegram របស់អ្នកគឺ៖ " + userId);
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
