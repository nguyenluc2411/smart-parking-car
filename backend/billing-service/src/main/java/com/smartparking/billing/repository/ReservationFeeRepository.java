package com.smartparking.billing.repository;

import com.smartparking.billing.entity.ReservationFee;
import com.smartparking.billing.entity.enums.ReservationFeeStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationFeeRepository extends JpaRepository<ReservationFee, UUID> {

    boolean existsByPayosOrderCode(Long payosOrderCode);

    Optional<ReservationFee> findByReservationIdAndStatusIn(UUID reservationId,
                                                             List<ReservationFeeStatus> statuses);

    /** In practice at most one fee is ever created per reservation (reservations are one-shot). */
    Optional<ReservationFee> findByReservationId(UUID reservationId);

    /** Pessimistic lock for idempotent webhook settlement (same pattern as InvoiceRepository). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM ReservationFee f WHERE f.payosOrderCode = :orderCode")
    Optional<ReservationFee> findByPayosOrderCodeForUpdate(@Param("orderCode") Long orderCode);
}
