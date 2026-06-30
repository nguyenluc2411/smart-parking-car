package com.smartparking.parking.service.impl;

import com.smartparking.parking.dto.request.CreateSlotRequestDTO;
import com.smartparking.parking.dto.request.ProvisionZoneRequestDTO;
import com.smartparking.parking.dto.request.UpdateSlotStatusRequestDTO;
import com.smartparking.parking.dto.response.ProvisionResultDTO;
import com.smartparking.parking.dto.response.SlotAvailabilityResponseDTO;
import com.smartparking.parking.dto.response.SlotResponseDTO;
import com.smartparking.parking.dto.response.SlotResyncResultDTO;
import com.smartparking.parking.entity.Session;
import com.smartparking.parking.entity.Slot;
import com.smartparking.parking.entity.enums.SessionStatus;
import com.smartparking.parking.entity.enums.SlotStatus;
import com.smartparking.parking.exception.ConflictException;
import com.smartparking.parking.exception.ResourceNotFoundException;
import com.smartparking.parking.repository.SessionRepository;
import com.smartparking.parking.repository.SlotRepository;
import com.smartparking.parking.service.SlotService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlotServiceImpl implements SlotService {

    private final SlotRepository slotRepository;
    private final SessionRepository sessionRepository;

    @Override
    @Transactional(readOnly = true)
    public SlotAvailabilityResponseDTO getAvailability() {
        long total = slotRepository.count();
        long occupied = slotRepository.countByStatus(SlotStatus.OCCUPIED);
        long empty = slotRepository.countByStatus(SlotStatus.EMPTY);
        long maintenance = slotRepository.countByStatus(SlotStatus.MAINTENANCE);

        double occupancyRate = total == 0 ? 0.0
                : BigDecimal.valueOf((double) occupied / total).setScale(2, RoundingMode.HALF_UP).doubleValue();

        return new SlotAvailabilityResponseDTO(total, occupied, empty, maintenance, occupancyRate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotResponseDTO> listSlots() {
        return slotRepository.findAll().stream()
                .sorted(Comparator.comparing(Slot::getSlotCode))
                .map(s -> new SlotResponseDTO(s.getId(), s.getSlotCode(), s.getZone(),
                        s.getStatus(), s.getCurrentSessionId()))
                .toList();
    }

    @Override
    @Transactional
    public SlotResyncResultDTO resync() {
        // Ground truth = ACTIVE sessions. Each ACTIVE session's slot must be OCCUPIED by it;
        // every other non-MAINTENANCE slot must be EMPTY. MAINTENANCE slots are never touched.
        Map<UUID, UUID> activeBySlot = sessionRepository.findByStatus(SessionStatus.ACTIVE).stream()
                .filter(s -> s.getSlotId() != null)
                .collect(Collectors.toMap(Session::getSlotId, Session::getId, (a, b) -> a));

        int corrected = 0;
        for (Slot slot : slotRepository.findAll()) {
            if (slot.getStatus() == SlotStatus.MAINTENANCE) {
                continue;
            }
            UUID expectedSession = activeBySlot.get(slot.getId());
            if (expectedSession != null) {
                if (slot.getStatus() != SlotStatus.OCCUPIED
                        || !expectedSession.equals(slot.getCurrentSessionId())) {
                    slot.setStatus(SlotStatus.OCCUPIED);
                    slot.setCurrentSessionId(expectedSession);
                    slotRepository.save(slot);
                    corrected++;
                }
            } else if (slot.getStatus() != SlotStatus.EMPTY || slot.getCurrentSessionId() != null) {
                slot.setStatus(SlotStatus.EMPTY);
                slot.setCurrentSessionId(null);
                slotRepository.save(slot);
                corrected++;
            }
        }
        log.info("Slot resync: {} slot(s) corrected against {} ACTIVE session(s)",
                corrected, activeBySlot.size());

        long total = slotRepository.count();
        return new SlotResyncResultDTO(total,
                slotRepository.countByStatus(SlotStatus.OCCUPIED),
                slotRepository.countByStatus(SlotStatus.EMPTY),
                slotRepository.countByStatus(SlotStatus.MAINTENANCE),
                corrected);
    }

    @Override
    @Transactional
    public SlotResponseDTO createSlot(CreateSlotRequestDTO request) {
        String code = request.slotCode().trim().toUpperCase(Locale.ROOT);
        String zone = request.zone().trim().toUpperCase(Locale.ROOT);
        if (slotRepository.existsBySlotCode(code)) {
            throw new ConflictException("Slot code already exists: " + code);
        }
        Slot slot = slotRepository.save(Slot.builder()
                .slotCode(code).zone(zone).status(SlotStatus.EMPTY).build());
        log.info("Slot created: {} (zone {})", code, zone);
        return toResponse(slot);
    }

    @Override
    @Transactional
    public void deleteSlot(UUID id) {
        Slot slot = slotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Slot", id.toString()));
        if (slot.getStatus() == SlotStatus.OCCUPIED) {
            throw new ConflictException("Cannot delete an OCCUPIED slot: " + slot.getSlotCode());
        }
        slotRepository.delete(slot);
        log.info("Slot deleted: {}", slot.getSlotCode());
    }

    @Override
    @Transactional
    public SlotResponseDTO updateStatus(UUID id, UpdateSlotStatusRequestDTO request) {
        SlotStatus target = request.status();
        // Only admin-controllable states; OCCUPIED is set by the entry/exit flow, never manually.
        if (target != SlotStatus.EMPTY && target != SlotStatus.MAINTENANCE) {
            throw new ConflictException("Slot status can only be set to EMPTY or MAINTENANCE");
        }
        Slot slot = slotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Slot", id.toString()));
        if (slot.getStatus() == SlotStatus.OCCUPIED) {
            throw new ConflictException(
                    "Cannot change an OCCUPIED slot (car parked): " + slot.getSlotCode());
        }
        slot.setStatus(target);
        slot.setCurrentSessionId(null);
        slotRepository.save(slot);
        log.info("Slot {} status set to {}", slot.getSlotCode(), target);
        return toResponse(slot);
    }

    @Override
    @Transactional
    public ProvisionResultDTO provisionZone(ProvisionZoneRequestDTO request) {
        String zone = request.zone().trim().toUpperCase(Locale.ROOT);
        int count = request.count();
        int pad = Math.max(2, Integer.toString(count).length());

        // Target codes: {zone}01..{zone}NN with consistent zero-padding.
        Set<String> targetCodes = new LinkedHashSet<>();
        for (int i = 1; i <= count; i++) {
            targetCodes.add(zone + leftPad(i, pad));
        }

        List<Slot> existing = slotRepository.findByZoneOrderBySlotCodeAsc(zone);
        Set<String> existingCodes = existing.stream().map(Slot::getSlotCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Refuse to remove a slot that currently has a car — report which ones block the change.
        List<String> blocked = existing.stream()
                .filter(s -> !targetCodes.contains(s.getSlotCode()))
                .filter(s -> s.getStatus() == SlotStatus.OCCUPIED)
                .map(Slot::getSlotCode)
                .toList();
        if (!blocked.isEmpty()) {
            throw new ConflictException(
                    "Cannot reduce zone " + zone + " — these slots still have a car: " + blocked);
        }

        int created = 0;
        for (String code : targetCodes) {
            if (!existingCodes.contains(code) && !slotRepository.existsBySlotCode(code)) {
                slotRepository.save(Slot.builder()
                        .slotCode(code).zone(zone).status(SlotStatus.EMPTY).build());
                created++;
            }
        }

        List<Slot> toRemove = existing.stream()
                .filter(s -> !targetCodes.contains(s.getSlotCode()))
                .toList();
        slotRepository.deleteAll(toRemove);

        long total = slotRepository.count();
        log.info("Zone {} provisioned to {} slot(s): +{} / -{} (total {})",
                zone, count, created, toRemove.size(), total);
        return new ProvisionResultDTO(zone, count, created, toRemove.size(), total);
    }

    private static String leftPad(int value, int width) {
        String s = Integer.toString(value);
        return "0".repeat(Math.max(0, width - s.length())) + s;
    }

    private SlotResponseDTO toResponse(Slot s) {
        return new SlotResponseDTO(s.getId(), s.getSlotCode(), s.getZone(),
                s.getStatus(), s.getCurrentSessionId());
    }
}
