package com.careflow.identity.document;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.identity.security.CallerRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class ConfirmedDocumentMatchController {
    private final DocumentIdentityService documents;
    private final CallerRegistry callers;
    public ConfirmedDocumentMatchController(DocumentIdentityService documents,CallerRegistry callers){this.documents=documents;this.callers=callers;}
    public record Match(UUID owner,DocumentIdentityService.Fields fields){}
    public record Result(UUID checkId){}
    @PostMapping("/api/auth/identity-document/confirmed-match")
    public ResponseEntity<?> match(@RequestBody Match body,Authentication auth,HttpServletRequest request){
        String caller=request.getHeader("X-Careflow-Caller");
        if(!"patient-service".equals(caller))throw new BusinessException(403,"Chỉ dịch vụ hồ sơ được đối chiếu liên kết.");
        callers.requireTransport(request,caller);callers.requireDelegation(caller,request.getHeader("X-Careflow-Caller-Key"),"POST",request.getRequestURI());
        if(auth==null || !auth.isAuthenticated() || body.owner()==null)throw new BusinessException(401,"Thiếu tài khoản xác thực.");
        var roles=auth.getAuthorities().stream().map(a->a.getAuthority()).toList();
        if(!roles.contains("ROLE_ADMIN") && !roles.contains("ROLE_STAFF") && !(roles.contains("ROLE_PATIENT") && body.owner().toString().equals(auth.getName())))throw new BusinessException(403,"Không có quyền đối chiếu tài khoản này.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(new Result(documents.confirmedMatch(body.owner(),body.fields()))));
    }
}
