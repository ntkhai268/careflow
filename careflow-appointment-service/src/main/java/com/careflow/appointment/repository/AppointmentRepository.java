package com.careflow.appointment.repository;

import com.careflow.appointment.model.Appointment;
import com.careflow.appointment.model.AppointmentStatus;
import com.careflow.appointment.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByPatientIdOrderByAppointmentDateDesc(UUID patientId);

    List<Appointment> findByDepartmentAndAppointmentDateOrderByTimeSlot(
            Department department, LocalDate appointmentDate);

    List<Appointment> findByStatusOrderByCreatedAtAsc(AppointmentStatus status);

    List<Appointment> findByAppointmentDateAndStatusOrderByTimeSlot(
            LocalDate appointmentDate, AppointmentStatus status);

    boolean existsByPatientIdAndAppointmentDateAndTimeSlotAndStatusNot(
            UUID patientId, LocalDate appointmentDate, String timeSlot, AppointmentStatus status);
}
