package com.daymate.bot.telegram;

import com.daymate.bot.entity.TaskEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class InlineKeyboards {
    private InlineKeyboards() {
    }

    public static List<List<Map<String, String>>> today(List<TaskEntity> openTasks) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        int limit = Math.min(openTasks.size(), 12);
        for (int i = 0; i < limit; i++) {
            TaskEntity task = openTasks.get(i);
            String id = task.getPublicId();
            rows.add(List.of(
                    button("▶ " + id, "t:start:" + id),
                    button("✅ " + id, "t:complete:" + id),
                    button("⏰ " + id, "t:snooze:" + id),
                    button("🗑 " + id, "t:delete:" + id)));
        }
        rows.add(List.of(
                button("Mark all done", "nav:all_done"),
                button("Add task", "nav:add"),
                button("Refresh", "nav:refresh")));
        return rows;
    }

    public static List<List<Map<String, String>>> emptyToday() {
        return List.of(List.of(
                button("Add task", "nav:add"),
                button("Refresh", "nav:refresh")));
    }

    public static List<List<Map<String, String>>> categories() {
        return List.of(List.of(
                button("Work", "flow:cat:WORK"),
                button("Personal", "flow:cat:PERSONAL"),
                button("Health", "flow:cat:HEALTH")));
    }

    public static List<List<Map<String, String>>> priorities() {
        return List.of(List.of(
                button("High", "flow:pri:HIGH"),
                button("Medium", "flow:pri:MEDIUM"),
                button("Low", "flow:pri:LOW")));
    }

    public static List<List<Map<String, String>>> recurrences() {
        return List.of(List.of(
                button("None", "flow:recur:NONE"),
                button("Daily", "flow:recur:DAILY"),
                button("Weekly", "flow:recur:WEEKLY")));
    }

    public static List<List<Map<String, String>>> donePicker(List<TaskEntity> tasks) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        int limit = Math.min(tasks.size(), 20);
        for (int i = 0; i < limit; i++) {
            TaskEntity task = tasks.get(i);
            rows.add(List.of(button(truncate(task.getPublicId() + " " + task.getTitle(), 40),
                    "t:pick:" + task.getPublicId())));
        }
        return rows;
    }

    public static List<List<Map<String, String>>> afterSave() {
        return List.of(List.of(
                button("Today", "nav:today"),
                button("Add another", "nav:add")));
    }

    private static Map<String, String> button(String text, String data) {
        return Map.of("text", text, "callback_data", data);
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
