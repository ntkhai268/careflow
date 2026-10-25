package com.careflow.queue.service;

import com.careflow.common.event.EventEnvelope;
import com.careflow.common.exception.BusinessException;
import com.careflow.queue.messaging.AppointmentEventConsumer;
import com.careflow.queue.repository.ProcessedEventRepository;
import com.careflow.queue.repository.QueueEntryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "R2_DB_TESTS", matches = "1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppointmentOrderingPostgresTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private AppointmentEventConsumer consumer;

    @Configuration
    @Import(RoomQueuePostgresTest.Config.class)
    static class Config {
        @Bean
        AppointmentEventConsumer consumer(ProcessedEventRepository processed, QueueEntryRepository entries,
                                          QueueManagementService queues, QueueEventService events, JdbcTemplate jdbc) {
            return new AppointmentEventConsumer(processed, entries, queues, events, jdbc);
        }
    }

    @BeforeAll
    void open() {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = context.getBean(JdbcTemplate.class);
        consumer = context.getBean(AppointmentEventConsumer.class);
    }

    @AfterAll
    void close() {
        if (context != null) context.close();
    }

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM queue.appointment_cancellations");
        jdbc.update("DELETE FROM queue.processed_events");
        jdbc.update("DELETE FROM queue.idempotency_records");
        jdbc.update("DELETE FROM queue.queue_entries");
        jdbc.update("DELETE FROM queue.queue_number_sequences");
        jdbc.update("DELETE FROM queue.room_policy_audit");
        jdbc.update("DELETE FROM queue.queue_configs");
    }

    private EventEnvelope event(UUID appointment, String type) {
        var payload = new ObjectMapper().createObjectNode();
        payload.put("appointmentId", appointment.toString());
        if (type.equals("AppointmentConfirmed")) {
            payload.put("patientId", UUID.randomUUID().toString());
            payload.put("userId", RoomQueuePostgresTest.OWNER.toString());
            payload.put("departmentId", RoomQueuePostgresTest.DEPT.toString());
            payload.put("department", "NOI_TONG_QUAT");
            payload.put("roomId", "R2-ROOM-01");
            payload.put("roomDisplayName", "Synthetic room");
            payload.put("appointmentDate", LocalDate.now().toString());
            payload.put("timeSlot", "09:00-09:30");
        }
        return new EventEnvelope(UUID.randomUUID(), type, 1, appointment, 1,
                Instant.now(), "appointment-service", "r7-ordering-test", payload);
    }

    @Test
    void cancellationBeforeUnseenConfirmationCannotIssueATicket() {
        UUID appointment = UUID.randomUUID();
        consumer.consume(event(appointment, "AppointmentCancelled"));
        consumer.consume(event(appointment, "AppointmentConfirmed"));
        assertThat(count("queue.queue_entries")).isZero();
        assertThat(count("queue.appointment_cancellations")).isEqualTo(1);
        assertThat(count("queue.processed_events")).isEqualTo(2);
    }

    @Test
    void confirmationThenCancellationAndReplayKeepOneCancelledTicket() {
        UUID appointment = UUID.randomUUID();
        var confirmed = event(appointment, "AppointmentConfirmed");
        consumer.consume(confirmed);
        consumer.consume(event(appointment, "AppointmentCancelled"));
        consumer.consume(confirmed);
        consumer.consume(event(appointment, "AppointmentConfirmed"));
        assertThat(count("queue.queue_entries")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM queue.queue_entries", String.class)).isEqualTo("CANCELLED");
        assertThat(count("queue.processed_events")).isEqualTo(3);
    }

    @Test
    void repeatedCancellationsKeepOneDurableMarker() {
        UUID appointment = UUID.randomUUID();
        var cancelled = event(appointment, "AppointmentCancelled");
        consumer.consume(cancelled);
        consumer.consume(cancelled);
        consumer.consume(event(appointment, "AppointmentCancelled"));
        assertThat(count("queue.appointment_cancellations")).isEqualTo(1);
        assertThat(count("queue.processed_events")).isEqualTo(2);
    }

    @Test
    void concurrentConfirmationAndCancellationCannotLeaveAnActiveTicket() throws Exception {
        UUID appointment = UUID.randomUUID();
        var gate = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var outcomes = pool.invokeAll(java.util.List.<Callable<Void>>of(
                    () -> { gate.await(); consumer.consume(event(appointment, "AppointmentConfirmed")); return null; },
                    () -> { gate.await(); consumer.consume(event(appointment, "AppointmentCancelled")); return null; }));
            for (var outcome : outcomes) outcome.get();
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM queue.queue_entries WHERE status<>'CANCELLED'", Integer.class)).isZero();
        assertThat(count("queue.appointment_cancellations")).isEqualTo(1);
        assertThat(count("queue.processed_events")).isEqualTo(2);
    }

    @Test
    void cancellationSurvivesConsumerAndPersistenceContextRestart() {
        UUID appointment = UUID.randomUUID();
        consumer.consume(event(appointment, "AppointmentCancelled"));
        context.close();
        open();
        consumer.consume(event(appointment, "AppointmentConfirmed"));
        assertThat(count("queue.queue_entries")).isZero();
        assertThat(count("queue.appointment_cancellations")).isEqualTo(1);
    }

    @Test
    void unsupportedEventRollsBackWithoutCancellationOrReceipt() {
        assertThatThrownBy(() -> consumer.consume(event(UUID.randomUUID(), "UnsupportedEvent")))
                .isInstanceOf(BusinessException.class);
        assertThat(count("queue.appointment_cancellations")).isZero();
        assertThat(count("queue.processed_events")).isZero();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
