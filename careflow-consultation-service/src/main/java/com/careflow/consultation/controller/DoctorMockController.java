package com.careflow.consultation.controller;

import com.careflow.common.exception.BusinessException;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** Retired endpoint: doctor directory data belongs to Hospital Directory. */
@RestController
@RequestMapping("/api/doctors")
public class DoctorMockController {
    @GetMapping
    public Object getAllDoctors() { throw retired(); }
    @GetMapping("/{id}")
    public Object getDoctorById(@PathVariable UUID id) { throw retired(); }
    private BusinessException retired() { return new BusinessException(410,"Danh bạ cũ đã ngừng hỗ trợ. Sử dụng /api/directory/doctors."); }
}
