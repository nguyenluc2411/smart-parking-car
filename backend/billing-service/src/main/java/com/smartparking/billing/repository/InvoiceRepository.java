package com.smartparking.billing.repository;

import com.smartparking.billing.entity.Invoice;
import com.smartparking.billing.entity.enums.InvoiceStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    /** BR-004-6: one invoice per session — guard against duplicate session.closed events. */
    boolean existsBySessionId(UUID sessionId);

    /** Look up an invoice by its session for the payment + detail endpoints. */
    Optional<Invoice> findBySessionId(UUID sessionId);

    boolean existsByPayosOrderCode(Long payosOrderCode);

    Optional<Invoice> findByPayosOrderCode(Long payosOrderCode);

    /** Pessimistic lock for idempotent online settlement (webhook vs poll race). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.payosOrderCode = :orderCode")
    Optional<Invoice> findByPayosOrderCodeForUpdate(@Param("orderCode") Long orderCode);

    /**
     * BR-005-2: same lock for the operator (cash/QR-at-gate) path. Without it an incoming MoMo IPN
     * and an operator taking cash can both read PENDING and both settle the same invoice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.sessionId = :sessionId")
    Optional<Invoice> findBySessionIdForUpdate(@Param("sessionId") UUID sessionId);

    /** Invoices whose exit falls in [from, to) — for daily/monthly revenue reports. */
    @Query("SELECT i FROM Invoice i WHERE i.exitTime >= :from AND i.exitTime < :to")
    List<Invoice> findByExitTimeInRange(@Param("from") OffsetDateTime from,
                                        @Param("to") OffsetDateTime to);

    /** Driver "my invoices" — scoped to the verified plates in the JWT claim (ADR-010). */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.plateNumber IN :plates
              AND (:status IS NULL OR i.status = :status)
            """)
    Page<Invoice> searchByPlates(@Param("plates") List<String> plates,
                                 @Param("status") InvoiceStatus status,
                                 Pageable pageable);

    /**
     * Operator/admin invoice list filtered by an optional status, a plate substring (empty = all,
     * case-insensitive) and an exit-time range [from, to). The caller always supplies a concrete
     * plate ("" for none) and a range (wide when no date) — passing typed values avoids Postgres
     * "could not determine data type of parameter" on null binds; only status stays nullable.
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE (:status IS NULL OR i.status = :status)
              AND UPPER(i.plateNumber) LIKE UPPER(CONCAT('%', :plate, '%'))
              AND i.exitTime >= :from AND i.exitTime < :to
            """)
    Page<Invoice> search(@Param("status") InvoiceStatus status,
                         @Param("plate") String plate,
                         @Param("from") OffsetDateTime from,
                         @Param("to") OffsetDateTime to,
                         Pageable pageable);
}
