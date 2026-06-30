package com.smartparking.admin.config;

import com.smartparking.admin.entity.User;
import com.smartparking.admin.entity.enums.Role;
import com.smartparking.admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstraps a default ADMIN on first start (when the username is absent), so the system is usable
 * out of the box. The password is BCrypt-encoded at runtime (no fabricated hash in a migration).
 * Change/remove the default credentials for production.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-username}")
    private String adminUsername;

    @Value("${app.seed.admin-email}")
    private String adminEmail;

    @Value("${app.seed.admin-password}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername(adminUsername).isPresent()) {
            return;
        }
        userRepository.save(User.builder()
                .username(adminUsername)
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .active(true)
                .build());
        log.warn("Seeded default ADMIN user '{}'. Change the password before production!", adminUsername);
    }
}
