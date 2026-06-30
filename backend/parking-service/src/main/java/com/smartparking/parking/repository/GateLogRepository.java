package com.smartparking.parking.repository;

import com.smartparking.parking.entity.GateLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GateLogRepository extends JpaRepository<GateLog, UUID> {
}
