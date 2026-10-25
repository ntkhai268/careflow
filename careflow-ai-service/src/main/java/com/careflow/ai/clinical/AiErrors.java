package com.careflow.ai.clinical;
import com.careflow.ai.controller.AiClinicalController;import com.careflow.common.dto.ApiResponse;import com.careflow.common.exception.BusinessException;import org.springframework.web.bind.annotation.*;import org.springframework.http.*;
@RestControllerAdvice(assignableTypes={AiClinicalController.class,KnowledgeController.class,AiCapabilitiesController.class})
public class AiErrors {
    @ExceptionHandler(BusinessException.class) public ResponseEntity<?> business(BusinessException e){return ResponseEntity.status(e.getStatus()).body(ApiResponse.error(e.getStatus(),e.getMessage()));}
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) public ResponseEntity<?> invalid(Exception e){return ResponseEntity.badRequest().body(ApiResponse.error(400,"Yêu cầu trợ lý không hợp lệ"));}
    @ExceptionHandler(Exception.class) public ResponseEntity<?> unavailable(Exception e){return ResponseEntity.status(503).body(ApiResponse.error(503,"Nguồn hoặc nhật ký trợ lý chưa sẵn sàng"));}
}
