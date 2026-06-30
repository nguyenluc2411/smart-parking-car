package com.smartparking.parking.repository;

import com.smartparking.parking.entity.OutboxEvent;
import com.smartparking.parking.entity.enums.OutboxStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Oldest-first batch of unpublished events for the outbox poller. */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status ORDER BY e.createdAt ASC")
    List<OutboxEvent> findBatchByStatus(@Param("status") OutboxStatus status, Pageable pageable);
}
