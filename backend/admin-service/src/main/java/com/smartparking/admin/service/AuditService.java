package com.smartparking.admin.service;

/**
 * Persists the audit trail from consumed Kafka events.
 *
 * <p>Contract only — no business logic, no {@code @Transactional} here (CLAUDE.md §6.4).
 */
public interface AuditService {

    /**
     * Record a business event as an audit-log entry. The action, target entity and source service
     * are derived from {@code topic}; {@code key} becomes the target id and {@code payload} is
     * stored verbatim.
     */
    void recordEvent(String topic, String key, String payload);

    /**
     * Record a message that landed on a Dead Letter Topic (BR-007-1 CRITICAL alert) and log it.
     */
    void recordDeadLetter(String dltTopic, String key, String payload);

    /**
     * Record a local user-initiated action (e.g. login/logout — BR-007-3) with {@code source_service
     * = admin-service}.
     */
    void recordUserAction(java.util.UUID userId, String action, String targetEntity, String targetId,
                          String payload);
}
