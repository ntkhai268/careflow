package com.careflow.consultation.handover;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;
/** Serializes starts/acceptances for one doctor across application instances. Call inside a transaction. */
@Component @RequiredArgsConstructor
public class DoctorWorkloadLock {
    private final JdbcTemplate jdbc;
    public void acquireVisit(UUID queueEntryId){
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "consultation-visit:"+queueEntryId);
    }
    public void acquire(UUID doctorId) {
        jdbc.update("INSERT INTO consultation_doctor_locks (doctor_id) VALUES (?) ON CONFLICT DO NOTHING", doctorId);
        jdbc.queryForObject("SELECT doctor_id FROM consultation_doctor_locks WHERE doctor_id = ? FOR UPDATE", UUID.class, doctorId);
    }
}
