package com.daymate.bot.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TelegramCommandMenu implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TelegramCommandMenu.class);

    private final TelegramApiClient telegram;

    public TelegramCommandMenu(TelegramApiClient telegram) {
        this.telegram = telegram;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!telegram.isConfigured()) {
            return;
        }
        try {
            telegram.setMyCommands(commands());
        } catch (RuntimeException exception) {
            log.warn("Could not publish Telegram commands ({})", exception.getClass().getSimpleName());
        }
    }

    public static List<Map<String, String>> commands() {
        return List.of(
                command("menu", "Open the menu"),
                command("ot", "Record overtime"),
                command("otlist", "Overtime this month"),
                command("salary", "Hourly pay and base salary"),
                command("upcoming_salary", "Next payday"),
                command("note", "Save a note"),
                command("notes", "Notes menu"),
                command("my_notes", "My saved notes"),
                command("search", "Search notes"),
                command("pinned", "Pinned notes"),
                command("language", "Switch language"),
                command("help", "Show this help message"));
    }

    private static Map<String, String> command(String name, String description) {
        return Map.of("command", name, "description", description);
    }
}
