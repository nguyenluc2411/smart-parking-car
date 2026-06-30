package com.smartparking.admin.repository;

import com.smartparking.admin.entity.AuditLog;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /** Paged audit search with optional filters ({@code GET /api/v1/audit-logs}). */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:action IS NULL OR a.action = :action)
              AND (:userId IS NULL OR a.userId = :userId)
              AND a.createdAt >= :from
              AND a.createdAt < :to
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> search(@Param("action") String action,
                          @Param("userId") UUID userId,
                          @Param("from") OffsetDateTime from,
                          @Param("to") OffsetDateTime to,
                          Pageable pageable);
}
