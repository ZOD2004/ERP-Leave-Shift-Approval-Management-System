package com.murali.repository;

import com.murali.entity.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;



@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    @Query("SELECT a FROM AuditLog a ORDER BY a.id DESC")
    List<AuditLog> findRecentLogs(Pageable pageable);
    @Query("SELECT COUNT(DISTINCT a.performedBy) FROM AuditLog a WHERE a.action = 'LOGIN' AND a.timestamp >= :startOfDay")
    long countUniqueLoginsSince(@Param("startOfDay") LocalDateTime startOfDay);
}
