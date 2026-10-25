package com.careflow.ai.clinical;
import org.springframework.context.annotation.*;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.beans.factory.annotation.Value;import com.fasterxml.jackson.databind.ObjectMapper;import javax.sql.DataSource;
@Configuration
@ConditionalOnProperty(name="careflow.clinical.enabled",havingValue="true")
public class ClinicalConfiguration {
    @Bean ClinicalStore clinicalStore(DataSource source,ObjectMapper json,@Value("${careflow.clinical.content-key}") String key,@Value("${careflow.clinical.retention-days:7}") int days){if(days<1||days>30)throw new IllegalArgumentException("Clinical retention must be1–30days");return new ClinicalStore(source,json,new ClinicalCipher(key),days);}
    @Bean ClinicalEngine clinicalEngine(ClinicalStore store,AiAccess access,ObjectMapper json,@Value("${careflow.clinical.allow-synthetic:false}") boolean synthetic,org.springframework.core.env.Environment env){if(synthetic&&env.matchesProfiles("prod","production"))throw new IllegalArgumentException("Synthetic clinical sources are forbidden in production");return new ClinicalEngine(store,access,json,synthetic);}
}
