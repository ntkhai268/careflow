package com.careflow.common.security;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
@AutoConfiguration(before={ActiveSessionAutoConfiguration.class,ServiceCallerAutoConfiguration.class})
@ConditionalOnProperty(name="security.internal.tls.enabled",havingValue="true")
public class InternalTlsAutoConfiguration {
    @Bean public InternalTlsMaterial internalTlsMaterial(
        @Value("${security.internal.tls.key-store}") String keys,@Value("${security.internal.tls.key-store-password}") String keyPassword,
        @Value("${security.internal.tls.trust-store}") String trust,@Value("${security.internal.tls.trust-store-password}") String trustPassword) {
        return new InternalTlsMaterial(keys,keyPassword,trust,trustPassword);
    }
}
