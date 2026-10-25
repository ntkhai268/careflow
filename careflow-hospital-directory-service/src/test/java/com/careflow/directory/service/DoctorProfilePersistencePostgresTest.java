package com.careflow.directory.service;

import com.careflow.directory.dto.request.*;
import com.careflow.directory.model.DoctorProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="R1_DIRECTORY_TEST_JDBC",matches="jdbc:postgresql://127\\.0\\.0\\.1:15432/careflow_r1_directory_persistence_test")
@SpringBootTest(classes=DoctorProfilePersistencePostgresTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
        properties={"eureka.client.enabled=false","spring.jpa.hibernate.ddl-auto=update","spring.jpa.show-sql=false","spring.flyway.enabled=true"})
class DoctorProfilePersistencePostgresTest {
    @Configuration
    @EnableAutoConfiguration(exclude=com.careflow.common.security.ActiveSessionAutoConfiguration.class)
    @EntityScan(basePackageClasses=DoctorProfile.class)
    @EnableJpaRepositories(basePackages="com.careflow.directory.repository")
    @Import(DirectoryService.class)
    static class Config {
        @Bean StaffIdentityVerifier identity(){return mock(StaffIdentityVerifier.class);}
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties){
        properties.add("spring.datasource.url",()->System.getenv("R1_DIRECTORY_TEST_JDBC"));
        properties.add("spring.datasource.username",()->System.getenv("RECOVERY_TEST_USER"));
        properties.add("spring.datasource.password",()->System.getenv("RECOVERY_TEST_PASSWORD"));
    }
    @Autowired DirectoryService directory;
    @Autowired JdbcTemplate jdbc;
    @Test void createsGeneratedProfileIdThenReloadsAndResumesTheSameProfile(){
        UUID user=UUID.randomUUID();
        var created=directory.createDoctorProfile(CreateDoctorProfileRequest.builder().userId(user).fullName("Synthetic doctor").isActive(true).build());
        assertThat(created.getId()).isNotNull().isNotEqualTo(user);
        assertThat(directory.getDoctorByUserId(user).getId()).isEqualTo(created.getId());
        var updated=directory.updateDoctorProfile(created.getId(),UpdateDoctorProfileRequest.builder().fullName("Updated synthetic doctor").isActive(false).build());
        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(directory.getAllDoctorProfilesForAdministration()).anySatisfy(profile->{
            assertThat(profile.getId()).isEqualTo(created.getId());assertThat(profile.getIsActive()).isFalse();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM doctor_profiles WHERE user_id=?",Long.class,user)).isEqualTo(1);
    }
}
