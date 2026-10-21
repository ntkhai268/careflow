package com.careflow.ai.voice;

import com.careflow.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class VoiceRouteFilter extends OncePerRequestFilter {
    private final VoiceProperties properties;private final boolean secure;private final ObjectMapper mapper;
    public VoiceRouteFilter(VoiceProperties properties,Environment env,ObjectMapper mapper){this.properties=properties;this.secure=env.matchesProfiles("secure","prod","production");this.mapper=mapper;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getRequestURI();boolean voice=path.equals("/api/ai/voice-prescription")||path.startsWith("/api/ai/voice-prescription/");
        if(voice&&request.getContentLengthLong()>21L*1024*1024) {
            response.setStatus(413);response.setContentType("application/json;charset=UTF-8");
            mapper.writeValue(response.getOutputStream(),ApiResponse.error(413,"Yêu cầu vượt giới hạn audio"));return;
        }
        if((voice&&!properties.isEnabled())||(!voice&&path.startsWith("/api/ai/")&&secure)) {
            response.setStatus(503);response.setContentType("application/json;charset=UTF-8");response.setHeader("Cache-Control","no-store");
            mapper.writeValue(response.getOutputStream(),ApiResponse.error(503,"Năng lực AI này chưa được bật cho môi trường hiện tại"));return;
        }
        chain.doFilter(request,response);
    }
}
