package com.smartparking.billing.service.impl;

import com.smartparking.billing.dto.request.UpdateRateRequestDTO;
import com.smartparking.billing.dto.response.RateResponseDTO;
import com.smartparking.billing.dto.response.RateResponseDTO.ScheduleDTO;
import com.smartparking.billing.entity.Rate;
import com.smartparking.billing.entity.RateSchedule;
import com.smartparking.billing.exception.RateNotFoundException;
import com.smartparking.billing.repository.RateRepository;
import com.smartparking.billing.repository.RateScheduleRepository;
import com.smartparking.billing.service.RateService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateServiceImpl implements RateService {

    private final RateRepository rateRepository;
    private final RateScheduleRepository rateScheduleRepository;

    @Override
    @Transactional(readOnly = true)
    public RateResponseDTO getCurrentRate() {
        Rate rate = currentRate();
        return toResponse(rate, rateScheduleRepository.findByRateIdOrderByHourStartAsc(rate.getId()));
    }

    @Override
    @Transactional
    public RateResponseDTO updateRate(UpdateRateRequestDTO request, UUID operatorId) {
        OffsetDateTime now = OffsetDateTime.now();

        // Close the current version (if any) and carry its schedules to the new one.
        List<RateSchedule> oldSchedules = List.of();
        var existing = rateRepository.findEffective(now, PageRequest.of(0, 1));
        if (!existing.isEmpty()) {
            Rate current = existing.get(0);
            current.setEffectiveTo(now);
            rateRepository.save(current);
            oldSchedules = rateScheduleRepository.findByRateIdOrderByHourStartAsc(current.getId());
        }

        Rate created = rateRepository.save(Rate.builder()
                .ratePerMin(request.ratePerMin())
                .peakMultiplier(request.peakMultiplier())
                .overnightFlat(request.overnightFlat())
                .minCharge(request.minCharge())
                .effectiveFrom(now)
                .effectiveTo(null)
                .createdBy(operatorId)
                .build());

        List<RateSchedule> newSchedules = oldSchedules.stream()
                .map(s -> rateScheduleRepository.save(RateSchedule.builder()
                        .rateId(created.getId())
                        .hourStart(s.getHourStart())
                        .hourEnd(s.getHourEnd())
                        .peak(s.isPeak())
                        .dayType(s.getDayType())
                        .build()))
                .toList();

        log.info("Rate updated by {}: new rateId={}, ratePerMin={}", operatorId, created.getId(),
                created.getRatePerMin());
        return toResponse(created, newSchedules);
    }

    private Rate currentRate() {
        return rateRepository.findEffective(OffsetDateTime.now(), PageRequest.of(0, 1)).stream()
                .findFirst()
                .orElseThrow(() -> new RateNotFoundException("No effective rate configured"));
    }

    private RateResponseDTO toResponse(Rate rate, List<RateSchedule> schedules) {
        List<ScheduleDTO> scheduleDtos = schedules.stream()
                .map(s -> new ScheduleDTO(s.getHourStart(), s.getHourEnd(), s.isPeak(), s.getDayType()))
                .toList();
        return new RateResponseDTO(rate.getId(), rate.getRatePerMin(), rate.getPeakMultiplier(),
                rate.getOvernightFlat(), rate.getMinCharge(), rate.getEffectiveFrom(),
                rate.getEffectiveTo(), scheduleDtos);
    }
}
