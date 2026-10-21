package com.careflow.common.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@AutoConfiguration
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class ClinicalAuditAutoConfiguration {
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> clinicalAuditFilter() {
        Logger log=LoggerFactory.getLogger("careflow.security.audit");
        OncePerRequestFilter filter=new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
                    throws ServletException,IOException {
                var principal=request.getAttribute(ActiveSessionFilter.VERIFIED_PRINCIPAL_ATTRIBUTE);
                if(!(principal instanceof ActiveSessionClient.Principal actor)) { chain.doFilter(request,response);return; }
                long started=System.nanoTime();boolean completed=false;
                try { chain.doFilter(request,response);completed=true; }
                finally {
                    // No request bodies, queries, tokens, OTPs or clinical text enter this audit stream.
                    log.info("event=API_ACCESS actor={} role={} method={} path={} outcome={} status={} correlation={} durationMs={}",
                            actor.userId(),actor.role(),request.getMethod(),request.getRequestURI(),completed?"COMPLETED":"ERROR",
                            completed?response.getStatus():500,request.getHeader("X-Correlation-Id"),(System.nanoTime()-started)/1_000_000);
                }
            }
        };
        var registration=new FilterRegistrationBean<>(filter);registration.setName("careflowClinicalAudit");
        registration.addUrlPatterns("/api/*");registration.setOrder(Ordered.HIGHEST_PRECEDENCE+25);return registration;
    }
}
