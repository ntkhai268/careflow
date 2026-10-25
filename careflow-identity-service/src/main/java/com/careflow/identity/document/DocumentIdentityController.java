package com.careflow.identity.document;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/auth/identity-document")
public class DocumentIdentityController {
    private final DocumentIdentityService service;
    public DocumentIdentityController(DocumentIdentityService service) { this.service=service; }
    public record Submission(String documentNumber,String fullName,String birthDate,boolean consent) { }
    public record Review(boolean accepted,boolean originalSeen,boolean holderMatched,boolean informationMatched,String reference) { }
    public record Status(boolean available,String method,DocumentIdentityService.Summary latest) { }
    @GetMapping("/status") public ResponseEntity<?> status(Authentication auth) {
        UUID owner=principal(auth,"PATIENT");return ok(new Status(service.ready(),"IN_PERSON",service.latest(owner)));
    }
    @PostMapping(value="/requests",consumes=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<?> submit(Authentication auth,
            @RequestHeader("Idempotency-Key") UUID key,@RequestBody Submission body) {
        return ok(service.submit(principal(auth,"PATIENT"),key,new DocumentIdentityService.Fields(body.documentNumber(),body.fullName(),body.birthDate()),body.consent()));
    }
    @PostMapping("/requests/{id}/withdraw") public ResponseEntity<?> withdraw(Authentication auth,@PathVariable UUID id) {
        return ok(service.withdraw(principal(auth,"PATIENT"),id));
    }
    @GetMapping("/pending") public ResponseEntity<?> pending(Authentication auth,@RequestParam(defaultValue="0") int page) {
        principal(auth,"STAFF","ADMIN");return ok(service.pending(page));
    }
    @GetMapping("/requests/{id}") public ResponseEntity<?> detail(Authentication auth,@PathVariable UUID id) {
        principal(auth,"STAFF","ADMIN");return ok(service.detail(id));
    }
    @PostMapping("/requests/{id}/review") public ResponseEntity<?> review(Authentication auth,@PathVariable UUID id,@RequestBody Review body) {
        UUID reviewer=principal(auth,"STAFF","ADMIN");
        return ok(service.review(reviewer,id,body.accepted(),body.originalSeen(),body.holderMatched(),body.informationMatched(),body.reference()));
    }
    private UUID principal(Authentication auth,String... roles) {
        if(auth==null || !auth.isAuthenticated()) throw new BusinessException(401,"Vui lòng đăng nhập.");
        if(auth.getAuthorities().stream().noneMatch(a->Arrays.stream(roles).anyMatch(r->("ROLE_"+r).equals(a.getAuthority()))))
            throw new BusinessException(403,"Không có quyền truy cập chức năng đối chiếu này.");
        return UUID.fromString(auth.getName());
    }
    private ResponseEntity<?> ok(Object result) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(result)); }
}
