package com.daymate.bot.repository;

import com.daymate.bot.entity.SalaryProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaryProfileRepository extends JpaRepository<SalaryProfileEntity, Long> {
}
