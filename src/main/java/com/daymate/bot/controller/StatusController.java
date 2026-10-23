package com.daymate.bot.controller;

import com.daymate.bot.config.BotProperties;
import com.daymate.bot.dto.response.BotStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/bot")
public class StatusController {
    private final BotProperties properties;
    private final Instant startedAt = Instant.now();

    public StatusController(BotProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/status")
    public BotStatusResponse status() {
        String token = properties.getToken();
        return new BotStatusResponse(
                token != null && !token.isBlank(),
                properties.getTimezone(),
                startedAt,
                Math.max(0, Instant.now().getEpochSecond() - startedAt.getEpochSecond()));
    }
}
