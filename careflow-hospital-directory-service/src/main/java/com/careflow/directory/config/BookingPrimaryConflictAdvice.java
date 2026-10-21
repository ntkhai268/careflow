package com.careflow.directory.config;
import com.careflow.common.dto.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(basePackages="com.careflow.directory") @Order(Ordered.HIGHEST_PRECEDENCE)
public class BookingPrimaryConflictAdvice {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException e) { return ResponseEntity.status(409).body(ApiResponse.error(409,"Phân công bị trùng. Mỗi phòng chỉ có một bác sĩ nhận lịch mặc định đang hoạt động.")); }
}
