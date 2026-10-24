package com.daymate.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "note_reminders", indexes = {
        @Index(name = "idx_note_reminders_due", columnList = "status, remind_at")
})
public class NoteReminderEntity {
    public static final String PENDING = "PENDING";
    public static final String SENT = "SENT";
    public static final String CANCELLED = "CANCELLED";
    public static final String SKIPPED = "SKIPPED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "note_id", nullable = false)
    private Long noteId;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "remind_at", nullable = false)
    private Instant remindAt;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NoteReminderEntity() {
    }

    public NoteReminderEntity(Long noteId, Long telegramUserId, Long chatId, Instant remindAt, Instant createdAt) {
        this.noteId = noteId;
        this.telegramUserId = telegramUserId;
        this.chatId = chatId;
        this.remindAt = remindAt;
        this.status = PENDING;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getNoteId() {
        return noteId;
    }

    public Long getTelegramUserId() {
        return telegramUserId;
    }

    public Long getChatId() {
        return chatId;
    }

    public Instant getRemindAt() {
        return remindAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
