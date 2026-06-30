package com.smartparking.parking.mapper;

import com.smartparking.parking.dto.event.SessionClosedEventDTO;
import com.smartparking.parking.dto.event.SessionCreatedEventDTO;
import com.smartparking.parking.entity.Session;
import org.mapstruct.Mapping;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Maps {@link Session} entities to event DTOs.
 * Fields not present on the entity (eventId, slotCode, gateId) are enriched by the service
 * via {@code .toBuilder()}-style copy in {@code SessionServiceImpl}.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SessionMapper {

    @Mapping(target = "sessionId", source = "id")
    @Mapping(target = "eventId", ignore = true)
    @Mapping(target = "slotCode", ignore = true)
    @Mapping(target = "gateId", ignore = true)
    SessionCreatedEventDTO toSessionCreatedEvent(Session session);

    /** {@code eventId} is enriched by the service via {@code toBuilder()}. */
    @Mapping(target = "sessionId", source = "id")
    @Mapping(target = "eventId", ignore = true)
    SessionClosedEventDTO toSessionClosedEvent(Session session);
}
