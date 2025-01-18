package com.dms.auth.repositories;


import com.dms.auth.entities.AuditLog;
import com.dms.auth.entities.interfaces.LoginAttemptStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    @Query(value = """
            SELECT DATE(a.timestamp) as date,
                   COUNT(CASE WHEN a.status = 'SUCCESS' THEN 1 END) as successCount,
                   COUNT(CASE WHEN a.status = 'FAILURE' THEN 1 END) as failureCount
            FROM audit_logs a
            WHERE a.action = 'LOGIN'
            AND a.timestamp >= :startDate
            GROUP BY DATE(a.timestamp)
            ORDER BY DATE(a.timestamp) DESC
            """, nativeQuery = true)
    List<LoginAttemptStats> getLoginAttemptStats(LocalDateTime startDate);
}
