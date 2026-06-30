package com.smartparking.billing.service;

import com.smartparking.billing.entity.Rate;
import com.smartparking.billing.entity.RateSchedule;
import com.smartparking.billing.entity.enums.DayType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fee calculation per BR-004 — <b>30-MINUTE BLOCK</b>. The session is divided into whole 30-minute
 * blocks (rounding the last partial block UP) and each block is priced by the tariff at the block's
 * START — no per-minute price mixing inside a block, so an invoice never shows two rates in one block.
 * <ul>
 *   <li>BR-004-1/2: each 30-min block = {@code rate_per_min × 30}; a block that STARTS in a peak
 *       window costs {@code × peak_multiplier}; a block that STARTS in the overnight window (22–06)
 *       is an overnight block (see BR-004-3).</li>
 *   <li>BR-004-7: peak windows come from {@code rate_schedules} (passed by the caller), not hardcoded
 *       — each has an hour range + day_type (ALL/WEEKDAY/WEEKEND).</li>
 *   <li>BR-004-3 + grace: {@code overnight_flat} is charged once per night ONLY when that night's
 *       overnight time exceeds {@code overnight-grace-minutes} (default 60). A night at/under the
 *       grace is billed as normal 30-min blocks instead — so a few minutes either side of 22:00
 *       isn't charged a whole overnight package.</li>
 *   <li>BR-004-4: the total is floored at {@code min_charge}.</li>
 * </ul>
 * No persistence — pure + unit-testable.
 */
@Slf4j
@Component
public class FeeCalculator {

    private static final int BLOCK_MINUTES = 30;

    private final ZoneId zoneId;
    private final long overnightGraceMinutes;

    public FeeCalculator(@Value("${app.billing.zone-id}") String zoneId,
                         @Value("${app.billing.overnight-grace-minutes:60}") long overnightGraceMinutes) {
        this.zoneId = ZoneId.of(zoneId);
        this.overnightGraceMinutes = overnightGraceMinutes;
    }

    public FeeCalculation calculate(OffsetDateTime entryTime, OffsetDateTime exitTime, Rate rate,
                                    List<RateSchedule> peakWindows) {
        int durationSeconds = (int) Math.max(0, Duration.between(entryTime, exitTime).getSeconds());
        int durationMinutes = (int) Math.ceil(durationSeconds / 60.0);

        // BR-004-2: bill in whole 30-minute blocks (last partial block rounds up).
        int blocks = (int) Math.ceil(durationMinutes / (double) BLOCK_MINUTES);
        ZonedDateTime start = entryTime.atZoneSameInstant(zoneId);

        long normalBlocks = 0;
        long peakBlocks = 0;
        // BR-004-3: overnight blocks counted per night (keyed by the 22:00-start date) for the grace check.
        Map<LocalDate, Long> overnightBlocksPerNight = new HashMap<>();
        for (int i = 0; i < blocks; i++) {
            ZonedDateTime blockStart = start.plusMinutes((long) BLOCK_MINUTES * i);
            int hour = blockStart.getHour();
            if (isOvernight(hour)) {
                LocalDate night = hour >= 22 ? blockStart.toLocalDate() : blockStart.toLocalDate().minusDays(1);
                overnightBlocksPerNight.merge(night, 1L, Long::sum);
            } else if (isPeak(blockStart, peakWindows)) {
                peakBlocks++;
            } else {
                normalBlocks++;
            }
        }

        // BR-004-3 + grace: > grace overnight minutes in a night -> one overnight_flat; otherwise those
        // blocks fall back to the normal rate (don't charge a whole overnight package for a few minutes).
        long overnightFlatNights = 0;
        long graceNormalBlocks = 0;
        for (long blocksInNight : overnightBlocksPerNight.values()) {
            if (blocksInNight * BLOCK_MINUTES > overnightGraceMinutes) {
                overnightFlatNights++;
            } else {
                graceNormalBlocks += blocksInNight;
            }
        }

        BigDecimal ratePerMin = rate.getRatePerMin();
        BigDecimal block = BigDecimal.valueOf(BLOCK_MINUTES);
        BigDecimal normalCharge = ratePerMin.multiply(block)
                .multiply(BigDecimal.valueOf(normalBlocks + graceNormalBlocks));
        BigDecimal peakCharge = ratePerMin.multiply(rate.getPeakMultiplier()).multiply(block)
                .multiply(BigDecimal.valueOf(peakBlocks));
        BigDecimal overnightCharge = rate.getOvernightFlat()
                .multiply(BigDecimal.valueOf(overnightFlatNights));

        BigDecimal amount = normalCharge.add(peakCharge).add(overnightCharge)
                .max(rate.getMinCharge())
                .setScale(2, RoundingMode.HALF_UP);

        boolean peakApplied = peakBlocks > 0;
        boolean overnightApplied = overnightFlatNights > 0;
        log.debug("Fee (30-min block): normalBlk={}, peakBlk={}, graceBlk={}, flatNights={}, amount={}",
                normalBlocks, peakBlocks, graceNormalBlocks, overnightFlatNights, amount);

        return new FeeCalculation(durationSeconds, durationMinutes, ratePerMin,
                peakApplied, overnightApplied, amount);
    }

    /** BR-004-3: 22:00–06:00 (the overnight_flat window). */
    private boolean isOvernight(int hour) {
        return hour >= 22 || hour < 6;
    }

    /** BR-004-2/7: peak if any rate_schedules peak window covers this hour + day type. */
    private boolean isPeak(ZonedDateTime moment, List<RateSchedule> peakWindows) {
        int hour = moment.getHour();
        DayOfWeek dow = moment.getDayOfWeek();
        for (RateSchedule window : peakWindows) {
            if (window.isPeak() && dayTypeMatches(window.getDayType(), dow)
                    && hour >= window.getHourStart() && hour < window.getHourEnd()) {
                return true;
            }
        }
        return false;
    }

    private boolean dayTypeMatches(DayType dayType, DayOfWeek dow) {
        return switch (dayType) {
            case ALL -> true;
            case WEEKDAY -> dow.getValue() <= 5;   // MON..FRI
            case WEEKEND -> dow.getValue() >= 6;   // SAT, SUN
        };
    }
}
