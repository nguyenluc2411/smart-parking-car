package com.smartparking.billing.repository;

import com.smartparking.billing.entity.RateSchedule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RateScheduleRepository extends JpaRepository<RateSchedule, UUID> {

    List<RateSchedule> findByRateIdOrderByHourStartAsc(UUID rateId);
}
