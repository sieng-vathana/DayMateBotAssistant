package com.daymate.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram.bot")
public class BotProperties {
    private String token = "";
    private String allowedUserIds = "";
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
