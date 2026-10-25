package com.careflow.ai.clinical;
import com.careflow.common.dto.ApiResponse;import com.careflow.common.exception.BusinessException;import jakarta.servlet.http.HttpServletRequest;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.beans.factory.ObjectProvider;import org.springframework.web.bind.annotation.*;import org.springframework.http.*;import java.net.*;import java.util.*;
@RestController
@RequestMapping("/api/ai/knowledge")
public class KnowledgeController {
    private final ObjectProvider<ClinicalStore> stores;public KnowledgeController(ObjectProvider<ClinicalStore> stores){this.stores=stores;}
    private ClinicalStore store(){var s=stores.getIfAvailable();if(s==null)throw new BusinessException(503,"Kho nguồn chuyên môn chưa được cấu hình");return s;}
    public record Import(@NotBlank @Size(max=200) String title,@NotBlank @Size(max=80) String revision,@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{2,49}") String department,@NotBlank @Size(max=1000) String url,@NotBlank @Size(max=12000) String content,boolean synthetic){}
    public record Review(@NotBlank @Pattern(regexp="[a-f0-9]{64}") String expectedHash,@AssertTrue boolean contentReviewed){}
    private ResponseEntity<?> ok(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(value));}
    @GetMapping public ResponseEntity<?> list(HttpServletRequest request){AiAccess.role(request,"ADMIN","DOCTOR");return ok(store().sources());}
    @PostMapping public ResponseEntity<?> create(@Valid @RequestBody Import input,HttpServletRequest request){AiAccess.role(request,"ADMIN");URI u;try{u=URI.create(input.url());}catch(Exception e){throw new BusinessException(400,"URL nguồn không hợp lệ");}if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null)throw new BusinessException(400,"Nguồn phải có URL HTTPS có thể kiểm tra");return ok(store().create(input.title(),input.revision(),input.department(),input.url(),input.content(),input.synthetic(),AiAccess.principal(request).userId()));}
    @PostMapping("/{id}/review") public ResponseEntity<?> review(@PathVariable UUID id,@Valid @RequestBody Review input,HttpServletRequest request){AiAccess.role(request,"DOCTOR");return ok(store().review(id,AiAccess.principal(request).userId(),input.expectedHash()));}
    @PostMapping("/{id}/retire") public ResponseEntity<?> retire(@PathVariable UUID id,HttpServletRequest request){AiAccess.role(request,"ADMIN");return ok(store().retire(id));}
}
