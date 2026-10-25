package com.careflow.consultation.handover;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** Idempotent supporting data only. Clinical requests require real doctors to submit them. */
@Component @RequiredArgsConstructor
public class HandoverDataInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    public void run(String... args) {
        jdbc.update("INSERT INTO consultation_doctor_locks(doctor_id) SELECT DISTINCT COALESCE(responsible_doctor_id,doctor_id) FROM consultations ON CONFLICT DO NOTHING");
    }
}
