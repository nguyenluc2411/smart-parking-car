package com.smartparking.admin.repository;

import com.smartparking.admin.entity.DriverRefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRefreshTokenRepository extends JpaRepository<DriverRefreshToken, UUID> {

    Optional<DriverRefreshToken> findByTokenHash(String tokenHash);
}
