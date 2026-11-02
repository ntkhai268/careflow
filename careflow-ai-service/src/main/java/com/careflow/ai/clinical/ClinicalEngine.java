package com.careflow.ai.clinical;

import com.careflow.ai.voice.VoiceRemoteClient;
import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Local extractive assistance. Source text remains data and is never interpreted as instructions. */
public class ClinicalEngine {
    public static final String MODEL = "careflow-local-evidence-v1";
    private static final Set<String> STOP_WORDS = Set.of(
            "cac", "cho", "cua", "voi", "benh", "nhan", "huong",
            "dan", "phac", "dieu", "tri", "goi", "theo", "thong", "tin", "thieu");
    private static final List<String> REQUIRED_CONTEXT_FIELDS = List.of(
            "symptoms", "bloodPressure", "temperature", "allergyNotes");

    private final ClinicalStore store;
    private final AiAccess access;
    private final ObjectMapper json;
    private final boolean synthetic;

    public ClinicalEngine(ClinicalStore store, AiAccess access, ObjectMapper json, boolean synthetic) {
        this.store = store;
        this.access = access;
        this.json = json;
        this.synthetic = synthetic;
    }

    public static Set<String> tokens(String text) {
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd');
        return Arrays.stream(normalized.split("[^a-z0-9]+"))
                .filter(token -> token.length() > 2 && !STOP_WORDS.contains(token))
                .limit(500)
                .collect(Collectors.toSet());
    }

    public JsonNode ask(UUID consultation, String question, UUID parent, HttpServletRequest request) {
        if (question == null || question.isBlank() || question.length() > 2000) {
            throw new BusinessException(400, "Câu hỏi cần có1–2000ký tự");
        }
        var actor = access.doctor(request);
        var context = access.context(consultation, actor);
        if (parent != null) {
            store.read(parent, actor.id(), consultation);
        }

        var eligible = store.eligibleSources(context.department(), synthetic).stream()
                .filter(source -> "APPROVED".equals(source.status())
                        && source.department().equals(context.department())
                        && (synthetic || !source.synthetic()))
                .toList();
        if (eligible.isEmpty()) {
            throw new BusinessException(503, "Chưa có nguồn chuyên môn được duyệt cho khoa này");
        }

        Set<String> queryTokens = tokens(question);
        Set<String> factTokens = tokens(
                context.facts().path("symptoms").asText() + " " + context.facts().path("diagnosis").asText());

        record Hit(ClinicalStore.Source source, double score) {
        }

        var ranked = eligible.stream()
                .map(source -> {
                    var terms = tokens(source.title() + " " + source.content());
                    long explicit = queryTokens.stream().filter(terms::contains).count();
                    long contextual = factTokens.stream().filter(terms::contains).count();
                    return new Hit(source, explicit * 2.0 + contextual * 0.25);
                })
                .filter(hit -> hit.score() > 0)
                .sorted(Comparator.comparingDouble(Hit::score).reversed().thenComparing(hit -> hit.source().id()))
                .limit(3)
                .toList();

        UUID id = UUID.randomUUID();
        var out = json.createObjectNode();
        out.put("interactionId", id.toString())
                .put("consultationId", consultation.toString())
                .put("mode", ranked.isEmpty() ? "INSUFFICIENT_EVIDENCE" : "LOCAL_EVIDENCE_RETRIEVAL")
                .put("summary", ranked.isEmpty()
                        ? "Chưa tìm thấy căn cứ phù hợp trong các nguồn đã duyệt. Hãy bổ sung thông tin hoặc tra cứu chuyên môn."
                        : "Các đoạn hướng dẫn đã duyệt dưới đây phù hợp từ khóa và ngữ cảnh. Đây là trích dẫn để bác sĩ đối chiếu, không phải kết luận tự động.")
                .put("generatedAt", Instant.now().toString())
                .put("disclaimer", "Bác sĩ quyết định chuyên môn; hệ thống không tự ghi toa hoặc chỉ định.");

        var citations = out.putArray("citations");
        var suggestions = out.putArray("suggestions");
        var warnings = out.putArray("warnings");
        var missing = out.putArray("missingInformation");

        for (String field : REQUIRED_CONTEXT_FIELDS) {
            if (!context.facts().hasNonNull(field) || context.facts().path(field).asText().isBlank()) {
                missing.add("Thiếu dữ liệu: " + field);
            }
        }

        for (var hit : ranked) {
            var source = hit.source();
            citations.addObject()
                    .put("sourceId", source.id().toString())
                    .put("title", source.title())
                    .put("revision", source.revision())
                    .put("url", source.url())
                    .put("contentHash", source.hash())
                    .put("reviewedAt", source.reviewedAt().toString())
                    .put("quote", source.content())
                    .put("synthetic", source.synthetic());
            suggestions.addObject()
                    .put("type", "TREATMENT_REFERRAL")
                    .put("text", source.title() + " — phiên bản " + source.revision());
            if (source.synthetic()) {
                warnings.add("NGUỒN TỔNG HỢP KIỂM THỬ — không dùng quyết định chuyên môn.");
            }
        }

        out.putObject("model")
                .put("provider", "SELF_HOSTED")
                .put("name", MODEL)
                .put("version", "1.0");
        out.put("contextHash", VoiceRemoteClient.sha(context.facts().toString()));

        access.context(consultation, actor);
        var envelope = json.createObjectNode().put("question", question);
        envelope.set("response", out);
        store.save(id, actor.id(), consultation, parent, out.path("contextHash").asText(), envelope, MODEL);
        return out;
    }

    public JsonNode read(UUID id, HttpServletRequest request) {
        var actor = access.doctor(request);
        UUID consultation = store.consultation(id, actor.id());
        access.context(consultation, actor);
        return store.read(id, actor.id(), consultation).path("response");
    }

    public List<JsonNode> history(UUID consultation, HttpServletRequest request) {
        var actor = access.doctor(request);
        access.context(consultation, actor);
        return store.history(actor.id(), consultation).stream()
                .map(id -> store.read(id, actor.id(), consultation))
                .toList();
    }
}
