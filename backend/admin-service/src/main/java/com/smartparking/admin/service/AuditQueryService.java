package com.smartparking.admin.service;

import com.smartparking.admin.dto.response.AuditLogResponseDTO;
import com.smartparking.admin.dto.response.PageResponseDTO;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Read side for the audit trail ({@code GET /api/v1/audit-logs}). ADMIN only. */
public interface AuditQueryService {

    PageResponseDTO<AuditLogResponseDTO> search(String action, UUID userId,
                                                OffsetDateTime from, OffsetDateTime to,
                                                int page, int size);
}
