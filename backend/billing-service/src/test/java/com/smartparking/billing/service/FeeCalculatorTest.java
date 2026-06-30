package com.smartparking.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartparking.billing.entity.Rate;
import com.smartparking.billing.entity.RateSchedule;
import com.smartparking.billing.entity.enums.DayType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** BR-004 fee-calculation rules. Times are constructed in UTC; ICT = UTC+7. */
class FeeCalculatorTest {

    private final FeeCalculator calculator = new FeeCalculator("Asia/Ho_Chi_Minh", 60);

    private Rate rate() {
        return Rate.builder()
                .ratePerMin(new BigDecimal("200.00"))
                .peakMultiplier(new BigDecimal("1.5"))
                .overnightFlat(new BigDecimal("30000.00"))
                .minCharge(new BigDecimal("5000.00"))
                .build();
    }

    /** BR-004-7: peak windows come from rate_schedules — 07–09 and 17–19, all days. */
    private List<RateSchedule> peakWindows() {
        return List.of(
                RateSchedule.builder().hourStart(7).hourEnd(9).peak(true).dayType(DayType.ALL).build(),
                RateSchedule.builder().hourStart(17).hourEnd(19).peak(true).dayType(DayType.ALL).build());
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, actual.compareTo(new BigDecimal(expected)),
                () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void normalDaytime_roundsUpTo30MinBlocks() {
        // 10:00–10:45 ICT = 03:00Z–03:45Z → 45' billed as 60' → 60 × 200 = 12000.
        FeeCalculation fee = calculator.calculate(
                OffsetDateTime.parse("2026-06-20T03:00:00Z"),
                OffsetDateTime.parse("2026-06-20T03:45:00Z"), rate(), peakWindows());

        assertFalse(fee.peakApplied());
        assertFalse(fee.overnightApplied());
        assertEquals(45, fee.durationMinutes());
        assertAmount("12000.00", fee.amount());
    }

    @Test
    void peakHour_appliesMultiplier() {
        // 08:00–08:30 ICT = 01:00Z–01:30Z → 30' × 200 = 6000 × 1.5 = 9000.
        FeeCalculation fee = calculator.calculate(
                OffsetDateTime.parse("2026-06-20T01:00:00Z"),
                OffsetDateTime.parse("2026-06-20T01:30:00Z"), rate(), peakWindows());

        assertTrue(fee.peakApplied());
        assertFalse(fee.overnightApplied());
        assertAmount("9000.00", fee.amount());
    }

    @Test
    void overnight_overGrace_usesFlatRate() {
        // BR-004-3: 22:00–23:30 ICT = 15:00Z–16:30Z → 90' overnight (> 60' grace) → flat 30000.
        FeeCalculation fee = calculator.calculate(
                OffsetDateTime.parse("2026-06-20T15:00:00Z"),
                OffsetDateTime.parse("2026-06-20T16:30:00Z"), rate(), peakWindows());

        assertTrue(fee.overnightApplied());
        assertFalse(fee.peakApplied());
        assertAmount("30000.00", fee.amount());
    }

    @Test
    void overnight_underGrace_billedAsNormal() {
        // BR-004-3 grace: 23:00–23:30 ICT = 16:00Z–16:30Z → 30' overnight (≤ 60' grace) → NOT the
        // overnight package; billed as one normal 30-min block = 30 × 200 = 6000.
        FeeCalculation fee = calculator.calculate(
                OffsetDateTime.parse("2026-06-20T16:00:00Z"),
                OffsetDateTime.parse("2026-06-20T16:30:00Z"), rate(), peakWindows());

        assertFalse(fee.overnightApplied());
        assertFalse(fee.peakApplied());
        assertAmount("6000.00", fee.amount());
    }

    @Test
    void peakBlock_partialBlockRoundsUpAtPeakRate() {
        // BR-004-2/#4: 17:00–17:31 ICT = 10:00Z–10:31Z → 31' → 2 blocks, BOTH start in peak →
        // 2 × 30 × 200 × 1.5 = 18000 (one clean rate per block, no normal/peak mix in a block).
        FeeCalculation fee = calculator.calculate(
                OffsetDateTime.parse("2026-06-20T10:00:00Z"),
                OffsetDateTime.parse("2026-06-20T10:31:00Z"), rate(), peakWindows());

        assertTrue(fee.peakApplied());
        assertFalse(fee.overnightApplied());
        assertAmount("18000.00", fee.amount());
    }

    @Test
    void zeroDuration_fallsBackToMinCharge() {
        OffsetDateTime t = OffsetDateTime.parse("2026-06-20T03:00:00Z");
        FeeCalculation fee = calculator.calculate(t, t, rate(), peakWindows());

        assertEquals(0, fee.durationSeconds());
        assertAmount("5000.00", fee.amount());
    }

    @Test
    void splitBlock_spansNormalAndPeak() {
        // 16:30–19:30 ICT = 09:30Z–12:30Z → 30' normal + 120' peak + 30' normal.
        // = 60×200 + 120×(200×1.5) = 12000 + 36000 = 48000.
        FeeCalculation fee = calculator.calculate(
                OffsetDateTime.parse("2026-06-20T09:30:00Z"),
                OffsetDateTime.parse("2026-06-20T12:30:00Z"), rate(), peakWindows());

        assertTrue(fee.peakApplied());
        assertFalse(fee.overnightApplied());
        assertAmount("48000.00", fee.amount());
    }

    @Test
    void splitBlock_overnightFlatPlusDayMinutes() {
        // 21:00 → 07:00(+1) ICT = 14:00Z → 00:00Z(+1).
        // 60' normal (21–22) + overnight_flat (22–06, one night) + 60' normal (06–07)
        // = 120×200 + 30000 = 24000 + 30000 = 54000.
        FeeCalculation fee = calculator.calculate(
                OffsetDateTime.parse("2026-06-20T14:00:00Z"),
                OffsetDateTime.parse("2026-06-21T00:00:00Z"), rate(), peakWindows());

        assertTrue(fee.overnightApplied());
        assertFalse(fee.peakApplied());
        assertAmount("54000.00", fee.amount());
    }

    @Test
    void peakWindow_weekdayOnly_appliesOnWeekday() {
        // BR-004-7: day_type=WEEKDAY. Monday 08:00–08:30 ICT (= 01:00Z–01:30Z on 2026-06-22).
        List<RateSchedule> weekdayPeak = List.of(
                RateSchedule.builder().hourStart(7).hourEnd(9).peak(true).dayType(DayType.WEEKDAY).build());
        FeeCalculation fee = calculator.calculate(
                OffsetDateTime.parse("2026-06-22T01:00:00Z"),
                OffsetDateTime.parse("2026-06-22T01:30:00Z"), rate(), weekdayPeak);

        assertTrue(fee.peakApplied());
        assertAmount("9000.00", fee.amount());
    }

    @Test
    void peakWindow_weekdayOnly_skippedOnWeekend() {
        // Same WEEKDAY window but Saturday 08:00–08:30 ICT (= 01:00Z on 2026-06-20) → billed as normal.
        List<RateSchedule> weekdayPeak = List.of(
                RateSchedule.builder().hourStart(7).hourEnd(9).peak(true).dayType(DayType.WEEKDAY).build());
        FeeCalculation fee = calculator.calculate(
                OffsetDateTime.parse("2026-06-20T01:00:00Z"),
                OffsetDateTime.parse("2026-06-20T01:30:00Z"), rate(), weekdayPeak);

        assertFalse(fee.peakApplied());
        assertAmount("6000.00", fee.amount());   // 30' × 200, no peak multiplier
    }
}
