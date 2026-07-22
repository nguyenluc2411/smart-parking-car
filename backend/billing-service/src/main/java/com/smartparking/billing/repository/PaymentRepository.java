package com.smartparking.billing.repository;

import com.smartparking.billing.entity.Payment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Payments for a set of invoices — used to break revenue reports down by method. */
    List<Payment> findByInvoiceIdIn(List<UUID> invoiceIds);
}
