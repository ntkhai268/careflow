package com.careflow.identity.recovery;
import java.util.UUID;

public interface RecoveryEmailSender {
    boolean configured();
    void sendCode(String email,String code,UUID requestId);
    void sendChanged(String email,UUID requestId);
    /** Acceptance by the provider, not a guarantee of delivery to an inbox. */
    default String sendTracked(String kind,String email,String code,UUID requestId) {
        if("CODE".equals(kind)) sendCode(email,code,requestId);
        else if("CHANGED".equals(kind)) sendChanged(email,requestId);
        else throw new IllegalStateException("Sender does not support this mail purpose");
        return null;
    }
}
