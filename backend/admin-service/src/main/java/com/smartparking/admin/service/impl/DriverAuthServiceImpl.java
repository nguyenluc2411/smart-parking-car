package com.smartparking.admin.service.impl;

import com.smartparking.admin.dto.request.RequestOtpRequestDTO;
import com.smartparking.admin.dto.request.VerifyOtpRequestDTO;
import com.smartparking.admin.dto.response.DriverLoginResponseDTO;
import com.smartparking.admin.dto.response.OtpChallengeResponseDTO;
import com.smartparking.admin.entity.Driver;
import com.smartparking.admin.entity.DriverRefreshToken;
import com.smartparking.admin.entity.OtpCode;
import com.smartparking.admin.exception.BadRequestException;
import com.smartparking.admin.exception.InvalidTokenException;
import com.smartparking.admin.exception.TooManyAttemptsException;
import com.smartparking.admin.repository.DriverRefreshTokenRepository;
import com.smartparking.admin.repository.DriverRepository;
import com.smartparking.admin.repository.DriverVehicleRepository;
import com.smartparking.admin.repository.OtpCodeRepository;
import com.smartparking.admin.security.JwtService;
import com.smartparking.admin.service.DriverAuthService;
import com.smartparking.admin.service.OtpSender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Driver auth via phone + OTP (ADR-010). OTPs are stored as BCrypt hashes; refresh tokens as
 * SHA-256 hashes (mirrors {@link AuthServiceImpl}). Access tokens carry the driver's verified
 * plates so parking/billing can scope data without a cross-service call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverAuthServiceImpl implements DriverAuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int OTP_TTL_SECONDS = 300;     // 5 minutes (contract expiresIn)
    private static final int OTP_RESEND_AFTER = 60;
    private static final int OTP_MAX_ATTEMPTS = 5;

    private final DriverRepository driverRepository;
    private final DriverVehicleRepository driverVehicleRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final DriverRefreshTokenRepository driverRefreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpSender otpSender;

    @Value("${app.jwt.refresh-token-ttl-seconds}")
    private long refreshTtlSeconds;

    @Override
    @Transactional
    public OtpChallengeResponseDTO requestOtp(RequestOtpRequestDTO request) {
        String phone = normalize(request.phone());

        // Only the latest code is valid: invalidate any outstanding ones first.
        otpCodeRepository.consumeAllByPhone(phone);

        String code = generateOtp();
        otpCodeRepository.save(OtpCode.builder()
                .phone(phone)
                .codeHash(passwordEncoder.encode(code))
                .purpose("LOGIN")
                .expiresAt(OffsetDateTime.now().plusSeconds(OTP_TTL_SECONDS))
                .consumed(false)
                .attempts(0)
                .build());

        otpSender.send(phone, code);
        log.info("OTP issued for phone={}", phone);
        return new OtpChallengeResponseDTO("SMS", OTP_TTL_SECONDS, OTP_RESEND_AFTER);
    }

    @Override
    @Transactional
    public DriverLoginResponseDTO verifyOtp(VerifyOtpRequestDTO request) {
        String phone = normalize(request.phone());

        OtpCode otp = otpCodeRepository.findFirstByPhoneAndConsumedFalseOrderByCreatedAtDesc(phone)
                .orElseThrow(() -> new BadRequestException("No active OTP for this phone"));

        if (otp.getExpiresAt().isBefore(OffsetDateTime.now())) {
            otp.setConsumed(true);
            otpCodeRepository.save(otp);
            throw new BadRequestException("OTP has expired");
        }
        if (otp.getAttempts() >= OTP_MAX_ATTEMPTS) {
            otp.setConsumed(true);
            otpCodeRepository.save(otp);
            throw new TooManyAttemptsException("Too many OTP attempts; request a new code");
        }
        if (!passwordEncoder.matches(request.code(), otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpCodeRepository.save(otp);
            throw new BadRequestException("Invalid OTP code");
        }

        otp.setConsumed(true);
        otpCodeRepository.save(otp);

        Driver driver = driverRepository.findByPhone(phone)
                .orElseGet(() -> registerDriver(phone, request.fullName()));

        return issueTokens(driver);
    }

    @Override
    @Transactional
    public DriverLoginResponseDTO refresh(String refreshToken) {
        DriverRefreshToken stored = driverRefreshTokenRepository.findByTokenHash(sha256(refreshToken))
                .orElseThrow(() -> new InvalidTokenException("Refresh token not recognised"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidTokenException("Refresh token is revoked or expired");
        }

        Driver driver = driverRepository.findById(stored.getDriverId())
                .filter(Driver::isActive)
                .orElseThrow(() -> new InvalidTokenException("Driver no longer active"));

        // Re-issue access token (picks up newly verified plates); keep the same refresh token.
        return DriverLoginResponseDTO.builder()
                .accessToken(jwtService.generateDriverAccessToken(driver, verifiedPlates(driver)))
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessTtlSeconds())
                .role(JwtService.ROLE_DRIVER)
                .build();
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        driverRefreshTokenRepository.findByTokenHash(sha256(refreshToken)).ifPresent(token -> {
            token.setRevoked(true);
            driverRefreshTokenRepository.save(token);
            log.info("Driver logout: refresh token revoked for driverId={}", token.getDriverId());
        });
    }

    private Driver registerDriver(String phone, String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new BadRequestException("fullName is required to register a new driver");
        }
        Driver driver = driverRepository.save(Driver.builder()
                .phone(phone)
                .fullName(fullName.trim())
                .active(true)
                .build());
        log.info("Driver registered: id={}, phone={}", driver.getId(), phone);
        return driver;
    }

    private DriverLoginResponseDTO issueTokens(Driver driver) {
        String accessToken = jwtService.generateDriverAccessToken(driver, verifiedPlates(driver));
        String refreshToken = issueRefreshToken(driver);
        log.info("Driver login success: id={}", driver.getId());
        return DriverLoginResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessTtlSeconds())
                .role(JwtService.ROLE_DRIVER)
                .build();
    }

    private List<String> verifiedPlates(Driver driver) {
        return driverVehicleRepository.findVerifiedPlateNumbers(driver.getId());
    }

    private String issueRefreshToken(Driver driver) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        driverRefreshTokenRepository.save(DriverRefreshToken.builder()
                .driverId(driver.getId())
                .tokenHash(sha256(rawToken))
                .expiresAt(OffsetDateTime.now().plusSeconds(refreshTtlSeconds))
                .revoked(false)
                .build());
        return rawToken;
    }

    private static String generateOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private static String normalize(String phone) {
        return phone.trim();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
