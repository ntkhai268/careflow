package com.careflow.identity.recovery;

import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.*;
import java.time.*;

@Component
public class RecoveryMailListener {
    private final PasswordResetCodeRepository codes;
    private final Clock clock;
    private final RecoveryMailOutbox outbox;
    public RecoveryMailListener(RecoveryMailOutbox outbox,PasswordResetCodeRepository codes,Clock clock) {
        this.outbox=outbox;this.codes=codes;this.clock=clock;
    }
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void requested(PasswordRecoveryService.CodeRequested event) {
        if(event.deliver()) outbox.enqueue(event.requestId(),"CODE",event.email(),event.code());
        else codes.delivery(event.requestId(),"SENT");
    }
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void changed(PasswordRecoveryService.PasswordChanged event) {
        outbox.enqueue(event.requestId(),"CHANGED",event.email(),null);
    }

    @Scheduled(fixedDelay=3600000)
    @Transactional
    public void cleanup() { codes.cleanup(clock.instant().minusSeconds(86400)); }
}
