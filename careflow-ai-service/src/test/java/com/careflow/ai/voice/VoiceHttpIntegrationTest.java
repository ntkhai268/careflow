package com.careflow.ai.voice;

import com.careflow.ai.AiApplication;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes=AiApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"careflow.voice.enabled=true","security.ai.demo-enabled=true","eureka.client.enabled=false","spring.cloud.discovery.enabled=false"})
class VoiceHttpIntegrationTest {
    static final UUID DOCTOR=UUID.randomUUID(),CONSULTATION=UUID.randomUUID();
    static final AtomicReference<UUID> assigned=new AtomicReference<>(DOCTOR);
    static final ObjectMapper JSON=new ObjectMapper();
    static final HttpServer fixture=startFixture();
    @Autowired TestRestTemplate rest;
    static HttpServer startFixture() {
        try {
            HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",exchange->{
                String path=exchange.getRequestURI().getPath();int status=200;Object body;
                if(path.equals("/api/auth/session")) {
                    if(!"Bearer valid".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {status=401;body=Map.of("status",401);}
                    else body=Map.of("status",200,"data",Map.of("active",true,"userId",DOCTOR,"role","DOCTOR","sessionId",UUID.randomUUID(),"expiresAt",Instant.now().plusSeconds(900).toString()));
                } else if(path.startsWith("/api/consultations/")) body=Map.of("status",200,"data",Map.of("id",CONSULTATION,"doctorId",assigned.get(),"status","IN_PROGRESS"));
                else if(path.equals("/api/prescriptions/medicines")) body=Map.of("status",200,"data",List.of(Map.of("code","PARA","name","Paracetamol","unit","viên","description","ignored")));
                else if(path.equals("/internal/v1/voice/infer")) {
                    if(!("Bearer "+"w".repeat(32)).equals(exchange.getRequestHeaders().getFirst("Authorization")))throw new AssertionError("worker service auth missing");
                    assertThat(exchange.getRequestHeaders().getFirst("Authorization")).doesNotContain("valid");
                    JsonNode input=JSON.readTree(exchange.getRequestBody());String text=input.path("transcript").asText();int start=text.indexOf("Paracetamol");
                    var result=JSON.createObjectNode().put("schemaVersion",VoiceTypes.SCHEMA).put("transcript",text)
                        .put("transcriptRevision",input.path("transcriptRevision").asInt()).put("transcriptHash",VoiceRemoteClient.sha(text)).put("catalogVersion",input.path("catalogVersion").asText());
                    var item=result.putArray("items").addObject().put("spokenName","Paracetamol").put("medicineCode","PARA");
                    item.putArray("candidates").addObject().put("medicineCode","PARA");
                    item.putObject("evidence").putObject("spokenName").put("text","Paracetamol").put("start",start).put("end",start+11)
                        .put("offsetUnit","unicode_codepoint").put("transcriptRevision",input.path("transcriptRevision").asInt()).put("transcriptHash",VoiceRemoteClient.sha(text));
                    var proposal=result.putObject("prescriptionDraft").put("canSaveWithoutCorrection",false);proposal.putArray("items");proposal.putArray("blockers");body=result;
                } else {status=404;body=Map.of("status",404);}
                byte[] bytes=JSON.writeValueAsBytes(body);exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });server.start();return server;
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        String base="http://127.0.0.1:"+fixture.getAddress().getPort();
        r.add("app.identity-service.url",()->base);r.add("security.caller.name",()->"ai-service");
        r.add("security.caller.key",()->"c".repeat(32));
        for(String key:List.of("identity-url","consultation-url","prescription-url","worker-url"))r.add("careflow.voice."+key,()->base);
        r.add("careflow.voice.jdbc-url",()->"jdbc:h2:mem:voice-http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        r.add("careflow.voice.db-user",()->"sa");r.add("careflow.voice.db-password",()->"");
        r.add("careflow.voice.worker-token",()->"w".repeat(32));r.add("careflow.voice.caller-key",()->"c".repeat(32));r.add("careflow.voice.allow-loopback-http",()->true);
    }
    HttpHeaders headers(String key) {HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);h.setBearerAuth("valid");h.set("Idempotency-Key",key);h.set("X-User-Id",UUID.randomUUID().toString());return h;}
    JsonNode waitForResult(String id,HttpHeaders headers) throws Exception {
        JsonNode node=null;
        for(int i=0;i<300;i++) {
            var response=rest.exchange("/api/ai/voice-prescription/jobs/"+id,HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class);
            assertThat(response.getStatusCode().value()).isEqualTo(200);node=response.getBody().path("data");
            if(!Set.of("QUEUED","RUNNING").contains(node.path("state").asText()))return node;
            Thread.sleep(10);
        }throw new AssertionError("Job timed out: "+node);
    }
    @Test void authenticatedJobReplayRevisionAndHandoverOverHttp() throws Exception {
        assigned.set(DOCTOR);HttpHeaders headers=headers("http-first");
        var body=Map.of("consultationId",CONSULTATION,"transcript","Cho Paracetamol.");
        var response=rest.postForEntity("/api/ai/voice-prescription/jobs",new HttpEntity<>(body,headers),JsonNode.class);
        assertThat(response.getStatusCode().value()).as("Job response: %s",response.getBody()).isEqualTo(202);String id=response.getBody().path("data").path("jobId").asText();
        JsonNode done=waitForResult(id,headers);assertThat(done.path("state").asText()).isEqualTo("SUCCEEDED");
        assertThat(done.path("result").path("items").get(0).path("medicineCode").asText()).isEqualTo("PARA");
        var replay=rest.postForEntity("/api/ai/voice-prescription/jobs",new HttpEntity<>(body,headers),JsonNode.class);
        assertThat(replay.getBody().path("data").path("jobId").asText()).isEqualTo(id);
        var revision=Map.of("expectedVersion",done.path("version").asLong(),"transcript","Cho Paracetamol, số lượng sáu viên.","append",false);
        var revised=rest.postForEntity("/api/ai/voice-prescription/jobs/"+id+"/revisions",new HttpEntity<>(revision,headers),JsonNode.class);
        assertThat(revised.getStatusCode().value()).isEqualTo(202);JsonNode finalResult=waitForResult(id,headers);
        assertThat(finalResult.path("transcriptRevision").asInt()).isEqualTo(2);
        var stale=rest.postForEntity("/api/ai/voice-prescription/jobs/"+id+"/revisions",new HttpEntity<>(revision,headers),JsonNode.class);
        assertThat(stale.getStatusCode().value()).isEqualTo(409);
        assigned.set(UUID.randomUUID());var forbidden=rest.exchange("/api/ai/voice-prescription/jobs/"+id,HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class);
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);assigned.set(DOCTOR);
    }
    @Test void spoofedHeadersWithoutBearerAndMixedInputsAreRejected() {
        HttpHeaders h=headers("bad");h.remove("Authorization");
        var body=Map.of("consultationId",CONSULTATION,"transcript","Cho Paracetamol.");
        assertThat(rest.postForEntity("/api/ai/voice-prescription/jobs",new HttpEntity<>(body,h),JsonNode.class).getStatusCode().value()).isEqualTo(401);
        var mixed=Map.of("consultationId",CONSULTATION,"transcript","Cho Paracetamol.","audioBase64","AAAA");
        assertThat(rest.postForEntity("/api/ai/voice-prescription/jobs",new HttpEntity<>(mixed,headers("mixed")),JsonNode.class).getStatusCode().value()).isEqualTo(400);
    }
    @AfterAll static void close(){fixture.stop(0);}
}
