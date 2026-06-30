package com.smartparking.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartparking.admin.dto.request.LoginRequestDTO;
import com.smartparking.admin.dto.request.LogoutRequestDTO;
import com.smartparking.admin.dto.request.RefreshRequestDTO;
import com.smartparking.admin.dto.response.LoginResponseDTO;
import com.smartparking.admin.entity.RefreshToken;
import com.smartparking.admin.entity.User;
import com.smartparking.admin.entity.enums.Role;
import com.smartparking.admin.exception.InvalidCredentialsException;
import com.smartparking.admin.exception.InvalidTokenException;
import com.smartparking.admin.repository.RefreshTokenRepository;
import com.smartparking.admin.repository.UserRepository;
import com.smartparking.admin.security.JwtService;
import com.smartparking.admin.service.AuditService;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuditService auditService;

    @InjectMocks private AuthServiceImpl service;

    private User adminUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "refreshTtlSeconds", 604800L);
        adminUser = User.builder()
                .id(UUID.randomUUID()).username("admin").email("admin@parking.vn")
                .passwordHash("bcrypt-hash").role(Role.ADMIN).active(true).build();
    }

    @Test
    void login_validCredentials_issuesTokensAndAudits() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("secret", "bcrypt-hash")).thenReturn(true);
        when(jwtService.generateAccessToken(adminUser)).thenReturn("access-jwt");
        when(jwtService.getAccessTtlSeconds()).thenReturn(28800L);

        LoginResponseDTO resp = service.login(new LoginRequestDTO("admin", "secret"));

        assertEquals("access-jwt", resp.accessToken());
        assertNotNull(resp.refreshToken());
        assertEquals(28800L, resp.expiresIn());
        assertEquals(Role.ADMIN, resp.role());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(auditService).recordUserAction(eq(adminUser.getId()), eq("USER_LOGIN"), any(), any(), any());
    }

    @Test
    void login_wrongPassword_throws() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("bad", "bcrypt-hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequestDTO("admin", "bad")));

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_unknownUser_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequestDTO("ghost", "x")));
    }

    @Test
    void refresh_validToken_issuesNewAccessToken() {
        RefreshToken stored = RefreshToken.builder()
                .userId(adminUser.getId()).tokenHash("hash")
                .expiresAt(OffsetDateTime.now().plusDays(1)).revoked(false).build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(jwtService.generateAccessToken(adminUser)).thenReturn("new-access");
        when(jwtService.getAccessTtlSeconds()).thenReturn(28800L);

        LoginResponseDTO resp = service.refresh(new RefreshRequestDTO("raw-refresh"));

        assertEquals("new-access", resp.accessToken());
        assertEquals("raw-refresh", resp.refreshToken());
    }

    @Test
    void refresh_revokedToken_throws() {
        RefreshToken stored = RefreshToken.builder()
                .userId(adminUser.getId()).tokenHash("hash")
                .expiresAt(OffsetDateTime.now().plusDays(1)).revoked(true).build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThrows(InvalidTokenException.class,
                () -> service.refresh(new RefreshRequestDTO("raw-refresh")));
    }

    @Test
    void logout_revokesTokenAndAudits() {
        RefreshToken stored = RefreshToken.builder()
                .userId(adminUser.getId()).tokenHash("hash")
                .expiresAt(OffsetDateTime.now().plusDays(1)).revoked(false).build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        service.logout(new LogoutRequestDTO("raw-refresh"));

        verify(refreshTokenRepository).save(stored);
        verify(auditService).recordUserAction(eq(adminUser.getId()), eq("USER_LOGOUT"), any(), any(), any());
    }
}
