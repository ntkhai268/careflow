package com.careflow.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveSessionAutoConfigurationTest {
    @Test
    void nonServletContextGetsClientWithoutServletFilter() {
        new ApplicationContextRunner().withPropertyValues("security.caller.name=test-service", "security.caller.key=0123456789012345678901234567890123456789").withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                ActiveSessionAutoConfiguration.class)).run(context -> {
            assertThat(context).hasSingleBean(ActiveSessionClient.class);
            assertThat(context).doesNotHaveBean("activeSessionFilter");
        });
    }

    @Test
    void servletContextRegistersExactlyOneGuard() {
        new WebApplicationContextRunner().withPropertyValues("security.caller.name=test-service", "security.caller.key=0123456789012345678901234567890123456789").withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                ActiveSessionAutoConfiguration.class)).run(context -> {
            assertThat(context).hasSingleBean(ActiveSessionClient.class);
            assertThat(context).hasBean("activeSessionFilter");
            assertThat(context.getBeansOfType(org.springframework.boot.web.servlet.FilterRegistrationBean.class)).hasSize(1);
        });
    }
}
