package com.smartparking.parking.repository;

import com.smartparking.parking.entity.Reservation;
import com.smartparking.parking.entity.enums.ReservationStatus;
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
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /** BR-009-5: the one live booking for a plate, if any. */
    Optional<Reservation> findByPlateNumberAndStatus(String plateNumber, ReservationStatus status);

    Page<Reservation> findByDriverIdOrderByCreatedAtDesc(UUID driverId, Pageable pageable);

    long countByStatus(ReservationStatus status);

    /** BR-003-4: slots a live hold is sitting on, so resync leaves them alone. */
    @Query("SELECT r.slotId FROM Reservation r WHERE r.status = 'HELD'")
    List<UUID> findHeldSlotIds();

    /** BR-009-3: is this slot promised to someone right now? Guards admin edits of the slot. */
    boolean existsBySlotIdAndStatus(UUID slotId, ReservationStatus status);

    boolean existsBySlotId(UUID slotId);

    /** BR-009-2: holds whose grace has run out — swept back into the pool. */
    @Query("SELECT r FROM Reservation r WHERE r.status = 'HELD' AND r.holdUntil < :now")
    List<Reservation> findExpired(@Param("now") OffsetDateTime now);

    /**
     * BR-009-8: no-shows for a plate since {@code since}. A driver who repeatedly holds a slot and
     * never turns up is denying it to someone who would have paid, so it has to have a cost.
     */
    @Query("""
            SELECT COUNT(r) FROM Reservation r
            WHERE r.plateNumber = :plate AND r.status = 'EXPIRED' AND r.holdUntil >= :since
            """)
    long countNoShowsSince(@Param("plate") String plate, @Param("since") OffsetDateTime since);
}
