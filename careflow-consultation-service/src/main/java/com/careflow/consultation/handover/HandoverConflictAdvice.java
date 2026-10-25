package com.careflow.consultation.handover;
import com.careflow.common.dto.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(basePackages="com.careflow.consultation") @Order(Ordered.HIGHEST_PRECEDENCE)
public class HandoverConflictAdvice {
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiResponse<Void>> conflict(RuntimeException exception) {
        return ResponseEntity.status(409).body(ApiResponse.error(409, "Ca khám hoặc yêu cầu bàn giao vừa thay đổi. Vui lòng tải lại trước khi tiếp tục."));
    }
}
