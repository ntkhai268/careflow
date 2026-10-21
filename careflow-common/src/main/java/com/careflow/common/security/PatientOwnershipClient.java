package com.careflow.common.security;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;

/** No cache: historical recipient snapshots cannot grant access after revocation. */
public class PatientOwnershipClient {
    private final String url,caller,key;
    private final ObjectMapper json;
    private final HttpClient client;
    public PatientOwnershipClient(String url,String caller,String key,ObjectMapper json,javax.net.ssl.SSLContext tls){
        this.url=url.replaceAll("/+$","");this.caller=caller;this.key=key;this.json=json;
        var builder=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER);
        if(tls!=null)builder.sslContext(tls);client=builder.build();
    }
    public UUID owner(UUID patientId){
        if(patientId==null || !url.startsWith("https://") || key.length()<32)throw unavailable();
        try{
            var request=HttpRequest.newBuilder(URI.create(url+"/internal/patients/"+patientId+"/owner"))
                    .timeout(Duration.ofSeconds(3)).header("X-Careflow-Caller",caller).header("X-Careflow-Caller-Key",key).GET().build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw unavailable();
            var envelope=json.readTree(response.body());var data=envelope.path("data");
            if(envelope.path("status").asInt()!=200 || !patientId.toString().equals(data.path("patientId").asText()) || !data.has("userId"))throw unavailable();
            return data.get("userId").isNull()?null:UUID.fromString(data.get("userId").asText());
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable();}
        catch(java.io.IOException | IllegalArgumentException e){throw unavailable();}
    }
    public void require(UUID patientId,UUID actor){
        if(actor==null || !actor.equals(owner(patientId)))throw new BusinessException(403,"Tài khoản không còn quyền truy cập hồ sơ này.");
    }
    private BusinessException unavailable(){return new BusinessException(503,"Không thể xác minh quyền hồ sơ hiện tại.");}
}
