package com.careflow.ai.voice;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=VoicePrescriptionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VoiceExceptionHandler {
    @ExceptionHandler(BusinessException.class) ResponseEntity<?> business(BusinessException e){return error(e.getStatus(),e.getMessage());}
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> size(){return error(413,"Audio vượt giới hạn");}
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,
        MissingRequestHeaderException.class,MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class,MethodArgumentTypeMismatchException.class,IllegalArgumentException.class})
    ResponseEntity<?> invalid(Exception e){return error(400,"Yêu cầu voice-prescription không hợp lệ");}
    @ExceptionHandler(Exception.class) ResponseEntity<?> failed(Exception e){return error(503,"Dịch vụ voice-prescription chưa sẵn sàng");}
    private ResponseEntity<?> error(int status,String message){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(ApiResponse.error(status,message));}
}
