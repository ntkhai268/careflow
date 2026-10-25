package com.careflow.lab.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.lab.service.LabCatalogService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/labs/services")
public class LabCatalogController {
    private final LabCatalogService catalog;
    public LabCatalogController(LabCatalogService catalog) { this.catalog=catalog; }
    @GetMapping
    public ApiResponse<List<LabCatalogService.Definition>> list(@RequestHeader("X-User-Role") String role) {
        if(!Set.of("DOCTOR","ADMIN","LAB_TECHNICIAN").contains(role)) throw new BusinessException(403,"Không có quyền xem danh mục CLS");
        return ApiResponse.success(catalog.list());
    }
}
