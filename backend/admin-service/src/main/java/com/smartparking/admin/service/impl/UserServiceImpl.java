package com.smartparking.admin.service.impl;

import com.smartparking.admin.dto.request.CreateUserRequestDTO;
import com.smartparking.admin.dto.response.PageResponseDTO;
import com.smartparking.admin.dto.response.UserResponseDTO;
import com.smartparking.admin.entity.User;
import com.smartparking.admin.entity.enums.Role;
import com.smartparking.admin.exception.NotFoundException;
import com.smartparking.admin.repository.UserRepository;
import com.smartparking.admin.service.AuditService;
import com.smartparking.admin.service.UserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<UserResponseDTO> list(int page, int size) {
        Page<User> result = userRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<UserResponseDTO> content = result.getContent().stream().map(this::toResponse).toList();
        return PageResponseDTO.of(result, content);
    }

    @Override
    @Transactional
    public UserResponseDTO create(CreateUserRequestDTO request, UUID actorId) {
        // Uniqueness on username/email is enforced by DB constraints -> 409 via the handler.
        User user = userRepository.save(User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .active(true)
                .build());
        auditService.recordUserAction(actorId, "USER_CREATED", "User", user.getId().toString(), null);
        log.info("User created: {} (role={}) by {}", user.getUsername(), user.getRole(), actorId);
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserResponseDTO updateRole(UUID userId, Role role, UUID actorId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        user.setRole(role);
        userRepository.save(user);
        auditService.recordUserAction(actorId, "USER_ROLE_UPDATED", "User", userId.toString(), null);
        return toResponse(user);
    }

    @Override
    @Transactional
    public void deactivate(UUID userId, UUID actorId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        user.setActive(false);
        userRepository.save(user);
        auditService.recordUserAction(actorId, "USER_DEACTIVATED", "User", userId.toString(), null);
        log.info("User deactivated: {} by {}", userId, actorId);
    }

    @Override
    @Transactional
    public UserResponseDTO activate(UUID userId, UUID actorId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        user.setActive(true);
        userRepository.save(user);
        auditService.recordUserAction(actorId, "USER_ACTIVATED", "User", userId.toString(), null);
        log.info("User activated: {} by {}", userId, actorId);
        return toResponse(user);
    }

    private UserResponseDTO toResponse(User user) {
        return new UserResponseDTO(user.getId(), user.getUsername(), user.getEmail(),
                user.getRole(), user.isActive(), user.getCreatedAt());
    }
}
