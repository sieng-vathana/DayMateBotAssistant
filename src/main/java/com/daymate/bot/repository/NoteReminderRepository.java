package com.daymate.bot.repository;

import com.daymate.bot.entity.NoteReminderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NoteReminderRepository extends JpaRepository<NoteReminderEntity, Long> {
    List<NoteReminderEntity> findByNoteIdAndTelegramUserIdAndStatus(Long noteId, Long telegramUserId, String status);

    List<NoteReminderEntity> findTop50ByStatusAndRemindAtLessThanEqualOrderByRemindAtAsc(String status, Instant remindAt);

    Optional<NoteReminderEntity> findByIdAndTelegramUserId(Long id, Long telegramUserId);
}
