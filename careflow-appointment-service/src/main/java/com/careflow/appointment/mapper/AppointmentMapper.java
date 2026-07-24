package com.careflow.appointment.mapper;

import com.careflow.appointment.dto.response.AppointmentResponse;
import com.careflow.appointment.model.Appointment;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class AppointmentMapper {

    private AppointmentMapper() {}

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault()) : null;
    }

    public static AppointmentResponse toResponse(Appointment appointment) {
        return AppointmentResponse.builder()
                .id(appointment.getId())
                .patientId(appointment.getPatientId())
                .patientName(appointment.getPatientName())
                .department(appointment.getDepartment().name())
                .departmentDisplayName(appointment.getDepartment().getDisplayName())
                .doctorId(appointment.getDoctorId())
                .doctorName(appointment.getDoctorName())
                .appointmentDate(appointment.getAppointmentDate())
                .timeSlot(appointment.getTimeSlot())
                .status(appointment.getStatus().name())
                .statusDisplayName(appointment.getStatus().getDisplayName())
                .reason(appointment.getReason())
                .notes(appointment.getNotes())
                .queueNumber(appointment.getQueueNumber())
                .createdAt(toLocalDateTime(appointment.getCreatedAt()))
                .updatedAt(toLocalDateTime(appointment.getUpdatedAt()))
                .build();
    }
}
