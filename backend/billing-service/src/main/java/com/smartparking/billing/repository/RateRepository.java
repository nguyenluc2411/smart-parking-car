package com.smartparking.billing.repository;

import com.smartparking.billing.entity.Rate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RateRepository extends JpaRepository<Rate, UUID> {

    /**
     * The rate effective at {@code at}: started, and either open-ended or not yet expired.
     * Most recently effective first; callers take the first row.
     */
    @Query("""
            SELECT r FROM Rate r
            WHERE r.effectiveFrom <= :at
              AND (r.effectiveTo IS NULL OR r.effectiveTo > :at)
            ORDER BY r.effectiveFrom DESC
            """)
    List<Rate> findEffective(@Param("at") OffsetDateTime at, Pageable pageable);
}
