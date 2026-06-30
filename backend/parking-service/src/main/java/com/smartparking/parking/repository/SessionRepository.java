package com.smartparking.parking.repository;

import com.smartparking.parking.entity.Session;
import com.smartparking.parking.entity.enums.SessionStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {

    /** BR-002-4: a plate may have only one ACTIVE/PENDING session at a time. */
    boolean existsByPlateNumberAndStatusIn(String plateNumber, java.util.Collection<SessionStatus> statuses);

    /** Close-session: locate the single open session to close (BR-002-4 guarantees at most one). */
    Optional<Session> findByPlateNumberAndStatus(String plateNumber, SessionStatus status);

    /** BR-006-5 exit dedup: the most recently exited session for a plate (to detect a repeat scan). */
    Optional<Session> findFirstByPlateNumberAndStatusInOrderByExitTimeDesc(
            String plateNumber, java.util.Collection<SessionStatus> statuses);

    /** Ground truth for slot reconciliation (BR-003-4). */
    List<Session> findByStatus(SessionStatus status);

    /** Paged session search with optional filters ({@code GET /api/v1/sessions}). */
    @Query("""
            SELECT s FROM Session s
            WHERE (:status IS NULL OR s.status = :status)
              AND (:plate IS NULL OR s.plateNumber = :plate)
              AND s.entryTime >= :from
              AND s.entryTime < :to
            """)
    Page<Session> search(@Param("status") SessionStatus status,
                         @Param("plate") String plate,
                         @Param("from") OffsetDateTime from,
                         @Param("to") OffsetDateTime to,
                         Pageable pageable);

    /** Driver "my sessions" — scoped to the verified plates in the JWT claim (ADR-010). */
    @Query("""
            SELECT s FROM Session s
            WHERE s.plateNumber IN :plates
              AND (:status IS NULL OR s.status = :status)
            """)
    Page<Session> searchByPlates(@Param("plates") List<String> plates,
                                 @Param("status") SessionStatus status,
                                 Pageable pageable);
}
