package com.careflow.notification.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.common.security.InternalTlsMaterial;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Component
public class PushSessionClient {
    private final HttpClient client;private final URI endpoint;private final ObjectMapper json;private final String caller,key;
    public PushSessionClient(@Value("${app.identity-service.url:http://careflow-identity-service:8081}") String url,@Value("${security.caller.name}") String caller,@Value("${security.caller.key}") String key,ObjectMapper json,ObjectProvider<InternalTlsMaterial> tls){
        this.endpoint=URI.create(url.replaceAll("/+$","")+"/internal/notifications/session-status");this.json=json;this.caller=caller;this.key=key;
        var builder=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER);var material=tls.getIfAvailable();if(material!=null)builder.sslContext(material.context());client=builder.build();
    }
    public boolean active(UUID user,UUID session){
        if(user==null || session==null)return false;
        try{
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3)).header("Content-Type","application/json").header("X-Careflow-Caller",caller).header("X-Careflow-Caller-Key",key).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("userId",user,"sessionId",session)))).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalStateException();
            var body=json.readTree(response.body());if(body.path("status").asInt()!=200 || !body.path("data").path("active").isBoolean())throw new IllegalStateException();return body.path("data").path("active").asBoolean();
        }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new BusinessException(503,"Chưa xác minh được quyền nhận thông báo.");}
    }
}
