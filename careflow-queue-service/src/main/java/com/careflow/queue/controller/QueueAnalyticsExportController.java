package com.careflow.queue.controller;
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
public class QueueAnalyticsExportController {
    private final AnalyticsExportSupport export;
    public QueueAnalyticsExportController(JdbcTemplate jdbc,@Value("${security.analytics.export-key:}") String key,@Value("${security.audit.allow-loopback-http:false}") boolean loopback) {export=new AnalyticsExportSupport(jdbc,key,loopback);}
    @GetMapping("/internal/analytics/records")
    public ResponseEntity<?> records(HttpServletRequest request,@RequestParam(required=false) UUID id,@RequestParam(required=false) UUID after,@RequestParam(defaultValue="200") int limit) {
        var page=export.read(request,id,after,limit,AnalyticsSnapshot.Source.QUEUE,"""
            SELECT t.id,t.version AS revision,t.updated_at,t.queue_date AS business_date,t.department_id,t.department_code,COALESCE(c.room_code,t.service_point_id) AS room_id,t.status,t.appointment_id,CASE WHEN t.consultation_phase='INITIAL' THEN t.id ELSE NULL END AS initial_entry_id,t.consultation_id,t.consultation_phase AS phase,t.checked_in_at,t.eligible_since_at,t.started_at,t.completed_at,t.missed_count FROM queue.queue_entries t LEFT JOIN queue.queue_configs c ON c.id=t.queue_config_id WHERE t.queue_type='CONSULTATION'
            """);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(page));
    }
}
