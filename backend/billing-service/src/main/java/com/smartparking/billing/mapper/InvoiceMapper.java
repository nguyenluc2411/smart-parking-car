package com.smartparking.billing.mapper;

import com.smartparking.billing.dto.event.InvoiceCalculatedEventDTO;
import com.smartparking.billing.dto.response.InvoiceResponseDTO;
import com.smartparking.billing.dto.response.InvoiceResponseDTO.BreakdownDTO;
import com.smartparking.billing.dto.response.InvoiceResponseDTO.BreakdownDTO.Line;
import com.smartparking.billing.entity.Invoice;
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

    @Mapping(target = "invoiceId", source = "id")
    @Mapping(target = "breakdown", expression = "java(toBreakdown(invoice))")
    InvoiceResponseDTO toInvoiceResponse(Invoice invoice);

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
