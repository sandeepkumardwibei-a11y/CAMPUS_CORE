package com.campuscore.repository;

import com.campuscore.entity.ModuleLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModuleLogRepository extends JpaRepository<ModuleLog, Long> {
}