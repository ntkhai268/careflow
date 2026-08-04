package com.careflow.queue.service;

import com.careflow.queue.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultationQueueSchedulerTest {
    @Test
    void rotatesPriorityNormalAndResultReviewAndKeepsFifoInsideEachLane() {
        QueueEntry p1 = entry(QueueClass.PRIORITY, ConsultationPhase.INITIAL, 1);
        QueueEntry p2 = entry(QueueClass.PRIORITY, ConsultationPhase.INITIAL, 2);
        QueueEntry n1 = entry(QueueClass.NORMAL, ConsultationPhase.INITIAL, 3);
        QueueEntry r1 = entry(null, ConsultationPhase.RESULT_REVIEW, 4);

        var scheduled = ConsultationQueueScheduler.schedule(
                List.of(n1, p2, r1, p1), SchedulingLane.RESULT_REVIEW, Instant.EPOCH, 10);

        assertThat(scheduled).extracting(ConsultationQueueScheduler.ScheduledEntry::entryId)
                .containsExactly(p1.getId(), n1.getId(), r1.getId(), p2.getId());
    }

    @Test
    void skipsEmptyLane() {
        QueueEntry p1 = entry(QueueClass.PRIORITY, ConsultationPhase.INITIAL, 1);
        QueueEntry r1 = entry(null, ConsultationPhase.RESULT_REVIEW, 2);

        assertThat(ConsultationQueueScheduler.next(List.of(p1, r1), SchedulingLane.PRIORITY))
                .isSameAs(r1);
    }

    private QueueEntry entry(QueueClass queueClass, ConsultationPhase phase, int sequence) {
        QueueEntry entry = new QueueEntry();
        entry.setId(UUID.randomUUID());
        entry.setQueueType(QueueType.CONSULTATION);
        entry.setConsultationPhase(phase);
        entry.setQueueClass(queueClass);
        entry.setStatus(phase == ConsultationPhase.INITIAL ? QueueStatus.CHECKED_IN : QueueStatus.QUEUED);
        entry.setSequenceNumber(sequence);
        entry.setEligibleSinceAt(Instant.EPOCH.plusSeconds(sequence));
        return entry;
    }
}
