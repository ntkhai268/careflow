package com.careflow.ai.controller;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
/** Closed by default. Only the independently gated, authorized R6 routes can open. */
@Component
public class AiAvailabilityFilter extends OncePerRequestFilter {
    private final boolean voice,clinical;
    public AiAvailabilityFilter(@Value("${careflow.voice.enabled:false}") boolean voice,@Value("${careflow.clinical.enabled:false}") boolean clinical){this.voice=voice;this.clinical=clinical;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String p=request.getRequestURI();boolean allowed=p.equals("/api/ai/capabilities")||(voice&&(p.equals("/api/ai/voice-prescription/jobs")||p.startsWith("/api/ai/voice-prescription/jobs/")))||(clinical&&(p.equals("/api/ai/clinical-chat")||p.equals("/api/ai/clinical-suggestions")||p.startsWith("/api/ai/interactions/")||p.startsWith("/api/ai/consultations/")||p.equals("/api/ai/knowledge")||p.startsWith("/api/ai/knowledge/")));
        if(p.startsWith("/api/ai")&&!allowed){response.setStatus(503);response.setHeader("Cache-Control","no-store");response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"status\":503,\"message\":\"Năng lực AI này chưa được bật hoặc chưa được nghiệm thu.\"}");return;}chain.doFilter(request,response);
    }
}
