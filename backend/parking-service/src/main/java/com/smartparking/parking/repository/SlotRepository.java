package com.smartparking.parking.repository;

import com.smartparking.parking.entity.Slot;
import com.smartparking.parking.entity.enums.SlotStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SlotRepository extends JpaRepository<Slot, UUID> {

    long countByStatus(SlotStatus status);

    boolean existsBySlotCode(String slotCode);

    java.util.List<Slot> findByZoneOrderBySlotCodeAsc(String zone);

    /**
     * BR-003-2/3: pick one EMPTY slot to assign, skipping MAINTENANCE.
     * Row-locked + skip-locked so concurrent entries never grab the same slot.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT s FROM Slot s WHERE s.status = :status ORDER BY s.slotCode ASC")
    Optional<Slot> findFirstAvailable(@Param("status") SlotStatus status, Limit limit);
}
