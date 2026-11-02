package com.careflow.lab.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.lab.service.LabCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/labs/services")
public class LabCatalogController {

    private static final Set<String> ALLOWED_ROLES = Set.of("DOCTOR", "ADMIN", "LAB_TECHNICIAN");

    private final LabCatalogService catalog;

    public LabCatalogController(LabCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ApiResponse<List<LabCatalogService.Definition>> list(@RequestHeader("X-User-Role") String role) {
        if (!ALLOWED_ROLES.contains(role)) {
            throw new BusinessException(403, "Không có quyền xem danh mục CLS");
        }
        return ApiResponse.success(catalog.list());
    }
}
