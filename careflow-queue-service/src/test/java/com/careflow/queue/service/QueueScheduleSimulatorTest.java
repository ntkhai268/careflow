package com.careflow.queue.service;

import com.careflow.queue.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QueueScheduleSimulatorTest {
    private final LocalDate date = LocalDate.of(2026, 7, 20);

    @Test
    void interleavesTwoPriorityThenOneNormalAndAlternatesAppointmentWalkIn() {
        QueueConfig config = config(2, 1);
        List<QueueEntry> entries = new ArrayList<>();
        QueueEntry p1 = entry(PriorityLevel.PRIORITY, 1);
        QueueEntry p2 = entry(PriorityLevel.PRIORITY, 2);
        QueueEntry p3 = entry(PriorityLevel.PRIORITY, 3);
        QueueEntry p4 = entry(PriorityLevel.PRIORITY, 4);
        QueueEntry a1 = entry(PriorityLevel.APPOINTMENT, 5);
        QueueEntry a2 = entry(PriorityLevel.APPOINTMENT, 6);
        QueueEntry w1 = entry(PriorityLevel.WALK_IN, 7);
        entries.addAll(List.of(w1, a2, p4, a1, p2, p1, p3));

        assertThat(QueueScheduleSimulator.order(config, date, entries)).containsExactly(
                p1.getId(), p2.getId(), a1.getId(), p3.getId(), p4.getId(), w1.getId(), a2.getId());
    }

    @Test
    void emergencyAlwaysBypassesCycleWithoutChangingIt() {
        QueueConfig config = config(2, 1);
        config.setSchedulerDate(date);
        config.setCyclePhase(CyclePhase.PRIORITY);
        config.setServedInPhase(1);
        QueueEntry emergency = entry(PriorityLevel.EMERGENCY, 3);
        QueueEntry priority = entry(PriorityLevel.PRIORITY, 2);
        QueueEntry appointment = entry(PriorityLevel.APPOINTMENT, 1);

        assertThat(QueueScheduleSimulator.order(config, date, List.of(appointment, emergency, priority)))
                .containsExactly(emergency.getId(), priority.getId(), appointment.getId());
    }

    @Test
    void fallsBackWhenEitherGroupIsEmpty() {
        QueueConfig config = config(2, 1);
        QueueEntry a1 = entry(PriorityLevel.APPOINTMENT, 1);
        QueueEntry w1 = entry(PriorityLevel.WALK_IN, 2);
        QueueEntry a2 = entry(PriorityLevel.APPOINTMENT, 3);
        assertThat(QueueScheduleSimulator.order(config, date, List.of(a1, w1, a2)))
                .containsExactly(a1.getId(), w1.getId(), a2.getId());

        QueueEntry p1 = entry(PriorityLevel.PRIORITY, 4);
        QueueEntry p2 = entry(PriorityLevel.PRIORITY, 5);
        QueueEntry p3 = entry(PriorityLevel.PRIORITY, 6);
        assertThat(QueueScheduleSimulator.order(config, date, List.of(p1, p2, p3)))
                .containsExactly(p1.getId(), p2.getId(), p3.getId());
    }

    @Test
    void dueAppointmentIsProtectedFromPriorityCycle() {
        QueueConfig config = config(2, 1);
        config.setAvgConsultationMinutes(15);
        Instant now = Instant.parse("2026-07-20T01:00:00Z");
        QueueEntry appointment = scheduledAppointment(1, now);
        QueueEntry p1 = entry(PriorityLevel.PRIORITY, 2);
        QueueEntry p2 = entry(PriorityLevel.PRIORITY, 3);

        assertThat(QueueScheduleSimulator.schedule(config, date, List.of(p1, p2, appointment), now))
                .extracting(QueueScheduleSimulator.ScheduledEntry::entryId)
                .containsExactly(appointment.getId(), p1.getId(), p2.getId());
    }

    @Test
    void fillsOnlyCapacityThatFinishesBeforeAppointment() {
        QueueConfig config = config(2, 1);
        config.setAvgConsultationMinutes(15);
        Instant now = Instant.parse("2026-07-20T00:30:00Z");
        QueueEntry appointment = scheduledAppointment(1, Instant.parse("2026-07-20T01:00:00Z"));
        QueueEntry p1 = entry(PriorityLevel.PRIORITY, 2);
        QueueEntry p2 = entry(PriorityLevel.PRIORITY, 3);
        QueueEntry p3 = entry(PriorityLevel.PRIORITY, 4);

        List<QueueScheduleSimulator.ScheduledEntry> schedule = QueueScheduleSimulator.schedule(
                config, date, List.of(p1, p2, p3, appointment), now);

        assertThat(schedule).extracting(QueueScheduleSimulator.ScheduledEntry::entryId)
                .containsExactly(p1.getId(), p2.getId(), appointment.getId(), p3.getId());
        assertThat(schedule.get(2).projectedStartAt()).isEqualTo(appointment.getScheduledStartAt());
    }

    @Test
    void emergencyCanDelayAppointmentButDoesNotAdvanceCycle() {
        QueueConfig config = config(2, 1);
        config.setAvgConsultationMinutes(15);
        config.setSchedulerDate(date);
        config.setServedInPhase(1);
        Instant now = Instant.parse("2026-07-20T01:00:00Z");
        QueueEntry emergency = entry(PriorityLevel.EMERGENCY, 1);
        QueueEntry appointment = scheduledAppointment(2, now);
        QueueEntry priority = entry(PriorityLevel.PRIORITY, 3);

        List<QueueScheduleSimulator.ScheduledEntry> schedule = QueueScheduleSimulator.schedule(
                config, date, List.of(priority, appointment, emergency), now);

        assertThat(schedule).extracting(QueueScheduleSimulator.ScheduledEntry::entryId)
                .containsExactly(emergency.getId(), appointment.getId(), priority.getId());
        assertThat(schedule.get(1).projectedStartAt()).isEqualTo(now.plusSeconds(15 * 60));
        assertThat(schedule.get(2).mode())
                .isEqualTo(QueueScheduleSimulator.SelectionMode.PRIORITY_CYCLE);
    }

    private QueueConfig config(int n, int m) {
        QueueConfig config = new QueueConfig();
        config.setPriorityRatioN(n);
        config.setNormalRatioM(m);
        config.setCyclePhase(CyclePhase.PRIORITY);
        config.setNormalCursor(NormalCursor.APPOINTMENT);
        return config;
    }

    private QueueEntry entry(PriorityLevel level, int sequence) {
        QueueEntry entry = new QueueEntry();
        entry.setId(UUID.randomUUID());
        entry.setPriorityLevel(level);
        entry.setStatus(QueueStatus.CHECKED_IN);
        entry.setSequenceNumber(sequence);
        entry.setEligibleSinceAt(Instant.parse("2026-07-20T00:00:00Z").plusSeconds(sequence));
        return entry;
    }

    private QueueEntry scheduledAppointment(int sequence, Instant scheduledStartAt) {
        QueueEntry entry = entry(PriorityLevel.APPOINTMENT, sequence);
        entry.setScheduledStartAt(scheduledStartAt);
        return entry;
    }
}
