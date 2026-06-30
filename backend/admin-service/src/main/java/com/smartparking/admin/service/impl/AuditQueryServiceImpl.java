package com.smartparking.admin.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.admin.dto.response.AuditLogResponseDTO;
import com.smartparking.admin.dto.response.PageResponseDTO;
import com.smartparking.admin.entity.AuditLog;
import com.smartparking.admin.entity.User;
import com.smartparking.admin.repository.AuditLogRepository;
import com.smartparking.admin.repository.UserRepository;
import com.smartparking.admin.service.AuditQueryService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditQueryServiceImpl implements AuditQueryService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private static final OffsetDateTime MIN_TIME = OffsetDateTime.parse("1970-01-01T00:00:00Z");
    private static final OffsetDateTime MAX_TIME = OffsetDateTime.parse("9999-12-31T23:59:59Z");

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<AuditLogResponseDTO> search(String action, UUID userId,
                                                       OffsetDateTime from, OffsetDateTime to,
                                                       int page, int size) {
        // Postgres can't infer the type of a null timestamp bind in `:from IS NULL` — use wide bounds.
        Page<AuditLog> result = auditLogRepository.search(action, userId,
                from != null ? from : MIN_TIME, to != null ? to : MAX_TIME,
                PageRequest.of(page, size));

        // Resolve usernames for the page in one query.
        List<UUID> userIds = result.getContent().stream()
                .map(AuditLog::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> usernames = userIds.isEmpty() ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername));

        List<AuditLogResponseDTO> content = result.getContent().stream()
                .map(a -> new AuditLogResponseDTO(
                        a.getId(), a.getUserId(),
                        a.getUserId() == null ? null : usernames.get(a.getUserId()),
                        a.getAction(), a.getTargetEntity(), a.getTargetId(),
                        parsePayload(a.getPayload()), a.getSourceService(), a.getCreatedAt()))
                .toList();
        return PageResponseDTO.of(result, content);
    }

    /** Stored payload is a JSON string; surface it as a JSON object (or null). */
    private Object parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            log.debug("Audit payload is not valid JSON, returning as string");
            return payload;
        }
    }
}
