package com.careflow.ai.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class VoiceRouteFilterTest {
    @Test void flagIsOffByDefaultAndLegacyRemainsBlockedInSecure() throws Exception {
        VoiceProperties p=new VoiceProperties();MockEnvironment env=new MockEnvironment();env.setActiveProfiles("secure");
        VoiceRouteFilter filter=new VoiceRouteFilter(p,env,new ObjectMapper().findAndRegisterModules());
        for(String path:new String[]{"/api/ai/voice-prescription/jobs","/api/ai/clinical-chat","/api/ai/voice-to-clinical-fields"}) {
            MockHttpServletResponse response=new MockHttpServletResponse();MockFilterChain chain=new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("POST",path),response,chain);
            assertThat(response.getStatus()).isEqualTo(503);assertThat(chain.getRequest()).isNull();
        }
        p.setEnabled(true);MockHttpServletResponse response=new MockHttpServletResponse();MockFilterChain chain=new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET","/api/ai/voice-prescription/jobs/id"),response,chain);
        assertThat(chain.getRequest()).isNotNull();
    }
}
