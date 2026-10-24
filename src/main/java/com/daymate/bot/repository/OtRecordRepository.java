package com.daymate.bot.repository;

import com.daymate.bot.entity.OtRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OtRecordRepository extends JpaRepository<OtRecordEntity, Long> {
    List<OtRecordEntity> findByTelegramUserIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAscIdAsc(
            Long telegramUserId, LocalDate start, LocalDate end);

    List<OtRecordEntity> findByTelegramUserIdOrderByWorkDateDesc(Long telegramUserId);

    Optional<OtRecordEntity> findFirstByTelegramUserIdOrderByIdDesc(Long telegramUserId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OtRecordEntity o set o.telegramUserId = :toId, o.chatId = :chatId where o.telegramUserId = :fromId")
    int moveOwner(@Param("fromId") Long fromId, @Param("toId") Long toId, @Param("chatId") Long chatId);
}
