package com.smartparking.admin.controller;

import com.smartparking.admin.dto.request.CreateUserRequestDTO;
import com.smartparking.admin.dto.request.UpdateRoleRequestDTO;
import com.smartparking.admin.dto.response.ApiResponse;
import com.smartparking.admin.dto.response.PageResponseDTO;
import com.smartparking.admin.dto.response.UserResponseDTO;
import com.smartparking.admin.service.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** User management (ADMIN — enforced by SecurityConfig). */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<PageResponseDTO<UserResponseDTO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(userService.list(page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponseDTO> create(
            @Valid @RequestBody CreateUserRequestDTO request,
            @AuthenticationPrincipal String actorId) {
        return ApiResponse.ok(userService.create(request, UUID.fromString(actorId)));
    }

    @PutMapping("/{id}/role")
    public ApiResponse<UserResponseDTO> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequestDTO request,
            @AuthenticationPrincipal String actorId) {
        return ApiResponse.ok(userService.updateRole(id, request.role(), UUID.fromString(actorId)));
    }

    @PutMapping("/{id}/activate")
    public ApiResponse<UserResponseDTO> activate(
            @PathVariable UUID id, @AuthenticationPrincipal String actorId) {
        return ApiResponse.ok(userService.activate(id, UUID.fromString(actorId)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id, @AuthenticationPrincipal String actorId) {
        userService.deactivate(id, UUID.fromString(actorId));
    }
}
