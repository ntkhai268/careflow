package com.careflow.ai.controller;
import com.careflow.ai.service.AiClinicalAssistantService;import com.careflow.common.dto.ApiResponse;import jakarta.servlet.http.HttpServletRequest;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.web.bind.annotation.*;import org.springframework.http.*;import java.util.*;
@RestController
@RequestMapping("/api/ai")
public class AiClinicalController {
    private final AiClinicalAssistantService service;public AiClinicalController(AiClinicalAssistantService service){this.service=service;}
    public record Question(@NotNull UUID consultationId,@Size(max=2000) String question,@Size(max=2000) String message,UUID parentInteractionId){}
    @PostMapping({"/clinical-chat","/clinical-suggestions"}) public ResponseEntity<?> ask(@Valid @RequestBody Question q,HttpServletRequest request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.engine().ask(q.consultationId(),q.message()==null?q.question():q.message(),q.parentInteractionId(),request)));}
    @GetMapping("/interactions/{id}") public ResponseEntity<?> read(@PathVariable UUID id,HttpServletRequest request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.engine().read(id,request)));}
    @GetMapping("/consultations/{id}/interactions") public ResponseEntity<?> history(@PathVariable UUID id,HttpServletRequest request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.engine().history(id,request)));}
}
