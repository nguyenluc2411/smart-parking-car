package com.smartparking.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

import com.smartparking.admin.entity.AuditLog;
import com.smartparking.admin.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock private AuditLogRepository auditLogRepository;
    @InjectMocks private AuditServiceImpl service;

    private AuditLog captureSaved() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void recordEvent_sessionClosed_mapsActionSourceAndEntity() {
        service.recordEvent("parking.session.closed", "session-123", "{\"sessionId\":\"session-123\"}");

        AuditLog saved = captureSaved();
        assertEquals("SESSION_CLOSED", saved.getAction());
        assertEquals("Session", saved.getTargetEntity());
        assertEquals("parking-service", saved.getSourceService());
        assertEquals("session-123", saved.getTargetId());
        assertNull(saved.getUserId()); // system event
    }

    @Test
    void recordEvent_plateDetected_sourceIsEdgeAgent() {
        service.recordEvent("parking.plate.detected", "GATE_ENTRY_01", "{}");

        AuditLog saved = captureSaved();
        assertEquals("PLATE_DETECTED", saved.getAction());
        assertEquals("edge-agent", saved.getSourceService());
    }

    @Test
    void recordEvent_unmappedTopic_derivesMetadata() {
        service.recordEvent("billing.something.new", "k1", "{}");

        AuditLog saved = captureSaved();
        assertEquals("BILLING_SOMETHING_NEW", saved.getAction());
        assertEquals("billing-service", saved.getSourceService());
    }

    @Test
    void recordDeadLetter_stripsSuffixAndFlagsAlert() {
        service.recordDeadLetter("parking.session.closed.DLT", "session-9", "{}");

        AuditLog saved = captureSaved();
        assertEquals("DLT_ALERT", saved.getAction());
        assertEquals("parking.session.closed", saved.getTargetEntity());
        assertEquals("parking-service", saved.getSourceService());
        assertEquals("session-9", saved.getTargetId());
    }
}
