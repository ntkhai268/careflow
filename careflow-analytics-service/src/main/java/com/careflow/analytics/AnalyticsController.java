package com.careflow.analytics;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.security.ActiveSessionClient.Principal;
import com.careflow.common.security.ActiveSessionFilter;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import java.time.*;
import java.util.*;
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsReportService reports;private final ReconciliationService sync;private final SnapshotStore store;private final boolean demo;
    public AnalyticsController(AnalyticsReportService reports,ReconciliationService sync,SnapshotStore store,@Value("${analytics.demo-enabled:false}") boolean demo){this.reports=reports;this.sync=sync;this.store=store;this.demo=demo;}
    private static void admin(Principal user){if(user==null||!"ADMIN".equals(user.role()))throw new BusinessException(403,"Chỉ quản trị viên được xem Analytics.");}
    @GetMapping("/report")
    public ResponseEntity<?> report(@RequestAttribute(ActiveSessionFilter.VERIFIED_PRINCIPAL_ATTRIBUTE) Principal user,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) UUID departmentId,@RequestParam(required=false) String roomId){
        admin(user);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(reports.report(from,to,departmentId,roomId)));
    }
    @PostMapping("/reconcile")
    public ResponseEntity<?> reconcile(@RequestAttribute(ActiveSessionFilter.VERIFIED_PRINCIPAL_ATTRIBUTE) Principal user){admin(user);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(sync.run(user.userId())));}
    @PostMapping("/rebuild")
    public ResponseEntity<?> rebuild(@RequestAttribute(ActiveSessionFilter.VERIFIED_PRINCIPAL_ATTRIBUTE) Principal user){admin(user);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(Map.of("rebuiltRecords",store.rebuild(demo,user.userId()))));}
}
