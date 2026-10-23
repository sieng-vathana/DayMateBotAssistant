package com.daymate.bot.service;

import com.daymate.bot.config.BotProperties;
import com.daymate.bot.save.TaskSaveService;
import com.daymate.bot.telegram.InlineKeyboards;
import com.daymate.bot.telegram.TelegramApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DailyDigestScheduler {
    private static final Logger log = LoggerFactory.getLogger(DailyDigestScheduler.class);

    private final BotProperties properties;
    private final TaskSaveService tasks;
    private final PlannerFormatter formatter;
    private final TelegramApiClient telegram;

    public DailyDigestScheduler(BotProperties properties, TaskSaveService tasks,
                                PlannerFormatter formatter, TelegramApiClient telegram) {
        this.properties = properties;
        this.tasks = tasks;
        this.formatter = formatter;
        this.telegram = telegram;
    }

    @Scheduled(cron = "${telegram.bot.digest-cron:0 0 8 * * *}", zone = "${telegram.bot.timezone:Asia/Bangkok}")
    public void sendDigest() {
        if (!properties.isDigestEnabled() || !telegram.isConfigured()) {
            return;
        }
        for (Object[] userChat : tasks.distinctUserChats()) {
            Long userId = (Long) userChat[0];
            Long chatId = (Long) userChat[1];
            TaskSaveService.Agenda agenda = tasks.today(userId);
            if (agenda.totalCount() == 0) {
                continue;
            }
            try {
                var keyboard = agenda.remaining().isEmpty()
                        ? InlineKeyboards.emptyToday()
                        : InlineKeyboards.today(agenda.remaining());
                telegram.sendMessage(chatId, formatter.today(agenda), keyboard);
            } catch (RuntimeException exception) {
                log.warn("Could not send daily digest to chat {}: {}", chatId, exception.getMessage());
            }
        }
    }
}
