package com.smartparking.billing.mapper;

import com.smartparking.billing.dto.event.InvoiceCalculatedEventDTO;
import com.smartparking.billing.dto.response.InvoiceResponseDTO;
import com.smartparking.billing.entity.Invoice;
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
    InvoiceResponseDTO toInvoiceResponse(Invoice invoice);
}
