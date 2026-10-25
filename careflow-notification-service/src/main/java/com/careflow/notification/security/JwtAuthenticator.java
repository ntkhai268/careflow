package com.careflow.notification.security;

import com.careflow.common.security.ActiveSessionClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class JwtAuthenticator {
    private final ActiveSessionClient sessions;
    public JwtAuthenticator(ActiveSessionClient sessions) { this.sessions = sessions; }
    public UsernamePasswordAuthenticationToken authenticate(String authorization) {
        return authentication(sessions.verify(authorization,null));
    }
    public UsernamePasswordAuthenticationToken authenticateConnect(String authorization,String proof) {
        return authentication(sessions.verifyRequest(authorization,null,proof,"GET","/ws/notifications",null,null));
    }
    private UsernamePasswordAuthenticationToken authentication(ActiveSessionClient.Principal session) {
        return new UsernamePasswordAuthenticationToken(session.userId().toString(),null,
                List.of(new SimpleGrantedAuthority("ROLE_"+session.role())));
    }
}
