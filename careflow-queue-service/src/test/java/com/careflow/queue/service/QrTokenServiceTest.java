package com.careflow.queue.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.queue.domain.QueueEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class QrTokenServiceTest {
    private static final String SECRET = "a-very-long-test-secret-that-is-at-least-thirty-two-bytes";

    @Test
    void signedTokenRoundTripsRequiredClaims() {
        QrTokenService service = new QrTokenService(SECRET, "Asia/Ho_Chi_Minh");
        QueueEntry entry = new QueueEntry();
        entry.setId(UUID.randomUUID());
        entry.setAppointmentId(UUID.randomUUID());
        entry.setUserId(UUID.randomUUID());
        entry.setQueueDate(LocalDate.now().plusDays(1));

        QrTokenService.QrClaims claims = service.verify(service.issue(entry));

        assertThat(claims.appointmentId()).isEqualTo(entry.getAppointmentId());
        assertThat(claims.ticketId()).isEqualTo(entry.getId());
        assertThat(claims.userId()).isEqualTo(entry.getUserId());
        assertThat(claims.queueDate()).isEqualTo(entry.getQueueDate());
    }

    @Test
    void rejectsTamperedToken() {
        QrTokenService service = new QrTokenService(SECRET, "Asia/Ho_Chi_Minh");
        assertThatThrownBy(() -> service.verify("not.a.valid-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(401);
    }
}
