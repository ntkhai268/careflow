package com.careflow.consultation.controller;
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
public class ConsultationAnalyticsExportController {
    private final AnalyticsExportSupport export;
    public ConsultationAnalyticsExportController(JdbcTemplate jdbc,@Value("${security.analytics.export-key:}") String key,@Value("${security.audit.allow-loopback-http:false}") boolean loopback) {export=new AnalyticsExportSupport(jdbc,key,loopback);}
    @GetMapping("/internal/analytics/records")
    public ResponseEntity<?> records(HttpServletRequest request,@RequestParam(required=false) UUID id,@RequestParam(required=false) UUID after,@RequestParam(defaultValue="200") int limit) {
        var page=export.read(request,id,after,limit,AnalyticsSnapshot.Source.CONSULTATION,"""
            SELECT t.id,COALESCE(t.version,0) AS revision,t.updated_at,COALESCE(t.started_at::date,(t.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date) AS business_date,NULL::uuid AS department_id,t.department_code,NULL::varchar AS room_id,t.status,t.appointment_id,t.queue_entry_id AS initial_entry_id,t.id AS consultation_id,NULL::varchar AS phase,NULL::timestamptz AS checked_in_at,NULL::timestamptz AS eligible_since_at,t.started_at AT TIME ZONE 'Asia/Ho_Chi_Minh' AS started_at,t.completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' AS completed_at,0 AS missed_count FROM consultations t WHERE 1=1
            """);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(page));
    }
}
