package com.smartparking.billing.repository;

import com.smartparking.billing.entity.Payment;
import com.smartparking.billing.entity.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Cash actually collected in [from, to), grouped by method — the figure an operator's till is
     * counted against. Keyed on {@code paidAt}, not the session's exit, because outage cash
     * (BR-005-7) is taken on the day the car left but keyed in later.
     *
     * <p>Aggregated in the database rather than by loading each payment: a month of rows only ever
     * collapses to a handful of methods, and the report has no use for the individual rows.
     */
    @Query("""
            SELECT p.method AS method, SUM(p.amountPaid) AS amount, COUNT(p) AS count
            FROM Payment p
            WHERE p.paidAt >= :from AND p.paidAt < :to
            GROUP BY p.method
            ORDER BY p.method
            """)
    List<MethodTotal> sumByMethodInRange(@Param("from") OffsetDateTime from,
                                         @Param("to") OffsetDateTime to);

    /** Projection for {@link #sumByMethodInRange}. */
    interface MethodTotal {
        PaymentMethod getMethod();

        BigDecimal getAmount();

        long getCount();
    }
}
