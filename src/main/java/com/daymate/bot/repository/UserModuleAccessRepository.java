package com.daymate.bot.repository;

import com.daymate.bot.entity.UserModuleAccessEntity;
import com.daymate.bot.entity.UserModuleAccessId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserModuleAccessRepository extends JpaRepository<UserModuleAccessEntity, UserModuleAccessId> {
}
