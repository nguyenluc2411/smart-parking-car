package com.smartparking.parking.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartparking.parking.dto.response.SessionDetailResponseDTO;
import com.smartparking.parking.entity.Session;
import com.smartparking.parking.entity.enums.SessionStatus;
import com.smartparking.parking.exception.ConflictException;
import com.smartparking.parking.repository.GateRepository;
import com.smartparking.parking.repository.SessionRepository;
import com.smartparking.parking.repository.SlotRepository;
import com.smartparking.parking.service.ImageUrlService;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionQueryServiceImplTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private SlotRepository slotRepository;
    @Mock private GateRepository gateRepository;
    @Mock private ImageUrlService imageUrlService;

    @InjectMocks private SessionQueryServiceImpl service;

    private Session reviewSession(UUID id) {
        OffsetDateTime now = OffsetDateTime.now();
        return Session.builder()
                .id(id).plateNumber("99X-99999").entryTime(now).exitTime(now)
                .status(SessionStatus.REQUIRES_ATTENTION).build();   // no slot/gate refs
    }

    @Test
    void resolve_requiresAttention_toClosed() {
        UUID id = UUID.randomUUID();
        Session session = reviewSession(id);
        when(sessionRepository.findById(id)).thenReturn(Optional.of(session));

        SessionDetailResponseDTO dto = service.resolve(id, SessionStatus.CLOSED, "đã kiểm tra camera");

        assertEquals(SessionStatus.CLOSED, dto.status());
        assertEquals(SessionStatus.CLOSED, session.getStatus());
        verify(sessionRepository).save(session);
    }

    @Test
    void getById_mapsPresignedImageUrls() {
        UUID id = UUID.randomUUID();
        Session s = Session.builder()
                .id(id).plateNumber("51F-12345").entryTime(OffsetDateTime.now())
                .status(SessionStatus.ACTIVE)
                .entryImageRef("frames/in.jpg").exitImageRef("frames/out.jpg").build();
        when(sessionRepository.findById(id)).thenReturn(Optional.of(s));
        when(imageUrlService.presignedGet("frames/in.jpg")).thenReturn("http://minio/in?sig");
        when(imageUrlService.presignedGet("frames/out.jpg")).thenReturn("http://minio/out?sig");
        when(imageUrlService.presignedPlateCrop("frames/in.jpg")).thenReturn("http://minio/in.plate?sig");
        when(imageUrlService.presignedPlateCrop("frames/out.jpg")).thenReturn("http://minio/out.plate?sig");

        SessionDetailResponseDTO dto = service.getById(id);

        assertEquals("http://minio/in?sig", dto.entryImageUrl());
        assertEquals("http://minio/out?sig", dto.exitImageUrl());
        assertEquals("http://minio/in.plate?sig", dto.entryPlateImageUrl());
        assertEquals("http://minio/out.plate?sig", dto.exitPlateImageUrl());
    }

    @Test
    void resolve_notRequiresAttention_throwsConflict() {
        UUID id = UUID.randomUUID();
        Session active = Session.builder().id(id).plateNumber("51F-12345")
                .entryTime(OffsetDateTime.now()).status(SessionStatus.ACTIVE).build();
        when(sessionRepository.findById(id)).thenReturn(Optional.of(active));

        assertThrows(ConflictException.class, () -> service.resolve(id, SessionStatus.CLOSED, "x"));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void resolve_invalidTargetStatus_throwsBadRequest() {
        // Only CLOSED or CANCELLED are allowed targets.
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(UUID.randomUUID(), SessionStatus.ACTIVE, "x"));
        verify(sessionRepository, never()).save(any());
    }
}
