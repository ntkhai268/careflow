package com.careflow.eureka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.security.user.name=audit-user",
                "spring.security.user.password=audit-password",
                "eureka.instance.hostname=localhost",
                "eureka.client.service-url.defaultZone=http://audit-user:audit-password@localhost:8761/eureka/"
        })
class EurekaServerIntegrationTest {

    private static final String USERNAME = "audit-user";
    private static final String PASSWORD = "audit-password";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthIsPublicButDashboardAndRegistryRequireAuthentication() {
        assertThat(restTemplate.getForEntity(url("/actuator/health"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.getForEntity(url("/"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.getForEntity(url("/eureka/apps"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        TestRestTemplate authenticated = restTemplate.withBasicAuth(USERNAME, PASSWORD);
        assertThat(authenticated.getForEntity(url("/"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(authenticated.getForEntity(url("/eureka/apps"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void authenticatedClientCanRegisterReadRenewAndCancelLease() {
        TestRestTemplate authenticated = restTemplate.withBasicAuth(USERNAME, PASSWORD);
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(USERNAME, PASSWORD);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        String instance = """
                {
                  "instance": {
                    "instanceId": "audit-service:19090",
                    "hostName": "127.0.0.1",
                    "app": "AUDIT-SERVICE",
                    "ipAddr": "127.0.0.1",
                    "status": "UP",
                    "overriddenStatus": "UNKNOWN",
                    "port": {"$": 19090, "@enabled": "true"},
                    "securePort": {"$": 443, "@enabled": "false"},
                    "countryId": 1,
                    "dataCenterInfo": {
                      "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
                      "name": "MyOwn"
                    },
                    "leaseInfo": {"renewalIntervalInSecs": 30, "durationInSecs": 90},
                    "homePageUrl": "http://127.0.0.1:19090/",
                    "statusPageUrl": "http://127.0.0.1:19090/actuator/info",
                    "healthCheckUrl": "http://127.0.0.1:19090/actuator/health",
                    "vipAddress": "audit-service",
                    "secureVipAddress": "audit-service",
                    "metadata": {"audit": "true"}
                  }
                }
                """;

        ResponseEntity<Void> registration = authenticated.exchange(
                url("/eureka/apps/AUDIT-SERVICE"),
                HttpMethod.POST,
                new HttpEntity<>(instance, headers),
                Void.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> registry = authenticated.getForEntity(
                url("/eureka/apps/AUDIT-SERVICE"), String.class);
        assertThat(registry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registry.getBody()).contains("audit-service:19090", "AUDIT-SERVICE");

        ResponseEntity<Void> heartbeat = authenticated.exchange(
                url("/eureka/apps/AUDIT-SERVICE/audit-service:19090"),
                HttpMethod.PUT,
                new HttpEntity<>(headers),
                Void.class);
        assertThat(heartbeat.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Void> cancellation = authenticated.exchange(
                url("/eureka/apps/AUDIT-SERVICE/audit-service:19090"),
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                Void.class);
        assertThat(cancellation.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
