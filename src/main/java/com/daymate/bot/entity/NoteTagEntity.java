package com.daymate.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "note_tags", uniqueConstraints = {
        @UniqueConstraint(name = "uk_note_tags_note_name", columnNames = {"note_id", "tag_name"})
})
public class NoteTagEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "note_id", nullable = false)
    private Long noteId;

    @Column(name = "tag_name", nullable = false, length = 32)
    private String tagName;

    protected NoteTagEntity() {
    }

    public NoteTagEntity(Long noteId, String tagName) {
        this.noteId = noteId;
        this.tagName = tagName;
    }

    public Long getId() {
        return id;
    }

    public Long getNoteId() {
        return noteId;
    }

    public String getTagName() {
        return tagName;
    }
}
