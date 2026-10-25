package com.careflow.common.security;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
@AutoConfiguration(after=InternalTlsAutoConfiguration.class)
public class PatientOwnershipAutoConfiguration {
    @Bean @ConditionalOnMissingBean
    public PatientOwnershipClient patientOwnershipClient(@Value("${security.patient-owner.url:}") String url,
            @Value("${security.caller.name:${spring.application.name:}}") String caller,@Value("${security.caller.key:}") String key,
            ObjectMapper json,ObjectProvider<InternalTlsMaterial> tls){
        var material=tls.getIfAvailable();return new PatientOwnershipClient(url,caller,key,json,material==null?null:material.context());
    }
}
