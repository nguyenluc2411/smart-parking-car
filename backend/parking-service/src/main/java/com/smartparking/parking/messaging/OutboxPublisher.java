package com.smartparking.parking.messaging;

import com.smartparking.parking.entity.OutboxEvent;
import com.smartparking.parking.entity.enums.OutboxStatus;
import com.smartparking.parking.repository.OutboxEventRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional-outbox relay: polls {@code outbox_events} for PENDING rows and publishes each to
 * Kafka (topic = {@code event_type}, key = aggregateId for per-aggregate ordering). On success the
 * row is marked PUBLISHED; on failure {@code retry_count} is bumped and, past the cap, set FAILED.
 *
 * <p>The payload is already-serialized JSON, so it is sent as-is via the String producer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.outbox.batch-size}")
    private int batchSize;

    @Value("${app.outbox.max-retry}")
    private int maxRetry;

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch =
                outboxEventRepository.findBatchByStatus(OutboxStatus.PENDING, PageRequest.of(0, batchSize));
        if (batch.isEmpty()) {
            return;
        }
        log.debug("Outbox: publishing {} pending event(s)", batch.size());

        for (OutboxEvent event : batch) {
            try {
                // Block so the row status reliably reflects the broker ack.
                kafkaTemplate.send(event.getEventType(), event.getAggregateId().toString(), event.getPayload())
                        .get();
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(OffsetDateTime.now());
                log.info("Outbox: published {} (id={}, aggregateId={})",
                        event.getEventType(), event.getId(), event.getAggregateId());
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= maxRetry) {
                    event.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox: giving up on event id={} after {} retries",
                            event.getId(), event.getRetryCount(), ex);
                } else {
                    log.warn("Outbox: publish failed for event id={}, retry {}/{}",
                            event.getId(), event.getRetryCount(), maxRetry, ex);
                }
            }
        }
        // Managed entities flush on transaction commit.
    }
}
