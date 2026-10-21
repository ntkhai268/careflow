package com.careflow.directory.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.directory.dto.request.ServicePriceRequest;
import com.careflow.directory.service.ServiceCatalogService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/directory/services")
public class ServiceCatalogController {
    private final ServiceCatalogService catalog;
    public ServiceCatalogController(ServiceCatalogService catalog) { this.catalog=catalog; }
    @GetMapping
    public ResponseEntity<?> list() { return ok(catalog.list(false)); }
    @GetMapping("/admin")
    public ResponseEntity<?> admin(@RequestHeader(value="X-User-Role",required=false) String role) {
        requireAdmin(role);return ok(catalog.list(true));
    }
    @GetMapping("/{code}")
    public ResponseEntity<?> current(@PathVariable String code) { return ok(catalog.current(code)); }
    @PostMapping
    public ResponseEntity<?> publish(@Valid @RequestBody ServicePriceRequest request,
            @RequestHeader(value="X-User-Role",required=false) String role,
            @RequestHeader(value="X-User-Id",required=false) UUID actor) {
        requireAdmin(role);
        if(actor==null) throw new BusinessException(401,"Cần xác thực người quản trị.");
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(ApiResponse.success(catalog.publish(request,actor)));
    }
    private void requireAdmin(String role) {
        if(!"ADMIN".equals(role)) throw new BusinessException(403,"Chỉ quản trị viên được quản lý bảng giá.");
    }
    private ResponseEntity<?> ok(Object data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data)); }
}
