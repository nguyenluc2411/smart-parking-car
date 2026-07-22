package com.smartparking.billing.mapper;

import com.smartparking.billing.dto.event.InvoiceCalculatedEventDTO;
import com.smartparking.billing.dto.response.InvoiceResponseDTO;
import com.smartparking.billing.dto.response.InvoiceResponseDTO.BreakdownDTO;
import com.smartparking.billing.dto.response.InvoiceResponseDTO.BreakdownDTO.Line;
import com.smartparking.billing.entity.Invoice;
import com.smartparking.billing.entity.Rate;
import java.math.BigDecimal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Maps {@link Invoice} entities to event / response DTOs. {@code eventId} is enriched by the
 * service via {@code toBuilder()}.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InvoiceMapper {

    @Mapping(target = "invoiceId", source = "id")
    @Mapping(target = "eventId", ignore = true)
    InvoiceCalculatedEventDTO toInvoiceCalculatedEvent(Invoice invoice);

    /** List views: no tariff constants (avoids an N+1 rate lookup per row) — those 3 stay null. */
    @Mapping(target = "invoiceId", source = "id")
    @Mapping(target = "breakdown", expression = "java(toBreakdown(invoice))")
    InvoiceResponseDTO toInvoiceResponse(Invoice invoice);

    /**
     * Single-invoice detail views (payment dialog etc.): {@code rate} is the {@link Rate}
     * referenced by {@code invoice.rateId} — the rate actually applied to this invoice at
     * calculation time, not whatever rate is currently effective — so the operator sees the true
     * pricing behind the amount even if rates changed since.
     *
     * <p>Where the invoice carries its own snapshot (V6 onward) that wins over the rate row: the
     * snapshot cannot drift if someone edits the rate in place, which is the whole reason it is
     * stored. The rate row is the fallback for invoices issued before the snapshot existed.
     */
    @Mapping(target = "invoiceId", source = "invoice.id")
    @Mapping(target = "ratePerMin", source = "invoice.ratePerMin")
    @Mapping(target = "breakdown", expression = "java(toBreakdown(invoice))")
    @Mapping(target = "peakMultiplier",
            expression = "java(invoice.getPeakMultiplier() != null ? invoice.getPeakMultiplier()"
                    + " : (rate == null ? null : rate.getPeakMultiplier()))")
    @Mapping(target = "overnightFlat",
            expression = "java(invoice.getOvernightFlat() != null ? invoice.getOvernightFlat()"
                    + " : (rate == null ? null : rate.getOvernightFlat()))")
    @Mapping(target = "minCharge", source = "rate.minCharge")
    InvoiceResponseDTO toInvoiceResponse(Invoice invoice, Rate rate);

    /**
     * BR-004 line items. Returns {@code null} for invoices issued before the V6 migration — they
     * have no stored counts, and inventing them from the current rate table would put numbers on a
     * receipt that never matched what the driver was charged.
     */
    default BreakdownDTO toBreakdown(Invoice invoice) {
        if (invoice == null || invoice.getBlockMinutes() == null) {
            return null;
        }
        BigDecimal blockMinutes = BigDecimal.valueOf(invoice.getBlockMinutes());
        BigDecimal normalUnit = invoice.getRatePerMin().multiply(blockMinutes);
        BigDecimal peakUnit = normalUnit.multiply(
                invoice.getPeakMultiplier() == null ? BigDecimal.ONE : invoice.getPeakMultiplier());
        BigDecimal overnightUnit = invoice.getOvernightFlat() == null
                ? BigDecimal.ZERO : invoice.getOvernightFlat();

        return new BreakdownDTO(
                invoice.getBlockMinutes(),
                line(invoice.getNormalBlocks(), normalUnit),
                line(invoice.getPeakBlocks(), peakUnit),
                line(invoice.getOvernightNights(), overnightUnit),
                Boolean.TRUE.equals(invoice.getMinChargeApplied()));
    }

    private static Line line(Long quantity, BigDecimal unitAmount) {
        long qty = quantity == null ? 0L : quantity;
        return new Line(qty, unitAmount, unitAmount.multiply(BigDecimal.valueOf(qty)));
    }
}
