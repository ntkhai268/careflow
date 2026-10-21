package com.careflow.lab.controller;
import com.careflow.common.dto.ApiResponse;
import com.careflow.lab.dto.response.LabOrderResponse;
import com.careflow.lab.service.LabOrderService;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/labs/orders")
public class LabReviewSynchronizationController {
    private final LabOrderService orders;
    public LabReviewSynchronizationController(LabOrderService orders){this.orders=orders;}
    @PostMapping("/{id}/synchronize-review") public ApiResponse<LabOrderResponse> synchronize(@PathVariable UUID id,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role,@RequestHeader(value="X-Correlation-Id",required=false) String correlation){return ApiResponse.success(orders.synchronizeReviewQueue(id,actor,role,correlation));}
}
