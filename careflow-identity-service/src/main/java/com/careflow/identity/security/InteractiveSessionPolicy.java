package com.careflow.identity.security;

import com.careflow.identity.domain.RefreshToken;
import com.careflow.identity.domain.UserRole;
import java.time.Instant;

/** API polling and refresh never count as human activity. */
public final class InteractiveSessionPolicy {
    private InteractiveSessionPolicy() { }
    public static long idleSeconds(UserRole role) { return role==UserRole.PATIENT?1800:900; }
    public static boolean expired(RefreshToken session,Instant now) {
        Instant last=session.getLastInteractiveAt();
        return last==null || !last.plusSeconds(idleSeconds(session.getUser().getRole())).isAfter(now);
    }
}
