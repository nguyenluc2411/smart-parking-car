package com.smartparking.billing.mapper;

import com.smartparking.billing.dto.event.InvoiceCalculatedEventDTO;
import com.smartparking.billing.dto.response.InvoiceResponseDTO;
import com.smartparking.billing.entity.Invoice;
import com.smartparking.billing.entity.Rate;
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

    /** List views: no rate breakdown (avoids an N+1 rate lookup per row) — those 3 fields stay null. */
    @Mapping(target = "invoiceId", source = "id")
    InvoiceResponseDTO toInvoiceResponse(Invoice invoice);

    /**
     * Single-invoice detail views (payment dialog etc.): {@code rate} is the {@link Rate}
     * referenced by {@code invoice.rateId} — the rate actually applied to this invoice at
     * calculation time, not whatever rate is currently effective — so the operator sees the true
     * pricing behind the amount even if rates changed since.
     */
    @Mapping(target = "invoiceId", source = "invoice.id")
    @Mapping(target = "ratePerMin", source = "invoice.ratePerMin")
    @Mapping(target = "peakMultiplier", source = "rate.peakMultiplier")
    @Mapping(target = "overnightFlat", source = "rate.overnightFlat")
    @Mapping(target = "minCharge", source = "rate.minCharge")
    InvoiceResponseDTO toInvoiceResponse(Invoice invoice, Rate rate);
}
