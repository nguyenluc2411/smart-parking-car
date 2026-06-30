package com.smartparking.admin.repository;

import com.smartparking.admin.entity.Driver;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    Optional<Driver> findByPhone(String phone);
}
