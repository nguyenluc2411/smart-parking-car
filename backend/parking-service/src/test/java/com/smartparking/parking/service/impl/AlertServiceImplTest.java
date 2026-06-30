package com.smartparking.parking.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartparking.parking.dto.response.AlertResponseDTO;
import com.smartparking.parking.entity.Alert;
import com.smartparking.parking.entity.enums.AlertSeverity;
import com.smartparking.parking.entity.enums.AlertStatus;
import com.smartparking.parking.entity.enums.AlertType;
import com.smartparking.parking.repository.AlertRepository;
import com.smartparking.parking.service.ImageUrlService;
import com.smartparking.parking.sse.AlertSseBroker;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertServiceImplTest {

    @Mock private AlertRepository alertRepository;
    @Mock private AlertSseBroker sseBroker;
    @Mock private ImageUrlService imageUrlService;
    @InjectMocks private AlertServiceImpl service;

    @Test
    void raise_persistsAndBroadcasts() {
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> {
            Alert a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            a.setCreatedAt(OffsetDateTime.now());
            return a;
        });

        service.raise(AlertType.BLACKLIST_HIT, AlertSeverity.CRITICAL, "51F-12345",
                "GATE_ENTRY_01", null, "frames/x.jpg", "Xe blacklist");

        verify(alertRepository).save(any(Alert.class));
        // No active transaction in a unit test -> broadcast immediately.
        verify(sseBroker).broadcast(any(AlertResponseDTO.class));
        verify(imageUrlService).presignedGet("frames/x.jpg");
    }

    @Test
    void acknowledge_setsStatusAndStamp() {
        UUID id = UUID.randomUUID();
        UUID operator = UUID.randomUUID();
        Alert alert = Alert.builder().id(id).alertType(AlertType.UNMATCHED_EXIT)
                .severity(AlertSeverity.WARNING).message("x").status(AlertStatus.NEW).build();
        when(alertRepository.findById(id)).thenReturn(Optional.of(alert));

        AlertResponseDTO dto = service.acknowledge(id, operator);

        assertEquals(AlertStatus.ACKNOWLEDGED, dto.status());
        assertEquals(operator, alert.getAcknowledgedBy());
        verify(alertRepository).save(alert);
    }

    @Test
    void acknowledge_alreadyAcknowledged_isIdempotent() {
        UUID id = UUID.randomUUID();
        Alert alert = Alert.builder().id(id).alertType(AlertType.UNMATCHED_EXIT)
                .severity(AlertSeverity.WARNING).message("x")
                .status(AlertStatus.ACKNOWLEDGED).acknowledgedBy(UUID.randomUUID()).build();
        when(alertRepository.findById(id)).thenReturn(Optional.of(alert));

        service.acknowledge(id, UUID.randomUUID());

        // Already handled -> no second write.
        verify(alertRepository, never()).save(any());
    }

    @Test
    void list_withStatus_usesStatusQuery() {
        when(alertRepository.findByStatusOrderByCreatedAtDesc(any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.list(AlertStatus.NEW, 0, 20);

        verify(alertRepository).findByStatusOrderByCreatedAtDesc(any(), any());
        verify(alertRepository, never()).findAllByOrderByCreatedAtDesc(any());
    }

    @Test
    void list_noStatus_usesAllQuery() {
        when(alertRepository.findAllByOrderByCreatedAtDesc(any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.list(null, 0, 20);

        verify(alertRepository).findAllByOrderByCreatedAtDesc(any());
        verify(alertRepository, never()).findByStatusOrderByCreatedAtDesc(any(), any());
    }
}
