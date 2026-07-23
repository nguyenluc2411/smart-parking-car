package com.smartparking.billing.entity;

import com.smartparking.billing.entity.enums.ReservationFeeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A driver's booking fee for a reservation (BR-009-11). Maps table {@code reservation_fees} in
 * billing_db. Deliberately separate from {@link Payment} — see V7 migration comment.
 */
@Entity
@Table(name = "reservation_fees")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationFee {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** parking-service reservation id — opaque cross-service reference, no FK (Database Per Service). */
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "plate_number", nullable = false, length = 15)
    private String plateNumber;

    @Column(name = "reservation_start_time", nullable = false)
    private OffsetDateTime reservationStartTime;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationFeeStatus status;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "payos_order_code")
    private Long payosOrderCode;

    @Column(name = "provider_ref", length = 100)
    private String providerRef;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "refunded_at")
    private OffsetDateTime refundedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
