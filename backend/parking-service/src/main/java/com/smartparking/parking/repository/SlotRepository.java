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

    @Query("""
            SELECT COUNT(s) FROM Slot s
            WHERE s.zoneId IN (
              SELECT z.id FROM ParkingZone z WHERE z.floorId IN (
                SELECT f.id FROM ParkingFloor f WHERE f.parkingLotId = :lotId
              )
            )
            """)
    long countForLot(@Param("lotId") UUID lotId);

    @Query("""
            SELECT COUNT(s) FROM Slot s
            WHERE s.status = :status AND s.zoneId IN (
              SELECT z.id FROM ParkingZone z WHERE z.floorId IN (
                SELECT f.id FROM ParkingFloor f WHERE f.parkingLotId = :lotId
              )
            )
            """)
    long countByStatusForLot(@Param("status") SlotStatus status, @Param("lotId") UUID lotId);

    boolean existsBySlotCode(String slotCode);

    java.util.List<Slot> findByZoneOrderBySlotCodeAsc(String zone);
    java.util.List<Slot> findByZoneIdOrderBySlotCodeAsc(UUID zoneId);
    boolean existsByZoneIdAndSlotCode(UUID zoneId, String slotCode);
    long countByZoneId(UUID zoneId);

    /**
     * BR-003-2/3: pick one EMPTY slot to assign, skipping MAINTENANCE.
     * Row-locked + skip-locked so concurrent entries never grab the same slot.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT s FROM Slot s WHERE s.status = :status ORDER BY s.slotCode ASC")
    Optional<Slot> findFirstAvailable(@Param("status") SlotStatus status, Limit limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM Slot s
            WHERE s.status = :status AND s.zoneId IN (
              SELECT z.id FROM ParkingZone z WHERE z.floorId IN (
                SELECT f.id FROM ParkingFloor f WHERE f.parkingLotId = :lotId
              )
            )
            ORDER BY s.slotCode ASC
            """)
    Optional<Slot> findFirstAvailableForLot(@Param("status") SlotStatus status,
                                            @Param("lotId") UUID lotId,
                                            Limit limit);

    @Query("""
            SELECT COUNT(s) > 0 FROM Slot s
            WHERE s.id = :slotId AND s.zoneId IN (
              SELECT z.id FROM ParkingZone z WHERE z.floorId IN (
                SELECT f.id FROM ParkingFloor f WHERE f.parkingLotId = :lotId
              )
            )
            """)
    boolean belongsToLot(@Param("slotId") UUID slotId, @Param("lotId") UUID lotId);

    /**
     * BR-009-10: claim the EXACT slot a driver picked off the map. Row-locked the same way as
     * {@link #findFirstAvailable} so a second driver racing for the same cell cannot also get it;
     * empty result means someone already took it since the driver's map was last fetched.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Slot s WHERE s.id = :id AND s.status = :status")
    Optional<Slot> findByIdAndStatus(@Param("id") UUID id, @Param("status") SlotStatus status);
}
