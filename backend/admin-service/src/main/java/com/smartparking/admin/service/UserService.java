package com.smartparking.admin.service;

import com.smartparking.admin.dto.request.CreateUserRequestDTO;
import com.smartparking.admin.dto.response.PageResponseDTO;
import com.smartparking.admin.dto.response.UserResponseDTO;
import com.smartparking.admin.entity.enums.Role;
import java.util.UUID;

/** User management ({@code /api/v1/users}). ADMIN only. All mutations are audited (BR-007-3). */
public interface UserService {

    PageResponseDTO<UserResponseDTO> list(int page, int size);

    UserResponseDTO create(CreateUserRequestDTO request, UUID actorId);

    UserResponseDTO updateRole(UUID userId, Role role, UUID actorId);

    /** Deactivate (soft-delete) a user. */
    void deactivate(UUID userId, UUID actorId);

    /** Re-activate a previously deactivated user (is_active=true) so they can log in again. */
    UserResponseDTO activate(UUID userId, UUID actorId);
}
