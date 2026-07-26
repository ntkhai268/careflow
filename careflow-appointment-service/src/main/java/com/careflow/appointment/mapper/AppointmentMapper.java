package com.careflow.appointment.mapper;

import com.careflow.appointment.dto.response.AppointmentResponse;
import com.careflow.appointment.model.Appointment;

public class AppointmentMapper {

    private AppointmentMapper() {}

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
                .createdAt(appointment.getCreatedAt())
                .updatedAt(appointment.getUpdatedAt())
                .build();
    }
}
