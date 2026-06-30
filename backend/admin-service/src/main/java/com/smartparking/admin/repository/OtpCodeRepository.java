package com.smartparking.admin.repository;

import com.smartparking.admin.entity.OtpCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    /** The most recent still-usable code for a phone (not consumed). */
    Optional<OtpCode> findFirstByPhoneAndConsumedFalseOrderByCreatedAtDesc(String phone);

    /** Invalidate any outstanding codes before issuing a fresh one. */
    @Modifying
    @Query("UPDATE OtpCode o SET o.consumed = true WHERE o.phone = :phone AND o.consumed = false")
    void consumeAllByPhone(@Param("phone") String phone);
}
