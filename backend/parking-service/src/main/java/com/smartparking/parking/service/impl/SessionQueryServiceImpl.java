package com.smartparking.parking.service.impl;

import com.smartparking.parking.dto.response.PageResponseDTO;
import com.smartparking.parking.dto.response.SessionDetailResponseDTO;
import com.smartparking.parking.dto.response.SessionDetailResponseDTO.GateRef;
import com.smartparking.parking.dto.response.SessionDetailResponseDTO.SlotRef;
import com.smartparking.parking.dto.response.SessionSummaryResponseDTO;
import com.smartparking.parking.entity.Session;
import com.smartparking.parking.entity.Slot;
import com.smartparking.parking.entity.enums.SessionStatus;
import com.smartparking.parking.exception.ConflictException;
import com.smartparking.parking.exception.ForbiddenException;
import com.smartparking.parking.exception.ResourceNotFoundException;
import com.smartparking.parking.repository.GateRepository;
import com.smartparking.parking.repository.SessionRepository;
import com.smartparking.parking.repository.SlotRepository;
import com.smartparking.parking.service.ImageUrlService;
import com.smartparking.parking.service.SessionQueryService;
import com.smartparking.parking.util.PlateNumbers;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionQueryServiceImpl implements SessionQueryService {

    private final SessionRepository sessionRepository;
    private final SlotRepository slotRepository;
    private final GateRepository gateRepository;
    private final ImageUrlService imageUrlService;

    @Value("${app.parking.zone-id}")
    private String zoneId;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<SessionSummaryResponseDTO> search(SessionStatus status, LocalDate date,
                                                             String plate, int page, int size) {
        // Postgres can't infer the type of a null timestamp bind — use wide bounds when no date filter.
        OffsetDateTime from = OffsetDateTime.parse("1970-01-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("9999-12-31T23:59:59Z");
        if (date != null) {
            ZoneId zone = ZoneId.of(zoneId);
            from = date.atStartOfDay(zone).toOffsetDateTime();
            to = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        }
        String plateNorm = (plate == null || plate.isBlank()) ? null : PlateNumbers.normalize(plate);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "entryTime"));
        Page<Session> result = sessionRepository.search(status, plateNorm, from, to, pageable);

        Map<UUID, String> slotCodes = loadSlotCodes(result.getContent());
        List<SessionSummaryResponseDTO> content = result.getContent().stream()
                .map(s -> new SessionSummaryResponseDTO(
                        s.getId(), s.getPlateNumber(),
                        s.getSlotId() == null ? null : slotCodes.get(s.getSlotId()),
                        s.getEntryTime(), s.getExitTime(), s.getDurationSeconds(), s.getStatus()))
                .toList();
        return PageResponseDTO.of(result, content);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<SessionSummaryResponseDTO> searchForDriver(List<String> plates,
                                                                      SessionStatus status, int page, int size) {
        List<String> normalized = plates.stream()
                .map(PlateNumbers::normalize)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "entryTime"));
        if (normalized.isEmpty()) {
            // No verified plates yet -> nothing to show (and avoids an `IN ()` query).
            return new PageResponseDTO<>(List.of(), 0, 0, page, size);
        }
        Page<Session> result = sessionRepository.searchByPlates(normalized, status, pageable);

        Map<UUID, String> slotCodes = loadSlotCodes(result.getContent());
        List<SessionSummaryResponseDTO> content = result.getContent().stream()
                .map(s -> new SessionSummaryResponseDTO(
                        s.getId(), s.getPlateNumber(),
                        s.getSlotId() == null ? null : slotCodes.get(s.getSlotId()),
                        s.getEntryTime(), s.getExitTime(), s.getDurationSeconds(), s.getStatus()))
                .toList();
        return PageResponseDTO.of(result, content);
    }

    @Override
    @Transactional(readOnly = true)
    public SessionDetailResponseDTO getByIdForDriver(UUID id, List<String> plates) {
        Session s = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session", id.toString()));
        boolean owned = plates.stream()
                .map(PlateNumbers::normalize)
                .anyMatch(p -> p != null && p.equals(s.getPlateNumber()));
        if (!owned) {
            throw new ForbiddenException("Session does not belong to the authenticated driver");
        }
        return getById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public SessionDetailResponseDTO getById(UUID id) {
        Session s = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session", id.toString()));

        SlotRef slot = s.getSlotId() == null ? null : slotRepository.findById(s.getSlotId())
                .map(sl -> new SlotRef(sl.getId(), sl.getSlotCode(), sl.getZone()))
                .orElse(null);

        return new SessionDetailResponseDTO(
                s.getId(), s.getPlateNumber(), slot,
                gateRef(s.getEntryGateId()), gateRef(s.getExitGateId()),
                s.getEntryTime(), s.getExitTime(), s.getDurationSeconds(), s.getStatus(),
                imageUrlService.presignedGet(s.getEntryImageRef()),
                imageUrlService.presignedGet(s.getExitImageRef()),
                imageUrlService.presignedPlateCrop(s.getEntryImageRef()),
                imageUrlService.presignedPlateCrop(s.getExitImageRef()));
    }

    @Override
    @Transactional
    public SessionDetailResponseDTO resolve(UUID id, SessionStatus status, String note) {
        if (status != SessionStatus.CLOSED && status != SessionStatus.CANCELLED) {
            throw new IllegalArgumentException("resolve status must be CLOSED or CANCELLED");
        }
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session", id.toString()));
        if (session.getStatus() != SessionStatus.REQUIRES_ATTENTION) {
            throw new ConflictException(
                    "Session %s is not REQUIRES_ATTENTION (status=%s)".formatted(id, session.getStatus()));
        }
        session.setStatus(status);
        sessionRepository.save(session);
        // NOTE: resolve does NOT emit parking.session.closed — a REQUIRES_ATTENTION session has no
        // reliable entry/duration, so no invoice is generated even when resolved to CLOSED.
        log.info("Session {} resolved to {} by operator (note: {})", id, status, note);
        return getById(id);
    }

    private GateRef gateRef(UUID gateId) {
        if (gateId == null) {
            return null;
        }
        return gateRepository.findById(gateId)
                .map(g -> new GateRef(g.getId(), g.getGateCode()))
                .orElse(null);
    }

    private Map<UUID, String> loadSlotCodes(List<Session> sessions) {
        List<UUID> slotIds = sessions.stream()
                .map(Session::getSlotId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (slotIds.isEmpty()) {
            return Map.of();
        }
        return slotRepository.findAllById(slotIds).stream()
                .collect(Collectors.toMap(Slot::getId, Slot::getSlotCode));
    }
}
