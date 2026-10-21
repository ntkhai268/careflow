package com.careflow.appointment.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.appointment.client.AppointmentConsultationClient;
import com.careflow.appointment.dto.response.AppointmentResponse;
import com.careflow.appointment.mapper.AppointmentMapper;
import com.careflow.appointment.repository.AppointmentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@RestController
@RequestMapping("/api/appointments/follow-ups/consultations")
public class FollowUpQueryController {
    private final AppointmentConsultationClient consultations;private final AppointmentRepository appointments;private final JdbcTemplate jdbc;
    public FollowUpQueryController(AppointmentConsultationClient consultations,AppointmentRepository appointments,JdbcTemplate jdbc){this.consultations=consultations;this.appointments=appointments;this.jdbc=jdbc;}
    @GetMapping("/{id}")
    @Transactional(readOnly=true)
    public ApiResponse<List<AppointmentResponse>> get(@PathVariable UUID id,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){
        if(!"DOCTOR".equals(role))throw new BusinessException(403,"Chỉ bác sĩ phụ trách được xem lịch tái khám của ca.");
        try{
            var response=consultations.getById(id);var c=response==null?null:response.getData();
            if(c==null || !id.equals(c.id()) || c.patientId()==null || !actor.equals(c.doctorId()))throw new BusinessException(403,"Không có quyền với ca khám này.");
            var ids=jdbc.query("SELECT id FROM appointments WHERE source_consultation_id=? ORDER BY appointment_date,time_slot,id",(rs,n)->rs.getObject(1,UUID.class),id);
            var result=appointments.findAllById(ids).stream().filter(a->c.patientId().equals(a.getPatientId())).map(AppointmentMapper::toResponse).toList();
            return ApiResponse.success(result);
        }catch(feign.FeignException e){throw new BusinessException(e.status()==403 || e.status()==404?403:503,"Chưa xác minh được quyền ca khám.");}
    }
}
