package com.careflow.appointment.service;

import com.careflow.appointment.dto.request.CreateAppointmentRequest;
import com.careflow.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class AppointmentCommandStore {
    private final JdbcTemplate jdbc;
    public AppointmentCommandStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Command(String scope,String fingerprint,UUID result){}
    public Command begin(UUID owner,String key,CreateAppointmentRequest request,UUID doctor){
        String scope=key==null || key.isBlank()?null:owner+":"+key;
        if(scope!=null && (scope.length()>255 || scope.codePoints().anyMatch(Character::isISOControl)))throw new BusinessException(400,"Idempotency-Key không hợp lệ.");
        String fingerprint=digest(Arrays.asList(owner,request.getPatientId(),request.getAppointmentDate(),AppointmentService.normalizeTimeSlot(request.getTimeSlot()),request.getDepartment().toUpperCase(Locale.ROOT),request.getReason(),request.getServiceCode()==null?"GENERAL_CONSULTATION":request.getServiceCode(),request.getPriceVersion(),doctor).stream().map(value->value==null?"-1:":value.toString().length()+":"+value).collect(java.util.stream.Collectors.joining()));
        if(scope!=null){
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","appointment-command:"+scope);
            var rows=jdbc.queryForList("SELECT fingerprint,appointment_id FROM appointment_booking_commands WHERE scope_key=?",scope);
            if(!rows.isEmpty()){
                if(!fingerprint.equals(rows.getFirst().get("fingerprint")))throw new BusinessException(409,"Idempotency-Key đã được dùng cho yêu cầu khác.");
                return new Command(scope,fingerprint,(UUID)rows.getFirst().get("appointment_id"));
            }
        }
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","appointment-patient:"+request.getPatientId()+":"+request.getAppointmentDate()+":"+AppointmentService.normalizeTimeSlot(request.getTimeSlot()));
        return new Command(scope,fingerprint,null);
    }
    public void complete(Command command,UUID appointment){
        if(command.scope()!=null)jdbc.update("INSERT INTO appointment_booking_commands(scope_key,fingerprint,appointment_id) VALUES (?,?,?)",command.scope(),command.fingerprint(),appointment);
    }
    private static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception error){throw new IllegalStateException(error);}}
}
