package com.careflow.ai.clinical;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/knowledge")
public class KnowledgeController {
    private final ObjectProvider<ClinicalStore> stores;

    public KnowledgeController(ObjectProvider<ClinicalStore> stores) {
        this.stores = stores;
    }

    private ClinicalStore store() {
        var s = stores.getIfAvailable();
        if (s == null) {
            throw new BusinessException(503, "Kho nguồn chuyên môn chưa được cấu hình");
        }
        return s;
    }

    public record Import(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 80) String revision,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,49}") String department,
            @NotBlank @Size(max = 1000) String url,
            @NotBlank @Size(max = 12000) String content,
            boolean synthetic) {
    }

    public record Review(
            @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String expectedHash,
            @AssertTrue boolean contentReviewed) {
    }

    private ResponseEntity<?> ok(Object value) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(value));
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request) {
        AiAccess.role(request, "ADMIN", "DOCTOR");
        return ok(store().sources());
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody Import input, HttpServletRequest request) {
        AiAccess.role(request, "ADMIN");
        URI uri;
        try {
            uri = URI.create(input.url());
        } catch (Exception e) {
            throw new BusinessException(400, "URL nguồn không hợp lệ");
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new BusinessException(400, "Nguồn phải có URL HTTPS có thể kiểm tra");
        }
        return ok(store().create(
                input.title(),
                input.revision(),
                input.department(),
                input.url(),
                input.content(),
                input.synthetic(),
                AiAccess.principal(request).userId()));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<?> review(@PathVariable UUID id, @Valid @RequestBody Review input, HttpServletRequest request) {
        AiAccess.role(request, "DOCTOR");
        return ok(store().review(id, AiAccess.principal(request).userId(), input.expectedHash()));
    }

    @PostMapping("/{id}/retire")
    public ResponseEntity<?> retire(@PathVariable UUID id, HttpServletRequest request) {
        AiAccess.role(request, "ADMIN");
        return ok(store().retire(id));
    }
}
