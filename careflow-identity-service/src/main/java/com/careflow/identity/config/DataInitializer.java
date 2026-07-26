package com.careflow.identity.config;

import com.careflow.identity.domain.User;
import com.careflow.identity.domain.UserRole;
import com.careflow.identity.domain.UserStatus;
import com.careflow.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        seedUser(UUID.fromString("d0000001-0000-0000-0000-000000000001"), "an@careflow.vn", "an@careflow.vn",
                "BS. Nguyễn Văn An - Bác sĩ Chuyên khoa I - Khoa Nội tổng quát", UserRole.DOCTOR);

        seedUser(UUID.fromString("11111111-1111-1111-1111-111111111111"), "doctor", "doctor@careflow.com",
                "BS. Phạm Hoàng Nam - Bác sĩ Chuyên khoa I", UserRole.DOCTOR);

        seedUser(UUID.fromString("22222222-2222-2222-2222-222222222222"), "patient", "patient@careflow.com",
                "Nguyễn Văn Bệnh", UserRole.PATIENT);

        seedUser(UUID.fromString("33333333-3333-3333-3333-333333333333"), "admin", "admin@careflow.com",
                "Quản trị hệ thống", UserRole.ADMIN);
    }

    private void seedUser(UUID id, String username, String email, String title, UserRole role) {
        try {
            if (!userRepository.existsById(id) && !userRepository.existsByUsernameIgnoreCase(username)) {
                User user = new User();
                user.setId(id);
                user.setUsername(username);
                user.setEmail(email);
                user.setPasswordHash(passwordEncoder.encode("Password123@"));
                user.setRole(role);
                user.setTitle(title);
                user.setStatus(UserStatus.ACTIVE);

                entityManager.persist(user);
                log.info("Successfully seeded user: {} (role: {})", username, role);
            }
        } catch (Exception e) {
            log.warn("Skipping seed for user {} (ID: {}): {}", username, id, e.getMessage());
        }
    }
}
