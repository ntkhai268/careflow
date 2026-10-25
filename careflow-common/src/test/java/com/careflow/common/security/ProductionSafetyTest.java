package com.careflow.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class ProductionSafetyTest {
    final ProductionSafetyAutoConfiguration safety=new ProductionSafetyAutoConfiguration();
    @Test void productionCannotStartWithoutSecureProfile() {
        var environment=new MockEnvironment();environment.setActiveProfiles("production");
        assertThatThrownBy(()->safety.productionSafetyChecks(environment)).isInstanceOf(IllegalStateException.class);
    }
    @Test void demoDataIsForbiddenEvenWhenSecureAlsoEnabled() {
        var environment=new MockEnvironment();environment.setActiveProfiles("secure","production","demo");
        assertThatThrownBy(()->safety.productionSafetyChecks(environment)).isInstanceOf(IllegalStateException.class);
    }
    @Test void unverifiedDatabaseTransportIsForbidden() {
        var environment=new MockEnvironment();environment.setActiveProfiles("secure","production");
        environment.setProperty("spring.datasource.url","jdbc:postgresql://db:5432/test");
        assertThatThrownBy(()->safety.productionSafetyChecks(environment)).isInstanceOf(IllegalStateException.class);
    }
    @Test void identityCannotSilentlyEnableWeakBootstrapInProduction() {
        var environment=new MockEnvironment();environment.setActiveProfiles("secure","production");
        environment.setProperty("spring.application.name","identity-service");
        assertThatThrownBy(()->safety.productionSafetyChecks(environment)).isInstanceOf(IllegalStateException.class);
        environment.setProperty("security.auth.require-staff-passkey","true");
        assertThatThrownBy(()->safety.productionSafetyChecks(environment)).isInstanceOf(IllegalStateException.class);
        environment.setProperty("security.auth.public-registration-enabled","false");
        assertThatCode(()->safety.productionSafetyChecks(environment)).doesNotThrowAnyException();
    }
}
