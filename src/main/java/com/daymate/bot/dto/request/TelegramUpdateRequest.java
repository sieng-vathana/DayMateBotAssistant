package com.daymate.bot.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramUpdateRequest(
        @JsonProperty("update_id") Long updateId,
        Message message,
        @JsonProperty("callback_query") CallbackQuery callbackQuery) {

    public record Message(
            @JsonProperty("message_id") Long messageId,
            Chat chat,
            User from,
            String text) {
    }

    public record Chat(Long id) {
    }

    public record User(
            Long id,
            @JsonProperty("first_name") String firstName,
            String username) {
    }

    public record CallbackQuery(
            String id,
            User from,
            String data,
            Message message) {
    }
}
