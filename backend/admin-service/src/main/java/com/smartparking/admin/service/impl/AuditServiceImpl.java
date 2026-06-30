package com.smartparking.admin.service.impl;

import com.smartparking.admin.entity.AuditLog;
import com.smartparking.admin.repository.AuditLogRepository;
import com.smartparking.admin.service.AuditService;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maps each consumed topic to an audit action / target entity / source service, then appends a row
 * to {@code audit_logs}. System events carry a NULL {@code user_id}.
 *
 * <p>Note: at-least-once delivery can produce duplicate audit rows (audit_logs has no event-id
 * dedup column in the schema). Accepted for RBL — the audit trail is append-only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    /** Per-topic audit metadata. Topics come from CLAUDE.md §5. */
    private record EventMeta(String action, String targetEntity, String sourceService) {
    }

    private static final Map<String, EventMeta> TOPIC_META = Map.of(
            "parking.plate.detected",     new EventMeta("PLATE_DETECTED", "Gate", "edge-agent"),
            "parking.gate.command",       new EventMeta("GATE_COMMAND", "Gate", "parking-service"),
            "parking.session.created",    new EventMeta("SESSION_CREATED", "Session", "parking-service"),
            "parking.session.closed",     new EventMeta("SESSION_CLOSED", "Session", "parking-service"),
            "billing.invoice.calculated", new EventMeta("INVOICE_CALCULATED", "Invoice", "billing-service"),
            "billing.payment.completed",  new EventMeta("PAYMENT_COMPLETED", "Invoice", "billing-service"));

    private static final String DLT_SUFFIX = ".DLT";

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public void recordEvent(String topic, String key, String payload) {
        EventMeta meta = metaFor(topic);
        auditLogRepository.save(AuditLog.builder()
                .userId(null)                       // system event
                .action(meta.action())
                .targetEntity(meta.targetEntity())
                .targetId(key)
                .payload(payload)
                .sourceService(meta.sourceService())
                .build());
        log.info("Audit: action={}, source={}, targetId={}", meta.action(), meta.sourceService(), key);
    }

    @Override
    @Transactional
    public void recordDeadLetter(String dltTopic, String key, String payload) {
        String originalTopic = dltTopic.endsWith(DLT_SUFFIX)
                ? dltTopic.substring(0, dltTopic.length() - DLT_SUFFIX.length())
                : dltTopic;
        EventMeta meta = metaFor(originalTopic);

        // BR-007-1: a DLT message is a CRITICAL alert.
        log.error("DLT ALERT: message dead-lettered on {} (origin={}, source={}, key={})",
                dltTopic, originalTopic, meta.sourceService(), key);

        auditLogRepository.save(AuditLog.builder()
                .userId(null)
                .action("DLT_ALERT")
                .targetEntity(originalTopic)
                .targetId(key)
                .payload(payload)
                .sourceService(meta.sourceService())
                .build());
    }

    @Override
    @Transactional
    public void recordUserAction(UUID userId, String action, String targetEntity, String targetId,
                                 String payload) {
        auditLogRepository.save(AuditLog.builder()
                .userId(userId)
                .action(action)
                .targetEntity(targetEntity)
                .targetId(targetId)
                .payload(payload)
                .sourceService("admin-service")
                .build());
        log.info("Audit: action={}, userId={}", action, userId);
    }

    private EventMeta metaFor(String topic) {
        EventMeta meta = TOPIC_META.get(topic);
        if (meta != null) {
            return meta;
        }
        // Defensive: unknown topic — derive a best-effort action, keep the trail.
        log.warn("Audit: unmapped topic '{}', recording with derived metadata", topic);
        String action = topic.toUpperCase().replace('.', '_');
        String source = topic.startsWith("billing.") ? "billing-service"
                : topic.startsWith("parking.") ? "parking-service" : "unknown";
        return new EventMeta(action, null, source);
    }
}
