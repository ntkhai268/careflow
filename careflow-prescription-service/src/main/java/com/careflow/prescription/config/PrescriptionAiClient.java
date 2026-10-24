package com.careflow.prescription.config;
import org.springframework.stereotype.Component;import org.springframework.beans.factory.annotation.Value;import org.springframework.boot.web.client.RestTemplateBuilder;import org.springframework.web.client.*;import org.springframework.http.*;import com.fasterxml.jackson.databind.*;import com.careflow.common.exception.BusinessException;import java.util.*;import java.time.*;
@Component
public class PrescriptionAiClient {
    private final RestTemplate http;private final String base;
    public PrescriptionAiClient(RestTemplateBuilder b,@Value("${app.ai-service.url:https://careflow-ai-service:8092}") String base){http=b.connectTimeout(Duration.ofSeconds(3)).readTimeout(Duration.ofSeconds(5)).build();this.base=base;}
    public JsonNode job(UUID id){try{var response=http.exchange(base+"/api/ai/voice-prescription/jobs/"+id,HttpMethod.GET,new HttpEntity<>(PrescriptionRequestHeaders.current()),JsonNode.class);JsonNode value=response.getBody();if(value==null||value.path("status").asInt()!=200||!value.path("data").isObject())throw new BusinessException(503,"Kết quả AI không đúng contract");return value.path("data");}catch(HttpStatusCodeException e){if(Set.of(401,403,404,409).contains(e.getStatusCode().value()))throw new BusinessException(e.getStatusCode().value(),"Không còn quyền đọc đề xuất AI");throw new BusinessException(503,"AI Service chưa sẵn sàng");}catch(RestClientException e){throw new BusinessException(503,"AI Service chưa sẵn sàng");}}
}
