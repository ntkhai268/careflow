package com.careflow.ai.voice;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static com.careflow.ai.voice.VoiceTypes.*;

public class VoiceRemoteClient implements VoiceDependencies {
    private final VoiceProperties p;
    private final ObjectMapper mapper;
    private final HttpClient http;
    public VoiceRemoteClient(VoiceProperties p,ObjectMapper mapper) {
        this.p=p;this.mapper=mapper;
        http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(p.getDependencyTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        for(String endpoint:List.of(p.getIdentityUrl(),p.getConsultationUrl(),p.getPrescriptionUrl(),p.getWorkerUrl())) {
            URI uri=URI.create(endpoint);
            boolean local=Set.of("127.0.0.1","localhost","::1").contains(uri.getHost());
            if(uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||
                    !("https".equals(uri.getScheme())||("http".equals(uri.getScheme())&&local&&p.isAllowLoopbackHttp())))
                throw new IllegalArgumentException("Voice dependency requires HTTPS or explicitly enabled loopback HTTP");
        }
        if(p.getWorkerToken().length()<32||p.getCallerKey().length()<32)throw new IllegalArgumentException("Voice service credentials are required");
    }
    public static String sha(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private JsonNode send(String base,String path,Actor actor,ObjectNode payload,boolean worker,Map<String,String> extra) {
        try {
            var request=HttpRequest.newBuilder(URI.create(base.replaceAll("/+$","")+path))
                .timeout(Duration.ofSeconds(worker?p.getInferenceTimeoutSeconds():p.getDependencyTimeoutSeconds()))
                .header("Accept","application/json").header("Content-Type","application/json");
            if(worker)request.header("Authorization","Bearer "+p.getWorkerToken());
            else {
                request.header("Authorization",actor.authorization()).header("X-Correlation-Id",actor.correlationId())
                    .header("X-Careflow-Caller",p.getCallerName()).header("X-Careflow-Caller-Key",p.getCallerKey());
            }
            extra.forEach(request::header);
            request=payload==null?request.GET():request.POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
            var response=http.send(request.build(),HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;
            try(var input=response.body()){body=input.readNBytes(4*1024*1024+1);}
            if(!worker&&(response.statusCode()==401||response.statusCode()==403))
                throw new BusinessException(response.statusCode(),"Không còn quyền dùng AI cho ca khám này");
            if(response.statusCode()==429)throw new BusinessException(429,"Dịch vụ AI đang bận");
            if(worker&&response.statusCode()==422)throw new BusinessException(422,"Audio hoặc transcript không hợp lệ");
            if(response.statusCode()!=200||body.length>4*1024*1024)throw new BusinessException(503,"Dịch vụ phụ thuộc AI chưa sẵn sàng");
            return mapper.readTree(body);
        } catch(BusinessException e){throw e;}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new BusinessException(503,"Yêu cầu AI đã bị ngắt");}
        catch(Exception e){throw new BusinessException(503,"Không thể kết nối dịch vụ phụ thuộc AI");}
    }
    private Actor session(String authorization,String correlation,Map<String,String> proof) {
        if(authorization==null||!authorization.startsWith("Bearer ")||authorization.length()>8192)
            throw new BusinessException(401,"Cần phiên đăng nhập hợp lệ");
        JsonNode principal=send(p.getIdentityUrl(),"/api/auth/session",new Actor(null,authorization,correlation),null,false,proof).path("data");
        try {
            if(!principal.path("active").isBoolean()||!principal.path("active").asBoolean()
                    ||!Instant.parse(principal.path("expiresAt").asText()).isAfter(Instant.now())
                    ||principal.path("sessionId").asText().isBlank())throw new IllegalArgumentException();
            UUID id=UUID.fromString(principal.path("userId").asText());
            if(!"DOCTOR".equals(principal.path("role").asText()))throw new BusinessException(403,"Chỉ bác sĩ được nhập toa bằng giọng nói");
            return new Actor(id,authorization,correlation);
        } catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException(401,"Phiên đăng nhập không còn hợp lệ");}
    }
    @Override public Actor authenticate(HttpServletRequest request) {
        String correlation;
        try{correlation=UUID.fromString(request.getHeader("X-Correlation-Id")).toString();}catch(Exception e){correlation=UUID.randomUUID().toString();}
        Map<String,String> proof=new HashMap<>();
        proof.put("X-Careflow-Request-Method",request.getMethod());proof.put("X-Careflow-Request-Path",request.getRequestURI());
        if(request.getHeader("DPoP")!=null)proof.put("DPoP",request.getHeader("DPoP"));
        return session(request.getHeader("Authorization"),correlation,proof);
    }
    @Override public void authorize(Actor actor,UUID consultation) {
        Actor current=session(actor.authorization(),actor.correlationId(),Map.of());
        if(!current.userId().equals(actor.userId()))throw new BusinessException(403,"Danh tính phiên đã thay đổi");
        JsonNode c=send(p.getConsultationUrl(),"/api/consultations/"+consultation,actor,null,false,Map.of()).path("data");
        if(!actor.userId().toString().equals(c.path("doctorId").asText())||!consultation.toString().equals(c.path("id").asText()))
            throw new BusinessException(403,"Bác sĩ không còn phụ trách ca khám");
        if(!Set.of("IN_PROGRESS","READY_TO_COMPLETE").contains(c.path("status").asText()))
            throw new BusinessException(409,"Ca khám không ở trạng thái cho phép nhập toa");
    }
    @Override public ObjectNode catalog(Actor actor) {
        JsonNode medicines=send(p.getPrescriptionUrl(),"/api/prescriptions/medicines",actor,null,false,Map.of()).path("data");
        if(!medicines.isArray()||medicines.isEmpty()||medicines.size()>10000)throw new BusinessException(503,"Catalog kê toa chưa sẵn sàng");
        ArrayNode clean=mapper.createArrayNode();Set<String> codes=new HashSet<>();
        for(JsonNode m:medicines) {
            String code=m.path("code").asText(),name=m.path("name").asText();
            if(code.isBlank()||code.length()>50||name.isBlank()||name.length()>200||!codes.add(code))throw new BusinessException(503,"Catalog kê toa không hợp lệ");
            // This Prescription endpoint returns active medicines only; description is deliberately ignored.
            ObjectNode row=clean.addObject().put("code",code).put("name",name).put("active",true);
            row.set("unit",m.get("unit"));
        }
        return mapper.createObjectNode().put("catalogVersion",sha(clean.toString())).set("medicines",clean);
    }
    @Override public JsonNode infer(ObjectNode request) {
        return send(p.getWorkerUrl(),"/internal/v1/voice/infer",null,request,true,Map.of());
    }
}
