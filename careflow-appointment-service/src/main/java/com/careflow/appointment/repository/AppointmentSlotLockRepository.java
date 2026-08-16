package com.careflow.appointment.repository;

import com.careflow.appointment.model.AppointmentSlotLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface AppointmentSlotLockRepository extends JpaRepository<AppointmentSlotLock, String> {

    @Modifying
    @Query(value = "INSERT INTO appointment_slot_locks(slot_key) VALUES (:slotKey) "
            + "ON CONFLICT (slot_key) DO NOTHING", nativeQuery = true)
    void ensureSlotExists(@Param("slotKey") String slotKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lock from AppointmentSlotLock lock where lock.slotKey = :slotKey")
    Optional<AppointmentSlotLock> findBySlotKeyForUpdate(@Param("slotKey") String slotKey);
}
