package com.daymate.bot.dto.response;

import java.time.Instant;

public record BotStatusResponse(
        boolean telegramTokenConfigured,
        String timezone,
        Instant startedAt,
        long uptimeSeconds) {
}
