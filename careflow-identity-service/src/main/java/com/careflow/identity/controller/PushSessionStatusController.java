package com.careflow.identity.controller;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.identity.security.CallerRegistry;
import com.careflow.identity.service.PushSessionStatusService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;
@RestController
public class PushSessionStatusController {
    private final CallerRegistry callers;private final PushSessionStatusService sessions;
    public PushSessionStatusController(CallerRegistry callers,PushSessionStatusService sessions){this.callers=callers;this.sessions=sessions;}
    public record Request(@NotNull UUID userId,@NotNull UUID sessionId){}
    @PostMapping("/internal/notifications/session-status")
    public ResponseEntity<?> status(@Valid @RequestBody Request body,HttpServletRequest request){
        String caller=request.getHeader("X-Careflow-Caller");if(!"notification-service".equals(caller))throw new BusinessException(403,"Caller không được kiểm tra quyền push.");
        callers.requireTransport(request,caller);callers.requireDelegation(caller,request.getHeader("X-Careflow-Caller-Key"),"POST","/internal/notifications/session-status");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(Map.of("active",sessions.active(body.userId(),body.sessionId()))));
    }
}
