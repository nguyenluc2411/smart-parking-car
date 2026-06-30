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
 * Consumes every Dead Letter Topic ({@code *.DLT}) via pattern subscription and records a CRITICAL
 * audit alert (BR-007-1). A separate consumer group keeps DLT subscription independent of the
 * business-topic listener's rebalance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterAuditListener {

    private final AuditService auditService;

    @KafkaListener(
            topicPattern = "${app.kafka.dlt-pattern}",
            groupId = "${spring.kafka.consumer.group-id}-dlt")
    public void onDeadLetter(
            @Payload(required = false) String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        auditService.recordDeadLetter(topic, key, payload);
    }
}
