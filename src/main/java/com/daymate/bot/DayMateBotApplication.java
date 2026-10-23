package com.daymate.bot;

import com.daymate.bot.config.BotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(BotProperties.class)
public class DayMateBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(DayMateBotApplication.class, args);
    }
}
