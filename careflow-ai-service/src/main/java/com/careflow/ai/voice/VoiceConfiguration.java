package com.careflow.ai.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.*;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(VoiceProperties.class)
public class VoiceConfiguration {
    @Bean public FilterRegistrationBean<VoiceRouteFilter> voiceRouteGate(VoiceProperties properties,Environment env,ObjectMapper mapper) {
        var bean=new FilterRegistrationBean<>(new VoiceRouteFilter(properties,env,mapper));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE+30);return bean;
    }
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name="careflow.voice.enabled",havingValue="true")
    static class Enabled {
        @Bean(destroyMethod="close") HikariDataSource voiceDataSource(VoiceProperties p) {
            if(p.getQueueCapacity()<1||p.getQueueCapacity()>64||p.getPerDoctorCapacity()<1||p.getTtlSeconds()<60
                    ||p.getInferenceTimeoutSeconds()<1||p.getInferenceTimeoutSeconds()>=p.getTtlSeconds())throw new IllegalArgumentException("Invalid voice limits");
            HikariConfig config=new HikariConfig();config.setJdbcUrl(p.getJdbcUrl());config.setUsername(p.getDbUser());config.setPassword(p.getDbPassword());
            config.setMaximumPoolSize(4);config.setConnectionTimeout(5000);config.setPoolName("voice-jobs");
            HikariDataSource source=new HikariDataSource(config);
            try{Flyway.configure().dataSource(source).locations("classpath:db/voice-migration").load().migrate();}
            catch(RuntimeException e){source.close();throw e;}
            return source;
        }
        @Bean VoiceJobStore voiceJobStore(HikariDataSource source,ObjectMapper mapper,VoiceProperties p) {
            VoiceJobStore store=new VoiceJobStore(source,mapper,p);store.recover();return store;
        }
        @Bean VoiceDependencies voiceDependencies(VoiceProperties p,ObjectMapper mapper,Environment env) {
            if(env.matchesProfiles("secure","prod","production")&&p.isAllowLoopbackHttp())throw new IllegalArgumentException("Loopback HTTP is not supported in secure deployment");
            return new VoiceRemoteClient(p,mapper);
        }
        @Bean VoiceJobService voiceJobService(VoiceJobStore store,VoiceDependencies deps,ObjectMapper mapper,VoiceProperties p){return new VoiceJobService(store,deps,mapper,p);}
    }
}
