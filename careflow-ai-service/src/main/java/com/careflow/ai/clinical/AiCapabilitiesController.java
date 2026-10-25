package com.careflow.ai.clinical;
import com.careflow.common.dto.ApiResponse;import jakarta.servlet.http.HttpServletRequest;import org.springframework.beans.factory.annotation.Value;import org.springframework.web.bind.annotation.*;import org.springframework.http.*;import java.util.*;
@RestController
public class AiCapabilitiesController {
    private final boolean voice,clinical;public AiCapabilitiesController(@Value("${careflow.voice.enabled:false}") boolean v,@Value("${careflow.clinical.enabled:false}") boolean c){voice=v;clinical=c;}
    @GetMapping("/api/ai/capabilities") public ResponseEntity<?> get(HttpServletRequest request){AiAccess.role(request,"DOCTOR","ADMIN");return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(Map.of("voicePrescription",voice,"clinicalEvidence",clinical,"clinicalEngine",ClinicalEngine.MODEL)));}
}
