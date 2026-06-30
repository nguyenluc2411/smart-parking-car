package com.smartparking.parking.service;

import com.smartparking.parking.dto.response.PageResponseDTO;
import com.smartparking.parking.dto.response.SessionDetailResponseDTO;
import com.smartparking.parking.dto.response.SessionSummaryResponseDTO;
import com.smartparking.parking.entity.enums.SessionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read queries + operator reconciliation for parking sessions ({@code /api/v1/sessions}). */
public interface SessionQueryService {

    PageResponseDTO<SessionSummaryResponseDTO> search(SessionStatus status, LocalDate date, String plate,
                                                      int page, int size);

    /** Driver "my sessions": only sessions whose plate is in the caller's verified {@code plates}. */
    PageResponseDTO<SessionSummaryResponseDTO> searchForDriver(List<String> plates, SessionStatus status,
                                                               int page, int size);

    /**
     * Driver session detail, scoped to the caller's plates.
     *
     * @throws com.smartparking.parking.exception.ResourceNotFoundException if the session is unknown
     * @throws com.smartparking.parking.exception.ForbiddenException        if the plate is not the caller's
     */
    SessionDetailResponseDTO getByIdForDriver(UUID id, List<String> plates);

    /**
     * @throws com.smartparking.parking.exception.ResourceNotFoundException if the session is unknown
     */
    SessionDetailResponseDTO getById(UUID id);

    /**
     * BR-006-5: operator resolves a REQUIRES_ATTENTION session to CLOSED or CANCELLED.
     *
     * @throws com.smartparking.parking.exception.ResourceNotFoundException if the session is unknown
     * @throws com.smartparking.parking.exception.ConflictException         if it is not REQUIRES_ATTENTION
     */
    SessionDetailResponseDTO resolve(UUID id, SessionStatus status, String note);
}
