package com.careflow.ai.clinical;

import com.careflow.common.exception.BusinessException;
import com.careflow.common.security.ActiveSessionClient;
import com.careflow.common.security.ActiveSessionFilter;
import com.careflow.common.security.InternalTlsMaterial;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class AiAccess {
    private static final Set<String> ALLOWED_CONSULTATION_STATUSES = Set.of(
            "IN_PROGRESS", "AWAITING_CLS", "AWAITING_REVIEW", "READY_TO_COMPLETE", "COMPLETED");
    private static final List<String> CONSULTATION_FACT_FIELDS = List.of(
            "symptoms", "clinicalNotes", "diagnosis", "icd10Code",
            "temperature", "bloodPressure", "heartRate", "spo2", "height", "weight");
    private static final List<String> PATIENT_FACT_FIELDS = List.of("allergyNotes", "medicalHistory");

    private final HttpClient http;
    private final ObjectMapper json;
    private final String consultation;
    private final String patient;
    private final String caller;
    private final String key;

    public AiAccess(ObjectMapper json,
                    ObjectProvider<InternalTlsMaterial> tls,
                    @Value("${app.consultation-service.url:https://careflow-consultation-service:8086}") String c,
                    @Value("${app.patient-service.url:https://careflow-patient-service:8082}") String p,
                    @Value("${security.caller.name:ai-service}") String caller,
                    @Value("${security.caller.key:}") String key) {
        this.json = json;
        this.consultation = c;
        this.patient = p;
        this.caller = caller;
        this.key = key;
        var builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER);
        if (tls.getIfAvailable() != null) {
            builder.sslContext(tls.getIfAvailable().context());
        }
        this.http = builder.build();
    }

    public record Actor(UUID id, String authorization) {
    }

    public record Context(String department, ObjectNode facts) {
    }

    public static ActiveSessionClient.Principal principal(HttpServletRequest request) {
        Object value = request.getAttribute(ActiveSessionFilter.VERIFIED_PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof ActiveSessionClient.Principal p)
                || !Boolean.TRUE.equals(p.active())
                || !p.expiresAt().isAfter(Instant.now())) {
            throw new BusinessException(401, "Cần phiên đăng nhập hợp lệ");
        }
        return p;
    }

    public Actor doctor(HttpServletRequest request) {
        var p = principal(request);
        if (!"DOCTOR".equals(p.role())) {
            throw new BusinessException(403, "Chỉ bác sĩ được dùng trợ lý lâm sàng");
        }
        return new Actor(p.userId(), request.getHeader("Authorization"));
    }

    public static void role(HttpServletRequest request, String... roles) {
        if (!Set.of(roles).contains(principal(request).role())) {
            throw new BusinessException(403, "Không có quyền thực hiện thao tác AI");
        }
    }

    private JsonNode get(String base, String path, Actor actor) {
        try {
            var req = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", actor.authorization())
                    .header("X-Careflow-Caller", caller)
                    .header("X-Careflow-Caller-Key", key)
                    .GET()
                    .build();
            var response = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (Set.of(401, 403, 404).contains(response.statusCode())) {
                throw new BusinessException(response.statusCode(), "Quyền ca khám không còn hợp lệ");
            }
            if (response.statusCode() != 200) {
                throw new BusinessException(503, "Chưa đọc được ngữ cảnh ca khám");
            }
            var data = json.readTree(response.body()).path("data");
            if (!data.isObject()) {
                throw new IllegalStateException();
            }
            return data;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(503, "Nguồn ngữ cảnh ca khám chưa sẵn sàng");
        }
    }

    public Context context(UUID id, Actor actor) {
        JsonNode c = get(consultation, "/api/consultations/" + id, actor);
        if (!id.toString().equals(c.path("id").asText()) || !actor.id().toString().equals(c.path("doctorId").asText())) {
            throw new BusinessException(403, "Bác sĩ không còn phụ trách ca khám");
        }
        if (!ALLOWED_CONSULTATION_STATUSES.contains(c.path("status").asText())) {
            throw new BusinessException(409, "Trạng thái ca khám không cho phép dùng trợ lý");
        }
        UUID patientId;
        try {
            patientId = UUID.fromString(c.path("patientId").asText());
        } catch (Exception e) {
            throw new BusinessException(503, "Ca khám thiếu liên kết hồ sơ hợp lệ");
        }
        JsonNode p = get(patient, "/api/patients/" + patientId, actor);
        if (!patientId.toString().equals(p.path("id").asText())) {
            throw new BusinessException(503, "Hồ sơ không khớp với ca khám");
        }

        var facts = json.createObjectNode();
        for (String field : CONSULTATION_FACT_FIELDS) {
            if (c.hasNonNull(field)) {
                facts.set(field, c.get(field));
            }
        }
        for (String field : PATIENT_FACT_FIELDS) {
            if (p.hasNonNull(field)) {
                facts.set(field, p.get(field));
            }
        }
        if (p.path("dateOfBirth").isTextual()) {
            try {
                int age = Period.between(
                        LocalDate.parse(p.path("dateOfBirth").asText()),
                        LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))).getYears();
                if (age >= 0 && age <= 130) {
                    facts.put("age", age);
                }
            } catch (Exception ignored) {
            }
        }
        String department = c.path("departmentCode").asText();
        if (department.isBlank()) {
            throw new BusinessException(503, "Ca khám thiếu thông tin khoa");
        }
        return new Context(department, facts);
    }
}
