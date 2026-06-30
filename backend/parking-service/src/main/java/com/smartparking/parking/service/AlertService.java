package com.smartparking.parking.service;

import com.smartparking.parking.dto.response.AlertResponseDTO;
import com.smartparking.parking.entity.enums.AlertSeverity;
import com.smartparking.parking.entity.enums.AlertStatus;
import com.smartparking.parking.entity.enums.AlertType;
import com.smartparking.parking.dto.response.PageResponseDTO;
import java.util.UUID;

/** BR-007 business/security alerts: raise on anomalies, list for operators, acknowledge. */
public interface AlertService {

    /**
     * Record an anomaly and push it to connected operators (SSE). Joins the caller's transaction;
     * the SSE broadcast fires only after that transaction commits.
     */
    void raise(AlertType type, AlertSeverity severity, String plateNumber, String gateId,
               UUID sessionId, String imageRef, String message);

    /** Newest-first alerts, optionally filtered by status (null = all). */
    PageResponseDTO<AlertResponseDTO> list(AlertStatus status, int page, int size);

    /** Mark an alert handled by the given operator (idempotent). */
    AlertResponseDTO acknowledge(UUID id, UUID operatorId);
}
