package com.daymate.bot.repository;

import com.daymate.bot.entity.TaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<TaskEntity, Long> {
    Optional<TaskEntity> findByTelegramUserIdAndPublicId(Long telegramUserId, String publicId);

    List<TaskEntity> findByTelegramUserIdOrderByDueDateAscDueTimeAscIdAsc(Long telegramUserId);

    @Query("select distinct t.telegramUserId, t.chatId from TaskEntity t")
    List<Object[]> findDistinctUserChats();
}
