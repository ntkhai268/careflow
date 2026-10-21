package com.careflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

import java.time.Duration;

@AutoConfiguration(after = JacksonAutoConfiguration.class)
@Import(ActiveSessionAutoConfiguration.ServletSessionConfiguration.class)
public class ActiveSessionAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public ActiveSessionClient activeSessionClient(
            @Value("${app.identity-service.url:http://careflow-identity-service:8081}") String identityUrl,
            @Value("${security.caller.name:${spring.application.name:}}") String callerName,
            @Value("${security.caller.key:}") String callerKey,
            org.springframework.beans.factory.ObjectProvider<InternalTlsMaterial> tls,
            ObjectMapper mapper) {
        if (callerName.isBlank() || callerKey.length() < 32) throw new IllegalArgumentException("Configure independent SECURITY_CALLER_NAME and SECURITY_CALLER_KEY");
        InternalTlsMaterial material=tls.getIfAvailable();
        return new ActiveSessionClient(identityUrl, mapper, Duration.ofSeconds(3), callerName, callerKey, material==null ? null : material.context());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingClass("com.careflow.identity.IdentityApplication")
    public static class ServletSessionConfiguration {
        @Bean
        public FilterRegistrationBean<ActiveSessionFilter> activeSessionFilter(ActiveSessionClient client, ObjectMapper mapper, @Value("${security.audit.allow-loopback-http:false}") boolean allowLoopbackHttp) {
            var registration = new FilterRegistrationBean<>(new ActiveSessionFilter(client, mapper, allowLoopbackHttp));
            registration.addUrlPatterns("/api/*");
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
            registration.setName("careflowActiveSessionFilter");
            return registration;
        }
    }
}
