package com.smartparking.parking.repository;

import com.smartparking.parking.entity.Gate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GateRepository extends JpaRepository<Gate, UUID> {

    /** Resolve the gate referenced by an event's gateId (e.g. GATE_ENTRY_01). */
    Optional<Gate> findByGateCode(String gateCode);
    java.util.List<Gate> findByFloorId(UUID floorId);
}
