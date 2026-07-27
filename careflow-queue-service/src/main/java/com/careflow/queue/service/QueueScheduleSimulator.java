package com.careflow.queue.service;

import com.careflow.queue.domain.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public final class QueueScheduleSimulator {
    private QueueScheduleSimulator() {}

    public enum SelectionMode {
        EMERGENCY,
        APPOINTMENT_PROTECTED,
        PRIORITY_CYCLE,
        NORMAL_CYCLE
    }

    public record ScheduledEntry(UUID entryId, Instant projectedStartAt, SelectionMode mode) {}

    public static List<UUID> order(QueueConfig config, LocalDate date, Collection<QueueEntry> entries) {
        return schedule(config, date, entries, Instant.now()).stream()
                .map(ScheduledEntry::entryId)
                .toList();
    }

    /**
     * Builds the same order used by call-next and the dashboard.
     *
     * <p>A checked-in appointment with a scheduled start is protected from being made late by
     * priority and walk-in patients. If another average consultation still fits before the
     * appointment, that capacity can be used. Otherwise the appointment is selected (at most one
     * average consultation early). Emergencies always bypass this protection.</p>
     */
    public static List<ScheduledEntry> schedule(
            QueueConfig config, LocalDate date, Collection<QueueEntry> entries, Instant availableAt) {
        Comparator<QueueEntry> byEligibility = Comparator
                .comparing(QueueEntry::getEligibleSinceAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(QueueEntry::getSequenceNumber);
        Comparator<QueueEntry> byAppointmentTime = Comparator
                .comparing(QueueEntry::getScheduledStartAt)
                .thenComparing(byEligibility);

        Map<PriorityLevel, ArrayDeque<QueueEntry>> queues = new EnumMap<>(PriorityLevel.class);
        queues.put(PriorityLevel.EMERGENCY, new ArrayDeque<>());
        queues.put(PriorityLevel.PRIORITY, new ArrayDeque<>());
        queues.put(PriorityLevel.WALK_IN, new ArrayDeque<>());
        ArrayDeque<QueueEntry> scheduledAppointments = new ArrayDeque<>();
        ArrayDeque<QueueEntry> legacyAppointments = new ArrayDeque<>();

        List<QueueEntry> checkedIn = entries.stream()
                .filter(entry -> entry.getStatus() == QueueStatus.CHECKED_IN)
                .toList();
        checkedIn.stream()
                .filter(entry -> entry.getPriorityLevel() != PriorityLevel.APPOINTMENT)
                .sorted(byEligibility)
                .forEach(entry -> queues.get(entry.getPriorityLevel()).add(entry));
        checkedIn.stream()
                .filter(entry -> entry.getPriorityLevel() == PriorityLevel.APPOINTMENT)
                .filter(entry -> entry.getScheduledStartAt() != null)
                .sorted(byAppointmentTime)
                .forEach(scheduledAppointments::add);
        checkedIn.stream()
                .filter(entry -> entry.getPriorityLevel() == PriorityLevel.APPOINTMENT)
                .filter(entry -> entry.getScheduledStartAt() == null)
                .sorted(byEligibility)
                .forEach(legacyAppointments::add);

        CyclePhase phase = date.equals(config.getSchedulerDate())
                ? config.getCyclePhase() : CyclePhase.PRIORITY;
        int served = date.equals(config.getSchedulerDate()) ? config.getServedInPhase() : 0;
        NormalCursor cursor = date.equals(config.getSchedulerDate())
                ? config.getNormalCursor() : NormalCursor.APPOINTMENT;
        Duration consultation = Duration.ofMinutes(config.getAvgConsultationMinutes());
        Instant projectedStart = availableAt;
        List<ScheduledEntry> result = new ArrayList<>();

        while (hasWaiting(queues, scheduledAppointments, legacyAppointments)) {
            QueueEntry emergency = queues.get(PriorityLevel.EMERGENCY).poll();
            if (emergency != null) {
                result.add(new ScheduledEntry(
                        emergency.getId(), projectedStart, SelectionMode.EMERGENCY));
                projectedStart = projectedStart.plus(consultation);
                continue;
            }

            QueueEntry appointment = scheduledAppointments.peek();
            if (appointment != null && !appointment.getScheduledStartAt().isAfter(projectedStart)) {
                scheduledAppointments.poll();
                result.add(new ScheduledEntry(
                        appointment.getId(), projectedStart, SelectionMode.APPOINTMENT_PROTECTED));
                projectedStart = projectedStart.plus(consultation);
                continue;
            }

            if (phase == CyclePhase.PRIORITY) {
                QueueEntry priority = queues.get(PriorityLevel.PRIORITY).peek();
                if (priority != null) {
                    if (wouldDelay(appointment, projectedStart, consultation)) {
                        scheduledAppointments.poll();
                        result.add(new ScheduledEntry(
                                appointment.getId(), projectedStart, SelectionMode.APPOINTMENT_PROTECTED));
                    } else {
                        queues.get(PriorityLevel.PRIORITY).poll();
                        result.add(new ScheduledEntry(
                                priority.getId(), projectedStart, SelectionMode.PRIORITY_CYCLE));
                        served++;
                        if (served >= config.getPriorityRatioN()) {
                            phase = CyclePhase.NORMAL;
                            served = 0;
                        }
                    }
                    projectedStart = projectedStart.plus(consultation);
                    continue;
                }
                phase = CyclePhase.NORMAL;
                served = 0;
            }

            QueueEntry normal = pollNormal(cursor, legacyAppointments, queues.get(PriorityLevel.WALK_IN));
            if (normal != null) {
                if (wouldDelay(appointment, projectedStart, consultation)) {
                    restoreNormal(normal, legacyAppointments, queues.get(PriorityLevel.WALK_IN));
                    scheduledAppointments.poll();
                    result.add(new ScheduledEntry(
                            appointment.getId(), projectedStart, SelectionMode.APPOINTMENT_PROTECTED));
                } else {
                    result.add(new ScheduledEntry(
                            normal.getId(), projectedStart, SelectionMode.NORMAL_CYCLE));
                    cursor = normal.getPriorityLevel() == PriorityLevel.APPOINTMENT
                            ? NormalCursor.WALK_IN : NormalCursor.APPOINTMENT;
                    served++;
                    if (served >= config.getNormalRatioM()) {
                        phase = CyclePhase.PRIORITY;
                        served = 0;
                    }
                }
                projectedStart = projectedStart.plus(consultation);
                continue;
            }

            if (!queues.get(PriorityLevel.PRIORITY).isEmpty()) {
                phase = CyclePhase.PRIORITY;
                served = 0;
                continue;
            }
            if (appointment != null) {
                projectedStart = appointment.getScheduledStartAt();
                continue;
            }
            break;
        }
        return result;
    }

    private static boolean hasWaiting(
            Map<PriorityLevel, ArrayDeque<QueueEntry>> queues,
            ArrayDeque<QueueEntry> scheduledAppointments,
            ArrayDeque<QueueEntry> legacyAppointments) {
        return !scheduledAppointments.isEmpty()
                || !legacyAppointments.isEmpty()
                || queues.values().stream().anyMatch(queue -> !queue.isEmpty());
    }

    private static boolean wouldDelay(
            QueueEntry nextAppointment, Instant projectedStart, Duration consultation) {
        return nextAppointment != null
                && projectedStart.plus(consultation).isAfter(nextAppointment.getScheduledStartAt());
    }

    private static QueueEntry pollNormal(
            NormalCursor cursor,
            ArrayDeque<QueueEntry> appointments,
            ArrayDeque<QueueEntry> walkIns) {
        QueueEntry selected;
        if (cursor == NormalCursor.APPOINTMENT) {
            selected = appointments.poll();
            return selected != null ? selected : walkIns.poll();
        }
        selected = walkIns.poll();
        return selected != null ? selected : appointments.poll();
    }

    private static void restoreNormal(
            QueueEntry entry,
            ArrayDeque<QueueEntry> appointments,
            ArrayDeque<QueueEntry> walkIns) {
        if (entry.getPriorityLevel() == PriorityLevel.APPOINTMENT) appointments.addFirst(entry);
        else walkIns.addFirst(entry);
    }
}
