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

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // Seed default Doctor account
        if (!userRepository.existsByUsernameIgnoreCase("doctor")) {
            User doctor = new User();
            doctor.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            doctor.setUsername("doctor");
            doctor.setEmail("doctor@careflow.com");
            doctor.setPasswordHash(passwordEncoder.encode("Password123@"));
            doctor.setRole(UserRole.DOCTOR);
            doctor.setTitle("Bác sĩ Chuyên khoa I");
            doctor.setStatus(UserStatus.ACTIVE);

            userRepository.save(doctor);
            log.info("Successfully seeded default Doctor user (username: doctor, role: DOCTOR)");
        }

        // Seed default Patient account
        if (!userRepository.existsByUsernameIgnoreCase("patient")) {
            User patient = new User();
            patient.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
            patient.setUsername("patient");
            patient.setEmail("patient@careflow.com");
            patient.setPasswordHash(passwordEncoder.encode("Password123@"));
            patient.setRole(UserRole.PATIENT);
            patient.setStatus(UserStatus.ACTIVE);

            userRepository.save(patient);
            log.info("Successfully seeded default Patient user (username: patient, role: PATIENT)");
        }

        // Seed default Admin account
        if (!userRepository.existsByUsernameIgnoreCase("admin")) {
            User admin = new User();
            admin.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
            admin.setUsername("admin");
            admin.setEmail("admin@careflow.com");
            admin.setPasswordHash(passwordEncoder.encode("Password123@"));
            admin.setRole(UserRole.ADMIN);
            admin.setStatus(UserStatus.ACTIVE);

            userRepository.save(admin);
            log.info("Successfully seeded default Admin user (username: admin, role: ADMIN)");
        }
    }
}
