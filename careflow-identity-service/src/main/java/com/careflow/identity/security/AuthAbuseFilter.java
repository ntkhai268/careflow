package com.careflow.identity.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Instant;

/** Shared admission budget before proof verification and expensive password hashing. */
@Component
@Order(-200)
public class AuthAbuseFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    private final int budget;
    public AuthAbuseFilter(JdbcTemplate jdbc,@Value("${security.auth.requests-per-minute:600}") int budget) {
        if(budget<1) throw new IllegalArgumentException("Positive auth admission budget required");
        this.jdbc=jdbc;this.budget=budget;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api/auth/")
                || request.getRequestURI().equals("/api/auth/introspect");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        // DB time keeps the budget consistent across instances. Never trust user-supplied forwarded IPs here.
        try {
            Integer count=jdbc.queryForObject("INSERT INTO identity.auth_rate_windows(bucket,hits,expires_at) "
                    +"VALUES ('global:' || floor(extract(epoch from CURRENT_TIMESTAMP)/60)::text,1,CURRENT_TIMESTAMP+interval '2 minutes') "
                    +"ON CONFLICT(bucket) DO UPDATE SET hits=LEAST(identity.auth_rate_windows.hits,1000000)+1 RETURNING hits",Integer.class);
            if(count==null || count>budget) { reject(response,429,"Quá nhiều yêu cầu. Vui lòng thử lại sau.");return; }
        } catch(RuntimeException failure) { reject(response,503,"Dịch vụ xác thực tạm thời chưa sẵn sàng.");return; }
        chain.doFilter(request,response);
    }
    private void reject(HttpServletResponse response,int status,String message) throws IOException {
        response.setStatus(status);response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control","no-store");
        if(status==429) response.setHeader("Retry-After","60");
        response.getWriter().write("{\"status\":"+status+",\"message\":\""+message+"\"}");
    }
}
