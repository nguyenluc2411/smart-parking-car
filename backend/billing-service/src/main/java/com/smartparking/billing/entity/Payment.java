package com.smartparking.billing.entity;

import com.smartparking.billing.entity.enums.PayerType;
import com.smartparking.billing.entity.enums.PaymentMethod;
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
 * A payment recorded against an invoice. Maps table {@code payments} in billing_db.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type", nullable = false, length = 20)
    private PayerType payerType;

    @Column(name = "received_by")
    private UUID receivedBy;            // operator user_id; NULL when payerType=DRIVER

    @Column(name = "driver_id")
    private UUID driverId;              // driver id when payerType=DRIVER (online)

    @Column(name = "provider_ref", length = 100)
    private String providerRef;        // payment-gateway transaction ref (online)

    @Column(name = "offline_voucher_no", length = 20)
    private String offlineVoucherNo;   // paper voucher serial; set only when method=CASH_OFFLINE

    @Column(name = "note")
    private String note;

    @Column(name = "paid_at", nullable = false)
    private OffsetDateTime paidAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (paidAt == null) {
            paidAt = OffsetDateTime.now();
        }
    }
}
