package com.smartparking.parking.service.impl;

import com.smartparking.parking.dto.response.AlertResponseDTO;
import com.smartparking.parking.dto.response.PageResponseDTO;
import com.smartparking.parking.entity.Alert;
import com.smartparking.parking.entity.enums.AlertSeverity;
import com.smartparking.parking.entity.enums.AlertStatus;
import com.smartparking.parking.entity.enums.AlertType;
import com.smartparking.parking.exception.ResourceNotFoundException;
import com.smartparking.parking.repository.AlertRepository;
import com.smartparking.parking.service.AlertService;
import com.smartparking.parking.service.ImageUrlService;
import com.smartparking.parking.sse.AlertSseBroker;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;
    private final AlertSseBroker sseBroker;
    private final ImageUrlService imageUrlService;

    @Override
    public void raise(AlertType type, AlertSeverity severity, String plateNumber, String gateId,
                      UUID sessionId, String imageRef, String message) {
        Alert alert = alertRepository.save(Alert.builder()
                .alertType(type).severity(severity).plateNumber(plateNumber)
                .gateId(gateId).sessionId(sessionId).imageRef(imageRef)
                .message(message).status(AlertStatus.NEW).build());
        log.warn("ALERT {} [{}] plate={} gate={} — {}", type, severity, plateNumber, gateId, message);

        // Push to operators only AFTER the surrounding transaction commits — a rollback (e.g. an
        // operator manual-entry that hits the blacklist) must not flash a phantom alert.
        AlertResponseDTO dto = toResponse(alert);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sseBroker.broadcast(dto);
                }
            });
        } else {
            sseBroker.broadcast(dto);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<AlertResponseDTO> list(AlertStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Alert> result = (status == null)
                ? alertRepository.findAllByOrderByCreatedAtDesc(pageable)
                : alertRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        List<AlertResponseDTO> content = result.getContent().stream().map(this::toResponse).toList();
        return PageResponseDTO.of(result, content);
    }

    @Override
    @Transactional
    public AlertResponseDTO acknowledge(UUID id, UUID operatorId) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", id.toString()));
        if (alert.getStatus() != AlertStatus.ACKNOWLEDGED) {
            alert.setStatus(AlertStatus.ACKNOWLEDGED);
            alert.setAcknowledgedBy(operatorId);
            alert.setAcknowledgedAt(OffsetDateTime.now());
            alertRepository.save(alert);
            log.info("Alert {} acknowledged by {}", id, operatorId);
        }
        return toResponse(alert);
    }

    private AlertResponseDTO toResponse(Alert a) {
        return new AlertResponseDTO(a.getId(), a.getAlertType(), a.getSeverity(), a.getPlateNumber(),
                a.getGateId(), a.getSessionId(), imageUrlService.presignedGet(a.getImageRef()),
                a.getMessage(), a.getStatus(), a.getAcknowledgedBy(), a.getAcknowledgedAt(),
                a.getCreatedAt());
    }
}
