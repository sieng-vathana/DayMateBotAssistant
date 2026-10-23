package com.daymate.bot.poller;

import com.daymate.bot.dto.request.TelegramUpdateRequest;
import com.daymate.bot.entity.BotStateEntity;
import com.daymate.bot.repository.BotStateRepository;
import com.daymate.bot.router.CommandRouter;
import com.daymate.bot.telegram.TelegramApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class BotPoller {
    private static final Logger log = LoggerFactory.getLogger(BotPoller.class);
    private static final String OFFSET_KEY = "telegram-update-offset";

    private final TelegramApiClient telegram;
    private final CommandRouter commandRouter;
    private final BotStateRepository stateRepository;
    private final ObjectMapper objectMapper;
    private long nextOffset = -1;
    private boolean offsetLoaded;

    public BotPoller(TelegramApiClient telegram, CommandRouter commandRouter,
                     BotStateRepository stateRepository, ObjectMapper objectMapper) {
        this.telegram = telegram;
        this.commandRouter = commandRouter;
        this.stateRepository = stateRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${telegram.bot.poll-delay-ms:1000}")
    public void poll() {
        if (!telegram.isConfigured()) {
            return;
        }
        loadOffset();
        try {
            JsonNode updates = telegram.getUpdates(nextOffset).path("result");
            for (JsonNode updateNode : updates) {
                TelegramUpdateRequest update = objectMapper.treeToValue(updateNode, TelegramUpdateRequest.class);
                try {
                    commandRouter.process(update);
                } catch (RuntimeException exception) {
                    log.warn("Could not process Telegram update {}: {}", update.updateId(), exception.getMessage());
                }
                if (update.updateId() != null) {
                    nextOffset = update.updateId() + 1;
                    persistOffset(nextOffset);
                }
            }
        } catch (TelegramApiClient.TelegramApiException exception) {
            log.warn("Telegram polling failed: {}", exception.getMessage());
        } catch (Exception exception) {
            log.error("Could not read Telegram updates.", exception);
        }
    }

    private void loadOffset() {
        if (offsetLoaded) {
            return;
        }
        nextOffset = stateRepository.findById(OFFSET_KEY)
                .map(state -> Long.parseLong(state.getStateValue()))
                .orElse(-1L);
        offsetLoaded = true;
    }

    private void persistOffset(long offset) {
        stateRepository.save(new BotStateEntity(OFFSET_KEY, Long.toString(offset), Instant.now()));
    }
}
