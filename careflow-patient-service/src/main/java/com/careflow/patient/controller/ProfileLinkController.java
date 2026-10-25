package com.careflow.patient.controller;
import com.careflow.common.dto.ApiResponse;
import com.careflow.patient.service.ProfileLinkService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/patients/profile-links")
public class ProfileLinkController {
    private final ProfileLinkService links;
    public ProfileLinkController(ProfileLinkService links){this.links=links;}
    public record Authorization(@NotNull UUID requestId,@NotBlank String token){}
    public record Proposal(@NotNull UUID requestId,@NotNull UUID patientId,@NotBlank @Size(max=50) String roomId,@NotBlank String token,@NotBlank @Size(max=80) String reference,boolean originalSeen){}
    public record Decision(boolean accepted){}
    @PostMapping("/authorization")
    public ResponseEntity<?> authorize(@Valid @RequestBody Authorization body,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){return ok(links.authorize(actor,role,body.requestId(),body.token()));}
    @GetMapping("/mine")
    public ResponseEntity<?> mine(@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){return ok(links.mine(actor,role));}
    @GetMapping("/reception")
    public ResponseEntity<?> reception(@RequestParam String roomId,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){return ok(links.room(roomId,actor,role));}
    @PostMapping
    public ResponseEntity<?> propose(@Valid @RequestBody Proposal body,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){return ok(links.propose(body.requestId(),body.patientId(),body.roomId(),body.token(),body.reference(),body.originalSeen(),actor,role));}
    @PostMapping("/{id}/decision")
    public ResponseEntity<?> decide(@PathVariable UUID id,@RequestBody Decision body,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){return ok(links.decide(id,body.accepted(),actor,role));}
    @PostMapping("/{id}/revoke")
    public ResponseEntity<?> revoke(@PathVariable UUID id,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){return ok(links.revoke(id,actor,role));}
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable UUID id,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){return ok(links.rejectReview(id,actor,role));}
    private ResponseEntity<?> ok(Object data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));}
}
