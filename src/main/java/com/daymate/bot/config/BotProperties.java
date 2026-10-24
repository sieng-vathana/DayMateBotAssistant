package com.daymate.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "telegram.bot")
public class BotProperties {
    private String token = "";
    private String allowedUserIds = "";
    private String adminUserIds = "";
    private int pollTimeoutSeconds = 20;
    private long pollDelayMs = 1000;
    private String timezone = "Asia/Phnom_Penh";
    private boolean digestEnabled = false;
    private String digestCron = "0 0 8 * * *";

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getAllowedUserIds() {
        return allowedUserIds;
    }

    public void setAllowedUserIds(String allowedUserIds) {
        this.allowedUserIds = allowedUserIds;
    }

    public String getAdminUserIds() {
        return adminUserIds;
    }

    public void setAdminUserIds(String adminUserIds) {
        this.adminUserIds = adminUserIds;
    }

    public Set<Long> adminUserIds() {
        if (adminUserIds == null || adminUserIds.isBlank()) {
            return Set.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String value : adminUserIds.split(",")) {
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.valueOf(trimmed));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "TELEGRAM_ADMIN_USER_IDS must contain Telegram numeric user IDs.");
            }
        }
        return Set.copyOf(ids);
    }

    public int getPollTimeoutSeconds() {
        return pollTimeoutSeconds;
    }

    public void setPollTimeoutSeconds(int pollTimeoutSeconds) {
        this.pollTimeoutSeconds = pollTimeoutSeconds;
    }

    public long getPollDelayMs() {
        return pollDelayMs;
    }

    public void setPollDelayMs(long pollDelayMs) {
        this.pollDelayMs = pollDelayMs;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public boolean isDigestEnabled() {
        return digestEnabled;
    }

    public void setDigestEnabled(boolean digestEnabled) {
        this.digestEnabled = digestEnabled;
    }

    public String getDigestCron() {
        return digestCron;
    }

    public void setDigestCron(String digestCron) {
        this.digestCron = digestCron;
    }
}
