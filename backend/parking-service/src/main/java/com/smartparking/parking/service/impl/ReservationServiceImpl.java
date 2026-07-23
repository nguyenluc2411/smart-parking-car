package com.smartparking.parking.service.impl;

import com.smartparking.parking.dto.request.CreateReservationRequestDTO;
import com.smartparking.parking.dto.response.PageResponseDTO;
import com.smartparking.parking.dto.response.ReservationResponseDTO;
import com.smartparking.parking.entity.Reservation;
import com.smartparking.parking.entity.Slot;
import com.smartparking.parking.entity.enums.ReservationStatus;
import com.smartparking.parking.entity.enums.SlotStatus;
import com.smartparking.parking.exception.ConflictException;
import com.smartparking.parking.exception.ForbiddenException;
import com.smartparking.parking.exception.ResourceNotFoundException;
import com.smartparking.parking.repository.ReservationRepository;
import com.smartparking.parking.repository.SlotRepository;
import com.smartparking.parking.security.DriverPrincipal;
import com.smartparking.parking.service.ReservationService;
import com.smartparking.parking.util.PlateNumbers;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Driver slot booking (BR-009).
 *
 * <p>The rule that shapes everything here: a held slot is counted as used. A booking that does not
 * remove a slot from the walk-in pool is not a booking — the driver arrives to find their spot
 * taken, which is worse than never having offered the feature.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final SlotRepository slotRepository;

    /** BR-009-2: minutes a slot stays held after the promised arrival time. */
    @Value("${app.parking.reservation.hold-minutes:20}")
    private int holdMinutes;

    /** BR-009-1: how far ahead a booking may be made. */
    @Value("${app.parking.reservation.max-lead-hours:72}")
    private int maxLeadHours;

    /** BR-009-8: no-shows within the window that get a plate blocked from booking. */
    @Value("${app.parking.reservation.no-show-limit:3}")
    private int noShowLimit;

    @Value("${app.parking.reservation.no-show-window-days:30}")
    private int noShowWindowDays;

    @Override
    @Transactional
    public ReservationResponseDTO create(DriverPrincipal driver, CreateReservationRequestDTO request) {
        String plate = PlateNumbers.normalize(request.plateNumber());

        // BR-009-1: only a plate the operator has verified as this driver's. Without this a driver
        // could hold slots against someone else's plate — or against plates that do not exist.
        if (driver.plates().stream().noneMatch(p -> PlateNumbers.normalize(p).equals(plate))) {
            throw new ForbiddenException(
                    "Biển %s chưa được xác minh cho tài khoản này".formatted(plate));
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startTime = request.startTime();
        if (startTime.isBefore(now.minusMinutes(5))) {
            throw new ConflictException("Giờ hẹn đã ở quá khứ: " + startTime);
        }
        if (startTime.isAfter(now.plusHours(maxLeadHours))) {
            throw new ConflictException(
                    "Chỉ đặt trước tối đa %d giờ".formatted(maxLeadHours));
        }

        // BR-009-5: one live booking per plate.
        if (reservationRepository.findByPlateNumberAndStatus(plate, ReservationStatus.HELD).isPresent()) {
            throw new ConflictException("Biển " + plate + " đang có một lượt đặt chỗ chưa dùng");
        }

        // BR-009-8: repeated no-shows deny slots to drivers who would have paid, so they cost the
        // right to book. No money is involved — see the ADR for why a deposit was not used.
        long noShows = reservationRepository.countNoShowsSince(
                plate, now.minusDays(noShowWindowDays));
        if (noShows >= noShowLimit) {
            throw new ConflictException(
                    "Biển %s đã bỏ lượt đặt %d lần trong %d ngày — tạm khóa đặt chỗ"
                            .formatted(plate, noShows, noShowWindowDays));
        }

        // BR-009-3: take a real slot out of the pool. Row-locked so two drivers racing for the last
        // slot cannot both be given it.
        Slot slot = slotRepository.findFirstAvailable(SlotStatus.EMPTY, Limit.of(1))
                .orElseThrow(() -> new ConflictException("BR-009-4: bãi đã hết chỗ trống để đặt"));
        slot.setStatus(SlotStatus.RESERVED);
        slot.setCurrentSessionId(null);
        slotRepository.save(slot);

        Reservation reservation = reservationRepository.save(Reservation.builder()
                .driverId(driver.driverId())
                .plateNumber(plate)
                .slotId(slot.getId())
                .startTime(startTime)
                .holdUntil(startTime.plusMinutes(holdMinutes))
                .status(ReservationStatus.HELD)
                .build());

        log.info("Reservation held: plate={}, slot={}, start={}, holdUntil={}, driver={}",
                plate, slot.getSlotCode(), startTime, reservation.getHoldUntil(), driver.driverId());
        return toResponse(reservation, slot);
    }

    @Override
    @Transactional
    public ReservationResponseDTO cancel(DriverPrincipal driver, UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", reservationId.toString()));
        if (!reservation.getDriverId().equals(driver.driverId())) {
            throw new ForbiddenException("Lượt đặt này không thuộc tài khoản của bạn");
        }
        if (reservation.getStatus() != ReservationStatus.HELD) {
            throw new ConflictException(
                    "Chỉ hủy được lượt đang giữ chỗ (hiện tại: %s)".formatted(reservation.getStatus()));
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
        Slot slot = releaseSlot(reservation);

        log.info("Reservation cancelled: id={}, plate={}, slot={}",
                reservationId, reservation.getPlateNumber(),
                slot == null ? "?" : slot.getSlotCode());
        return toResponse(reservation, slot);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ReservationResponseDTO> listForDriver(DriverPrincipal driver,
                                                                 int page, int size) {
        Page<Reservation> result = reservationRepository.findByDriverIdOrderByCreatedAtDesc(
                driver.driverId(), PageRequest.of(page, size));
        List<ReservationResponseDTO> content = result.getContent().stream()
                .map(r -> toResponse(r, slotRepository.findById(r.getSlotId()).orElse(null)))
                .toList();
        return PageResponseDTO.of(result, content);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reservation> findLiveHold(String plateNumber) {
        String plate = PlateNumbers.normalize(plateNumber);
        return reservationRepository.findByPlateNumberAndStatus(plate, ReservationStatus.HELD)
                // A hold past its grace is not live even if the sweep has not run yet — otherwise
                // whether the driver gets their slot depends on scheduler timing.
                .filter(r -> !r.getHoldUntil().isBefore(OffsetDateTime.now()));
    }

    @Override
    @Transactional
    public void markFulfilled(Reservation reservation, UUID sessionId) {
        reservation.setStatus(ReservationStatus.FULFILLED);
        reservation.setSessionId(sessionId);
        reservationRepository.save(reservation);
        log.info("Reservation fulfilled: id={}, plate={}, sessionId={}",
                reservation.getId(), reservation.getPlateNumber(), sessionId);
    }

    @Override
    @Transactional
    public int expireDueHolds() {
        List<Reservation> due = reservationRepository.findExpired(OffsetDateTime.now());
        for (Reservation reservation : due) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(reservation);
            Slot slot = releaseSlot(reservation);
            log.info("Reservation expired (no-show): id={}, plate={}, slot={}",
                    reservation.getId(), reservation.getPlateNumber(),
                    slot == null ? "?" : slot.getSlotCode());
        }
        return due.size();
    }

    /**
     * Hand the slot back to the walk-in pool. An OCCUPIED slot is left alone: a car is physically
     * parked on it, and flipping it to EMPTY would hand the same slot to a second driver.
     */
    private Slot releaseSlot(Reservation reservation) {
        return slotRepository.findById(reservation.getSlotId()).map(slot -> {
            if (slot.getStatus() == SlotStatus.RESERVED) {
                slot.setStatus(SlotStatus.EMPTY);
                slot.setCurrentSessionId(null);
                slotRepository.save(slot);
            }
            return slot;
        }).orElse(null);
    }

    private ReservationResponseDTO toResponse(Reservation r, Slot slot) {
        return new ReservationResponseDTO(
                r.getId(), r.getPlateNumber(), r.getSlotId(),
                slot == null ? null : slot.getSlotCode(),
                slot == null ? null : slot.getZone(),
                slot == null ? null : slot.getGridRow(),
                slot == null ? null : slot.getGridCol(),
                r.getStartTime(), r.getHoldUntil(), r.getStatus(), r.getSessionId(),
                r.getCreatedAt());
    }

    /** Exposed for the scheduler's log line. */
    Duration holdDuration() {
        return Duration.ofMinutes(holdMinutes);
    }
}
