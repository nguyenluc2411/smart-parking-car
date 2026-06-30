package com.smartparking.parking.repository;

import com.smartparking.parking.entity.Alert;
import com.smartparking.parking.entity.enums.AlertStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {

    /** Newest-first, optionally filtered by status — backs GET /api/v1/alerts. */
    Page<Alert> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Alert> findByStatusOrderByCreatedAtDesc(AlertStatus status, Pageable pageable);
}
