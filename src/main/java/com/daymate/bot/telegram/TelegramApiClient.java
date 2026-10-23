package com.daymate.bot.telegram;

import com.daymate.bot.config.BotProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TelegramApiClient {
    private final String token;
    private final int pollTimeoutSeconds;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public TelegramApiClient(BotProperties properties, ObjectMapper objectMapper) {
        this.token = properties.getToken() == null ? "" : properties.getToken().trim();
        this.pollTimeoutSeconds = Math.max(1, Math.min(properties.getPollTimeoutSeconds(), 50));
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return !token.isBlank();
    }

    public JsonNode getUpdates(long offset) {
        String query = "timeout=" + pollTimeoutSeconds + "&allowed_updates=%5B%22message%22%2C%22callback_query%22%5D"
                + (offset >= 0 ? "&offset=" + offset : "");
        return call("getUpdates?" + query, null, Duration.ofSeconds(pollTimeoutSeconds + 15L));
    }

    public void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    public void sendMessage(Long chatId, String text, List<List<Map<String, String>>> keyboard) {
        send(chatId, text, keyboard, false);
    }

    public void sendHtml(Long chatId, String text, List<List<Map<String, String>>> keyboard) {
        send(chatId, text, keyboard, true);
    }

    private void send(Long chatId, String text, List<List<Map<String, String>>> keyboard, boolean html) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", chatId);
        payload.put("text", clip(text));
        if (html) {
            payload.put("parse_mode", "HTML");
        }
        if (keyboard != null) {
            payload.put("reply_markup", Map.of("inline_keyboard", keyboard));
        }
        call("sendMessage", payload, Duration.ofSeconds(15));
    }

    public void editMessage(Long chatId, Long messageId, String text, List<List<Map<String, String>>> keyboard) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", chatId);
        payload.put("message_id", messageId);
        payload.put("text", clip(text));
        if (keyboard != null) {
            payload.put("reply_markup", Map.of("inline_keyboard", keyboard));
        }
        call("editMessageText", payload, Duration.ofSeconds(15));
    }

    public void answerCallback(String callbackQueryId, String text, boolean showAlert) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("callback_query_id", callbackQueryId);
        if (text != null && !text.isBlank()) {
            payload.put("text", clip(text, 200));
        }
        payload.put("show_alert", showAlert);
        call("answerCallbackQuery", payload, Duration.ofSeconds(15));
    }

    private JsonNode call(String method, Object payload, Duration timeout) {
        if (!isConfigured()) {
            throw new TelegramApiException("Telegram bot token is not configured.");
        }
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + token + "/" + method))
                    .timeout(timeout);
            if (payload == null) {
                requestBuilder.GET();
            } else {
                requestBuilder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            }
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TelegramApiException("Telegram API returned HTTP " + response.statusCode() + ".");
            }
            JsonNode body = objectMapper.readTree(response.body());
            if (!body.path("ok").asBoolean(false)) {
                String description = body.path("description").asText("Telegram API request failed.");
                if (description.toLowerCase().contains("message is not modified")) {
                    return body;
                }
                throw new TelegramApiException(description);
            }
            return body;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TelegramApiException("Telegram API request was interrupted.");
        } catch (IOException | IllegalArgumentException exception) {
            throw new TelegramApiException("Could not communicate with the Telegram API.");
        }
    }

    private static String clip(String text) {
        return clip(text, 4096);
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "…";
    }

    public static class TelegramApiException extends RuntimeException {
        public TelegramApiException(String message) {
            super(message);
        }
    }
}
