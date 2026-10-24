package com.daymate.bot.repository;

import com.daymate.bot.entity.OtRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface OtRecordRepository extends JpaRepository<OtRecordEntity, Long> {
    List<OtRecordEntity> findByTelegramUserIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAscIdAsc(
            Long telegramUserId, LocalDate start, LocalDate end);

    List<OtRecordEntity> findByTelegramUserIdOrderByWorkDateDesc(Long telegramUserId);
}
