package com.careflow.ai.voice;

import com.careflow.ai.AiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes=AiApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"server.ssl.enabled=false","security.internal.tls.enabled=false","careflow.voice.enabled=false","eureka.client.enabled=false","spring.cloud.discovery.enabled=false","security.caller.name=ai-service",
                "security.caller.key=disabled-voice-test-only-caller-key-32"})
@ActiveProfiles("secure")
class VoiceDisabledApplicationTest {
    @Autowired TestRestTemplate rest;
    @Autowired ApplicationContext context;
    @Test void disabledFeatureDoesNotCreateOrConnectADataSource() {
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(context.getBeansOfType(VoiceJobService.class)).isEmpty();
        assertThat(rest.getForEntity("/api/ai/voice-prescription/jobs/unused",String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    @Test void legacyClinicalRoutesRemainClosedInSecureProfile() {
        assertThat(rest.postForEntity("/api/ai/clinical-chat","{}",String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.postForEntity("/api/ai/voice-to-clinical-fields","",String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
