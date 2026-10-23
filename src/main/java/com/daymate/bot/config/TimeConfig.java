package com.daymate.bot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {
    @Bean
    ZoneId zoneId(BotProperties properties) {
        return ZoneId.of(properties.getTimezone());
    }

    @Bean
    Clock clock(ZoneId zoneId) {
        return Clock.system(zoneId);
    }
}
