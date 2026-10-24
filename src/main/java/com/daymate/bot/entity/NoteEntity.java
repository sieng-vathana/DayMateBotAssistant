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
@Table(name = "notes", indexes = {
        @Index(name = "idx_notes_user_list", columnList = "telegram_user_id, deleted_at, pinned, created_at, id")
})
public class NoteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected NoteEntity() {
    }

    public NoteEntity(Long telegramUserId, String title, String content, boolean pinned, Instant createdAt) {
        this.telegramUserId = telegramUserId;
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTelegramUserId() {
        return telegramUserId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
