package com.careflow.appointment.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.appointment.client.AppointmentConsultationClient;
import com.careflow.appointment.dto.request.UpdateAppointmentStatusRequest;
import com.careflow.appointment.dto.response.AppointmentResponse;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.appointment.service.AppointmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class ClinicalAppointmentCompletionController {
    private final AppointmentConsultationClient consultations;private final AppointmentRepository appointments;private final AppointmentService service;
    public ClinicalAppointmentCompletionController(AppointmentConsultationClient consultations,AppointmentRepository appointments,AppointmentService service){this.consultations=consultations;this.appointments=appointments;this.service=service;}
    public record Request(@NotNull UUID appointmentId,@NotNull UUID consultationId){}
    @PostMapping("/api/appointments/clinical-completion")
    public ApiResponse<AppointmentResponse> complete(@Valid @RequestBody Request request,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){
        if(!"DOCTOR".equals(role))throw new BusinessException(403,"Cần bác sĩ phụ trách ca khám.");
        var appointment=appointments.findById(request.appointmentId()).orElseThrow(()->new BusinessException(404,"Không tìm thấy lịch khám."));
        try{
            var response=consultations.getByAppointment(request.appointmentId());var visits=response==null?null:response.getData();
            if(visits==null)throw new BusinessException(503,"Chưa xác minh được ca khám đã hoàn tất.");
            if(visits.stream().noneMatch(c->request.consultationId().equals(c.id()) && appointment.getPatientId().equals(c.patientId()) && actor.equals(c.doctorId()) && "COMPLETED".equals(c.status())))throw new BusinessException(409,"Ca khám nguồn chưa hoàn tất hoặc không khớp lịch/bác sĩ.");
            return ApiResponse.success(service.updateAppointmentStatus(request.appointmentId(),UpdateAppointmentStatusRequest.builder().status("COMPLETED").build(),actor,role));
        }catch(feign.FeignException e){throw new BusinessException(e.status()==403 || e.status()==404?403:503,"Chưa xác minh được quyền hoàn tất lịch khám.");}
    }
}
