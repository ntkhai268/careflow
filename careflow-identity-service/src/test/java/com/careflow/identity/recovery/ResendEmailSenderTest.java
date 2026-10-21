package com.careflow.identity.recovery;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class ResendEmailSenderTest {
    HttpServer server; ResendEmailSender sender; ObjectMapper json=new ObjectMapper();
    AtomicReference<JsonNode> body=new AtomicReference<>();AtomicReference<String> idempotency=new AtomicReference<>();
    int status=200;String response="{\"id\":\"email-fixture\"}";
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/emails",exchange->{
            body.set(json.readTree(exchange.getRequestBody()));idempotency.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        sender=new ResendEmailSender("fixture-only-key","CareFlow <careflow@supersignal-resend.id.vn>",HttpClient.newHttpClient(),
            URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/emails"),json);
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void codeEmailHasCareFlowHtmlBrandWithoutAnyAttachments() {
        var id=UUID.randomUUID();sender.sendCode("owned@example.com","012345",id);
        var message=body.get();
        assertThat(message.path("from").asText()).isEqualTo("CareFlow <careflow@supersignal-resend.id.vn>");
        assertThat(message.path("html").asText()).contains("012345","CareFlow","5 phút").doesNotContain("{{code}}","cid:","<img");
        assertThat(message.path("text").asText()).contains("012345","một lần");
        assertThat(message.path("subject").asText()).doesNotContain("012345");
        assertThat(message.has("attachments")).isFalse();
        assertThat(idempotency.get()).isEqualTo("recovery-code-"+id);
    }
    @Test void changedNotificationContainsNoPasswordOrCode() {
        sender.sendChanged("owned@example.com",UUID.randomUUID());
        assertThat(body.get().path("html").asText()).contains("đã được đặt lại");
        assertThat(body.get().toString()).doesNotContain("newPassword","{{code}}");
    }
    @Test void providerErrorsDoNotExposeProviderBodyOrKey() {
        status=403;response="{\"message\":\"private-provider-details fixture-only-key\"}";
        assertThatThrownBy(()->sender.sendCode("owned@example.com","123456",UUID.randomUUID()))
            .isInstanceOf(BusinessException.class).hasMessageNotContaining("private-provider").hasMessageNotContaining("fixture-only-key");
    }
    @Test void successStatusWithoutProviderIdIsNotAccepted() {
        response="{}";
        assertThatThrownBy(()->sender.sendCode("owned@example.com","123456",UUID.randomUUID())).isInstanceOf(BusinessException.class);
    }
    @Test void signupEmailHasItsOwnPurposeAndNoImageAttachments() {
        var id=UUID.randomUUID();
        assertThat(sender.sendTracked("SIGNUP","owned@example.test","012345",id)).isEqualTo("email-fixture");
        var message=body.get();
        assertThat(message.path("html").asText()).contains("Xác minh email đăng ký","012345","5 phút").doesNotContain("cid:","<img","{{code}}");
        assertThat(message.has("attachments")).isFalse();
        assertThat(idempotency.get()).isEqualTo("signup-code-"+id);
    }
    @Test void staffInviteRequiresSeparateActivationAndShowsCorrectExpiry() {
        var id=UUID.randomUUID();String grant=id+"."+"x".repeat(43);
        sender.sendTracked("STAFF_INVITE","owned@example.test",grant,id);
        var message=body.get();
        assertThat(message.path("html").asText()).contains(grant,"15 phút","mã kích hoạt").doesNotContain("115 phút","cid:","<img");
        assertThat(message.has("attachments")).isFalse();
        assertThat(idempotency.get()).isEqualTo("staff-passkey-"+id);
    }

}
