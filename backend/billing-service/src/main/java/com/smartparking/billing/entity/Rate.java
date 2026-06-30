package com.smartparking.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
 * A pricing rate effective over a time window. Maps table {@code rates} in billing_db.
 */
@Entity
@Table(name = "rates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rate {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "rate_per_min", nullable = false)
    private BigDecimal ratePerMin;

    @Column(name = "peak_multiplier", nullable = false)
    private BigDecimal peakMultiplier;

    @Column(name = "overnight_flat", nullable = false)
    private BigDecimal overnightFlat;

    @Column(name = "min_charge", nullable = false)
    private BigDecimal minCharge;

    @Column(name = "effective_from", nullable = false)
    private OffsetDateTime effectiveFrom;

    @Column(name = "effective_to")
    private OffsetDateTime effectiveTo;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
