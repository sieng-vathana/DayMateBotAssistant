package com.daymate.bot.repository;

import com.daymate.bot.entity.OtRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface OtRecordRepository extends JpaRepository<OtRecordEntity, Long> {
    @Query("select max(o.recordNumber) from OtRecordEntity o where o.telegramUserId = :userId")
    Integer findMaxRecordNumber(Long userId);

    List<OtRecordEntity> findByTelegramUserIdAndWorkDateBetweenOrderByWorkDateAscRecordNumberAsc(
            Long telegramUserId, LocalDate start, LocalDate end);
}
