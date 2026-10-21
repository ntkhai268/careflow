package com.careflow.common.security;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import java.net.URI;
import java.util.Set;

@AutoConfiguration
@ConditionalOnMissingClass("com.careflow.identity.IdentityApplication")
public class ServiceCallerAutoConfiguration {
    private final String name, key;
    private final Set<String> allowedHosts;
    public ServiceCallerAutoConfiguration(@Value("${security.caller.name:${spring.application.name:}}") String name,
             @Value("${security.caller.key:}") String key,
             @Value("${security.caller.allowed-hosts:careflow-api-gateway,api-gateway,careflow-identity-service,identity-service,careflow-patient-service,patient-service,careflow-appointment-service,appointment-service,careflow-hospital-directory-service,hospital-directory-service,careflow-consultation-service,consultation-service,careflow-lab-service,lab-service,careflow-prescription-service,prescription-service,careflow-queue-service,queue-service,careflow-emr-service,emr-service}") String hosts, org.springframework.beans.factory.ObjectProvider<InternalTlsMaterial> tls) {
        tls.getIfAvailable();
        this.name = name; this.key = key; this.allowedHosts = Set.of(hosts.split(","));
    }
    @Bean
    public RequestInterceptor careflowCallerFeign() {
        return request -> {
            URI target = URI.create(request.feignTarget().url());
            if (!allowedHosts.contains(target.getHost())) return;
            request.removeHeader("X-Careflow-Caller"); request.removeHeader("X-Careflow-Caller-Key");
            request.header("X-Careflow-Caller",name); request.header("X-Careflow-Caller-Key",key);
        };
    }
    @Bean
    public RestTemplateCustomizer careflowCallerRestTemplate() {
        return template -> template.getInterceptors().add((request,body,execution) -> {
            if (allowedHosts.contains(request.getURI().getHost())) {
                request.getHeaders().set("X-Careflow-Caller",name);
                request.getHeaders().set("X-Careflow-Caller-Key",key);
            }
            return execution.execute(request,body);
        });
    }
}
