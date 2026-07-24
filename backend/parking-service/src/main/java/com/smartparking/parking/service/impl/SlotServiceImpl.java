package com.smartparking.parking.service.impl;

import com.smartparking.parking.dto.request.CreateSlotRequestDTO;
import com.smartparking.parking.dto.request.ProvisionZoneRequestDTO;
import com.smartparking.parking.dto.request.UpdateSlotStatusRequestDTO;
import com.smartparking.parking.dto.response.DriverSlotDTO;
import com.smartparking.parking.dto.response.ProvisionResultDTO;
import com.smartparking.parking.dto.response.SlotAvailabilityResponseDTO;
import com.smartparking.parking.dto.response.SlotResponseDTO;
import com.smartparking.parking.dto.response.SlotResyncResultDTO;
import com.smartparking.parking.entity.Session;
import com.smartparking.parking.entity.Slot;
import com.smartparking.parking.entity.enums.ReservationStatus;
import com.smartparking.parking.entity.enums.SessionStatus;
import com.smartparking.parking.entity.enums.SlotStatus;
import com.smartparking.parking.exception.ConflictException;
import com.smartparking.parking.exception.ResourceNotFoundException;
import com.smartparking.parking.repository.ReservationRepository;
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

    /** Slots per row when laying out a zone map (BR-003-6). */
    private static final int GRID_COLUMNS = 10;

    private final SlotRepository slotRepository;
    private final SessionRepository sessionRepository;
    private final ReservationRepository reservationRepository;

    @Override
    @Transactional(readOnly = true)
    public SlotAvailabilityResponseDTO getAvailability() {
        long total = slotRepository.count();
        long occupied = slotRepository.countByStatus(SlotStatus.OCCUPIED);
        long reserved = slotRepository.countByStatus(SlotStatus.RESERVED);
        long empty = slotRepository.countByStatus(SlotStatus.EMPTY);
        long maintenance = slotRepository.countByStatus(SlotStatus.MAINTENANCE);

        // BR-009-4: RESERVED is unavailable to a walk-in, so it belongs on the used side of the
        // rate — the same accounting the entry flow's "full" check uses.
        long used = occupied + reserved;
        double occupancyRate = total == 0 ? 0.0
                : BigDecimal.valueOf((double) used / total).setScale(2, RoundingMode.HALF_UP).doubleValue();

        return new SlotAvailabilityResponseDTO(
                total, occupied, reserved, empty, maintenance, occupancyRate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotResponseDTO> listSlots() {
        return slotRepository.findAll().stream()
                .sorted(Comparator.comparing(Slot::getSlotCode))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DriverSlotDTO> listForDriver() {
        return slotRepository.findAll().stream()
                .sorted(Comparator.comparing(Slot::getZone).thenComparing(Slot::getSlotCode))
                .map(s -> new DriverSlotDTO(s.getId(), s.getSlotCode(), s.getZone(),
                        s.getStatus(), s.getGridRow(), s.getGridCol()))
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

        // BR-009-4: a slot under a live hold has no ACTIVE session yet — by the rule above it would
        // be "corrected" to EMPTY and handed to the next walk-in, which is precisely the broken
        // promise booking exists to prevent. The hold, not the session table, is its ground truth.
        Set<UUID> heldSlots = Set.copyOf(reservationRepository.findHeldSlotIds());

        int corrected = 0;
        for (Slot slot : slotRepository.findAll()) {
            if (slot.getStatus() == SlotStatus.MAINTENANCE) {
                continue;
            }
            if (heldSlots.contains(slot.getId())) {
                // Repair only the flag; the hold itself is untouched. A hold on a slot that a car
                // is physically parked on is left OCCUPIED — the sweep releases it when it expires.
                if (slot.getStatus() == SlotStatus.EMPTY) {
                    slot.setStatus(SlotStatus.RESERVED);
                    slot.setCurrentSessionId(null);
                    slotRepository.save(slot);
                    corrected++;
                }
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
                slotRepository.countByStatus(SlotStatus.RESERVED),
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
        reflowZone(zone);   // BR-003-6: place the new slot on the zone map by code order
        log.info("Slot created: {} (zone {}) at grid {},{}",
                code, zone, slot.getGridRow(), slot.getGridCol());
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
        requireNoLiveHold(slot, "delete");
        slotRepository.delete(slot);
        slotRepository.flush();
        reflowZone(slot.getZone());   // close the hole the removal left in the map
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
        requireNoLiveHold(slot, "change the status of");
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
        List<Slot> surplus = existing.stream()
                .filter(s -> !targetCodes.contains(s.getSlotCode()))
                .toList();
        List<String> blocked = surplus.stream()
                .filter(s -> s.getStatus() == SlotStatus.OCCUPIED)
                .map(Slot::getSlotCode)
                .toList();
        if (!blocked.isEmpty()) {
            throw new ConflictException(
                    "Cannot reduce zone " + zone + " — these slots still have a car: " + blocked);
        }
        // Same for a slot promised to a driver (BR-009-3). Deleting it would fail on the
        // reservations FK anyway; refusing here says why instead of surfacing a constraint error.
        List<String> held = surplus.stream()
                .filter(s -> reservationRepository.existsBySlotIdAndStatus(
                        s.getId(), ReservationStatus.HELD))
                .map(Slot::getSlotCode)
                .toList();
        if (!held.isEmpty()) {
            throw new ConflictException("Cannot reduce zone " + zone
                    + " — these slots are booked by a driver: " + held
                    + ". Wait for the hold to expire or have it cancelled first.");
        }

        int created = 0;
        for (String code : targetCodes) {
            if (!existingCodes.contains(code) && !slotRepository.existsBySlotCode(code)) {
                // Coordinates are assigned by the re-flow below, once the zone's final membership
                // is known — placing them here would reserve cells for slots about to be deleted.
                slotRepository.save(Slot.builder()
                        .slotCode(code).zone(zone).status(SlotStatus.EMPTY).build());
                created++;
            }
        }

        List<Slot> toRemove = existing.stream()
                .filter(s -> !targetCodes.contains(s.getSlotCode()))
                .toList();
        slotRepository.deleteAll(toRemove);

        reflowZone(zone);

        long total = slotRepository.count();
        log.info("Zone {} provisioned to {} slot(s): +{} / -{} (total {})",
                zone, count, created, toRemove.size(), total);
        return new ProvisionResultDTO(zone, count, created, toRemove.size(), total);
    }

    /**
     * BR-009-3: an admin edit must not quietly break a promise already made to a driver. The slot
     * flag alone is not enough to check — an admin who just flipped it to EMPTY would then be
     * allowed to delete it, leaving the hold pointing at nothing.
     */
    private void requireNoLiveHold(Slot slot, String action) {
        if (reservationRepository.existsBySlotIdAndStatus(slot.getId(), ReservationStatus.HELD)) {
            throw new ConflictException("Cannot %s slot %s — a driver has booked it. Wait for the"
                    .formatted(action, slot.getSlotCode())
                    + " hold to expire or have it cancelled first.");
        }
    }

    /**
     * BR-003-6: lay a zone out row-major so the drawn map matches how the codes read (A01 top-left).
     *
     * <p>Coordinates are cleared first and flushed before being reassigned. Slots swap cells when
     * the zone grows or shrinks, and {@code uq_slots_zone_grid} would reject the intermediate state
     * if the rows were updated one at a time.
     */
    private void reflowZone(String zone) {
        List<Slot> slots = slotRepository.findByZoneOrderBySlotCodeAsc(zone);
        for (Slot slot : slots) {
            slot.setGridRow(null);
            slot.setGridCol(null);
        }
        slotRepository.saveAll(slots);
        slotRepository.flush();

        int position = 0;
        for (Slot slot : slots) {
            slot.setGridRow(position / GRID_COLUMNS);
            slot.setGridCol(position % GRID_COLUMNS);
            position++;
        }
        slotRepository.saveAll(slots);
    }

    private static String leftPad(int value, int width) {
        String s = Integer.toString(value);
        return "0".repeat(Math.max(0, width - s.length())) + s;
    }

    private SlotResponseDTO toResponse(Slot s) {
        return new SlotResponseDTO(s.getId(), s.getSlotCode(), s.getZone(), s.getZoneId(),
                s.getStatus(), s.getCurrentSessionId(), s.getGridRow(), s.getGridCol());
    }
}
