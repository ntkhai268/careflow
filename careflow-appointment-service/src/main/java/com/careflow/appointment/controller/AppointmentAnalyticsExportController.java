package com.careflow.appointment.controller;
import com.careflow.common.analytics.*;
import com.careflow.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import java.util.UUID;
@RestController
public class AppointmentAnalyticsExportController {
    private final AnalyticsExportSupport export;
    public AppointmentAnalyticsExportController(JdbcTemplate jdbc,@Value("${security.analytics.export-key:}") String key,@Value("${security.audit.allow-loopback-http:false}") boolean loopback) {export=new AnalyticsExportSupport(jdbc,key,loopback);}
    @GetMapping("/internal/analytics/records")
    public ResponseEntity<?> records(HttpServletRequest request,@RequestParam(required=false) UUID id,@RequestParam(required=false) UUID after,@RequestParam(defaultValue="200") int limit) {
        var page=export.read(request,id,after,limit,AnalyticsSnapshot.Source.APPOINTMENT,"""
            SELECT t.id,0 AS revision,t.updated_at,t.appointment_date AS business_date,t.department_id,t.department AS department_code,t.room_id,t.status,t.id AS appointment_id,NULL::uuid AS initial_entry_id,NULL::uuid AS consultation_id,NULL::varchar AS phase,NULL::timestamptz AS checked_in_at,NULL::timestamptz AS eligible_since_at,NULL::timestamptz AS started_at,NULL::timestamptz AS completed_at,0 AS missed_count FROM appointments t WHERE 1=1
            """);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(page));
    }
}
