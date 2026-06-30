package com.smartparking.admin.listener;

import com.smartparking.admin.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumes every business topic (CLAUDE.md §5) and appends an audit-log entry for each message.
 * The originating topic and message key are read from the record headers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventAuditListener {

    private final AuditService auditService;

    @KafkaListener(
            topics = "#{'${app.kafka.business-topics}'.split(',')}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onBusinessEvent(
            @Payload(required = false) String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        auditService.recordEvent(topic, key, payload);
    }
}
