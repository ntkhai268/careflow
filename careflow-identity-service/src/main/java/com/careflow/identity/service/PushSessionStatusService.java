package com.careflow.identity.service;
import com.careflow.identity.repository.RefreshTokenRepository;
import com.careflow.identity.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/** A generic push grant follows refresh rotation, but never grants access to medical APIs. */
@Service
public class PushSessionStatusService {
    private final RefreshTokenRepository tokens;
    public PushSessionStatusService(RefreshTokenRepository tokens){this.tokens=tokens;}
    @Transactional(readOnly=true)
    public boolean active(UUID user,UUID session){
        var token=tokens.findById(session).orElse(null);var seen=new HashSet<UUID>();var now=Instant.now();
        for(int hops=0;token!=null && hops<256;hops++){
            if(!seen.add(token.getId()) || !user.equals(token.getUser().getId()) || token.getUser().getStatus()!=UserStatus.ACTIVE || token.getUser().getRole()!=UserRole.PATIENT)return false;
            if(token.getAbsoluteExpiresAt()==null || !token.getAbsoluteExpiresAt().isAfter(now))return false;
            if(!token.isRevoked())return !token.isExpired(now);
            String next=token.getReplacedByTokenHash();if(next==null)return false;token=tokens.findForPushByTokenHash(next).orElse(null);
        }
        return false;
    }
}
