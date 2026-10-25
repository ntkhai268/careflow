package com.careflow.prescription.config;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.time.*;
import java.util.*;

@Component
public class AppointmentClient {
    private final RestTemplate restTemplate;
    @Value("${app.appointment-service.url:http://careflow-appointment-service:8083}") private String appointmentServiceUrl;
    public AppointmentClient(RestTemplateBuilder builder){restTemplate=builder.connectTimeout(Duration.ofSeconds(2)).readTimeout(Duration.ofSeconds(3)).build();}
    public void createFollowUpAppointment(UUID patientId,UUID doctorId,UUID consultationId,LocalDate date,String notes){
        createFollowUpAppointment(patientId,doctorId,consultationId,date,notes,doctorId);
    }
    public void createFollowUpAppointment(UUID patientId,UUID doctorId,UUID consultationId,LocalDate date,String notes,UUID actor){
        var headers=PrescriptionRequestHeaders.current();
        if(actor==null || !actor.toString().equals(headers.getFirst("X-User-Id")) || !"DOCTOR".equals(headers.getFirst("X-User-Role")))throw new BusinessException(403,"Cần bác sĩ đang phụ trách ca khám.");
        throw new BusinessException(422,"Chọn khoa và ca còn chỗ cho lịch tái khám.");
    }
    public UUID schedule(UUID patient,UUID consultation,LocalDate date,String department,String slot,String notes,UUID actor){
        if(date==null || date.isBefore(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))) || department==null || department.isBlank() || slot==null || slot.isBlank())
            throw new BusinessException(422,"Chọn ngày, khoa và ca tái khám còn chỗ.");
        HttpHeaders headers=PrescriptionRequestHeaders.current();
        if(actor==null || !actor.toString().equals(headers.getFirst("X-User-Id")) || !"DOCTOR".equals(headers.getFirst("X-User-Role")))throw new BusinessException(403,"Cần bác sĩ đang phụ trách ca khám.");
        headers.setContentType(MediaType.APPLICATION_JSON);headers.set("Idempotency-Key","follow-up-"+consultation+"-"+date);
        var body=new LinkedHashMap<String,Object>();body.put("patientId",patient);body.put("consultationId",consultation);body.put("recommendedDate",date);body.put("department",department);body.put("timeSlot",slot);body.put("note",notes);
        try{
            var response=restTemplate.exchange(appointmentServiceUrl+"/api/appointments/follow-ups",HttpMethod.POST,new HttpEntity<>(body,headers),new ParameterizedTypeReference<ApiResponse<Booking>>(){});
            var booking=response.getBody()==null?null:response.getBody().getData();
            if(booking==null || booking.id()==null || !patient.equals(booking.patientId()) || !consultation.equals(booking.sourceConsultationId()) || !"CONFIRMED".equals(booking.status()))throw new BusinessException(503,"Chưa thể xác nhận kết quả tạo lịch tái khám.");
            return booking.id();
        }catch(HttpStatusCodeException e){int code=e.getStatusCode().value();throw new BusinessException(code>=400 && code<500?code:503,"Chưa tạo được lịch tái khám; toa đã ký được giữ nguyên.");}
        catch(RestClientException e){throw new BusinessException(503,"Chưa xác định được kết quả tạo lịch. Thử lại cùng yêu cầu để đối soát.");}
    }
    @JsonIgnoreProperties(ignoreUnknown=true) public record Booking(UUID id,UUID patientId,UUID sourceConsultationId,String status){}
}
