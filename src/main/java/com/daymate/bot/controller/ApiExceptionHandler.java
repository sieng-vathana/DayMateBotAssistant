package com.daymate.bot.controller;

import com.daymate.bot.core.access.AccessDeniedException;
import com.daymate.bot.dto.response.ApiErrorResponse;
import com.daymate.bot.telegram.TelegramApiClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> badRequest(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> forbidden(AccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "That action is currently unavailable.");
    }

    @ExceptionHandler(TelegramApiClient.TelegramApiException.class)
    public ResponseEntity<ApiErrorResponse> telegramFailure(TelegramApiClient.TelegramApiException exception) {
        return response(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message));
    }
}
