package com.smartparking.parking.sse;

import com.smartparking.parking.dto.response.AlertResponseDTO;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory fan-out of alerts to connected dashboards/operators over Server-Sent Events.
 *
 * <p>Single-instance (one parking-service) per the architecture, so an in-process emitter list is
 * sufficient — no external pub/sub needed. Each connected client holds one {@link SseEmitter}; a
 * failed send drops that emitter. History/replay is served by GET /api/v1/alerts, not this stream.
 */
@Slf4j
@Component
public class AlertSseBroker {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** Register a new client stream (never times out; removed on completion/error). */
    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(0L);   // 0 = no timeout
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        log.debug("SSE client registered ({} total)", emitters.size());
        return emitter;
    }

    /** Push an alert to every connected client; drop any emitter whose send fails. */
    public void broadcast(AlertResponseDTO alert) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("alert").data(alert, MediaType.APPLICATION_JSON));
            } catch (IOException | RuntimeException ex) {
                emitters.remove(emitter);
            }
        }
    }
}
