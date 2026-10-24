package com.daymate.bot.repository;

import com.daymate.bot.entity.NoteTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoteTagRepository extends JpaRepository<NoteTagEntity, Long> {
    List<NoteTagEntity> findByNoteIdOrderByIdAsc(Long noteId);
}
