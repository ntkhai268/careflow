package com.careflow.identity.recovery;

import com.careflow.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class MailWebhookController {
    private final MailDeliveryTracker tracker;
    public MailWebhookController(MailDeliveryTracker tracker) { this.tracker=tracker; }
    @PostMapping("/api/auth/mail/events")
    public ResponseEntity<?> receive(HttpServletRequest request) throws java.io.IOException {
        tracker.accept(request.getHeader("svix-id"),request.getHeader("svix-timestamp"),request.getHeader("svix-signature"),request.getInputStream().readNBytes(65537));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(null));
    }
}
