package com.careflow.consultation.controller;

import com.careflow.common.dto.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Mock controller trả dữ liệu bác sỹ hardcode.
 * Sẽ được thay thế bằng gọi tới Identity Service khi sẵn sàng.
 */
@RestController
@RequestMapping("/api/doctors")
public class DoctorMockController {

    private static final List<DoctorInfo> MOCK_DOCTORS = List.of(
            DoctorInfo.builder()
                    .id(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                    .fullName("BS. Nguyễn Văn An")
                    .department("Nội tổng quát")
                    .specialization("Nội khoa")
                    .phone("0901000001")
                    .email("an.nguyen@careflow.vn")
                    .build(),
            DoctorInfo.builder()
                    .id(UUID.fromString("d0000001-0000-0000-0000-000000000002"))
                    .fullName("BS. Trần Thị Bình")
                    .department("Nhi khoa")
                    .specialization("Nhi khoa")
                    .phone("0901000002")
                    .email("binh.tran@careflow.vn")
                    .build(),
            DoctorInfo.builder()
                    .id(UUID.fromString("d0000001-0000-0000-0000-000000000003"))
                    .fullName("BS. Lê Hoàng Cường")
                    .department("Ngoại tổng quát")
                    .specialization("Ngoại khoa")
                    .phone("0901000003")
                    .email("cuong.le@careflow.vn")
                    .build()
    );

    @GetMapping
    public ResponseEntity<ApiResponse<List<DoctorInfo>>> getAllDoctors() {
        return ResponseEntity.ok(ApiResponse.success(MOCK_DOCTORS));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorInfo>> getDoctorById(@PathVariable UUID id) {
        DoctorInfo doctor = MOCK_DOCTORS.stream()
                .filter(d -> d.getId().equals(id))
                .findFirst()
                .orElse(MOCK_DOCTORS.get(0));
        return ResponseEntity.ok(ApiResponse.success(doctor));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DoctorInfo {
        private UUID id;
        private String fullName;
        private String department;
        private String specialization;
        private String phone;
        private String email;
    }
}
