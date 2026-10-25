package com.careflow.queue.service;

import com.careflow.queue.domain.*;
import com.careflow.queue.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class QueueNumberAllocationTest {
    @Test void missingCounterStartsAboveSeededEntries() { assertAllocation(null, 5, 6); }
    @Test void staleCounterDoesNotReuseImportedNumbers() { assertAllocation(3, 50, 51); }
    @Test void higherCounterNeverMovesBackwards() { assertAllocation(100, 5, 101); }

    private void assertAllocation(Integer counter, int persistedMaximum, int expected) {
        var configs = mock(QueueConfigRepository.class);
        var sequences = mock(QueueNumberSequenceRepository.class);
        var entries = mock(QueueEntryRepository.class);
        var config = new QueueConfig(); config.setId(UUID.randomUUID());
        config.setDepartmentId(UUID.randomUUID()); config.setQueuePrefix("NOI");
        LocalDate date = LocalDate.now();
        QueueNumberSequence sequence = new QueueNumberSequence();
        if (counter != null) sequence.setLastNumber(counter);
        when(sequences.findByQueueConfigIdAndQueueDate(config.getId(), date))
                .thenReturn(counter == null ? Optional.empty() : Optional.of(sequence));
        if (counter == null) when(sequences.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(entries.maximumSequence(config.getId(), date)).thenReturn(persistedMaximum);
        var queues = new QueueManagementService(configs, sequences, mock(ServicePointSequenceRepository.class),
                entries, mock(IdempotencyRecordRepository.class), mock(QueueEventService.class),
                mock(QrTokenService.class), "Asia/Ho_Chi_Minh");
        var result = queues.createAppointmentEntry(config, date, LocalTime.of(8, 0), "08:00-08:30",
                "NOI_TONG_QUAT", "ROOM-01", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThat(result.getSequenceNumber()).isEqualTo(expected);
        assertThat(result.getQueueNumber()).isEqualTo("NOI-" + String.format("%03d", expected));
    }
}
