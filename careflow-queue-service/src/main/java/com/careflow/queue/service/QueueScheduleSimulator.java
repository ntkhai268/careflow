package com.careflow.queue.service;

import com.careflow.queue.domain.*;

import java.time.LocalDate;
import java.util.*;

public final class QueueScheduleSimulator {
    private QueueScheduleSimulator() {}

    public static List<UUID> order(QueueConfig config, LocalDate date, Collection<QueueEntry> entries) {
        Comparator<QueueEntry> byEligibility = Comparator.comparing(QueueEntry::getEligibleSinceAt)
                .thenComparingInt(QueueEntry::getSequenceNumber);
        Map<PriorityLevel, ArrayDeque<QueueEntry>> queues = new EnumMap<>(PriorityLevel.class);
        for (PriorityLevel level : PriorityLevel.values()) queues.put(level, new ArrayDeque<>());
        entries.stream().filter(e -> e.getStatus() == QueueStatus.CHECKED_IN).sorted(byEligibility)
                .forEach(e -> queues.get(e.getPriorityLevel()).add(e));

        CyclePhase phase = date.equals(config.getSchedulerDate()) ? config.getCyclePhase() : CyclePhase.PRIORITY;
        int served = date.equals(config.getSchedulerDate()) ? config.getServedInPhase() : 0;
        NormalCursor cursor = date.equals(config.getSchedulerDate()) ? config.getNormalCursor() : NormalCursor.APPOINTMENT;
        List<UUID> result = new ArrayList<>();

        while (queues.values().stream().anyMatch(q -> !q.isEmpty())) {
            QueueEntry emergency = queues.get(PriorityLevel.EMERGENCY).poll();
            if (emergency != null) {
                result.add(emergency.getId());
                continue;
            }
            if (phase == CyclePhase.PRIORITY) {
                QueueEntry priority = queues.get(PriorityLevel.PRIORITY).poll();
                if (priority != null) {
                    result.add(priority.getId());
                    served++;
                    if (served >= config.getPriorityRatioN()) { phase = CyclePhase.NORMAL; served = 0; }
                    continue;
                }
                phase = CyclePhase.NORMAL;
                served = 0;
            }

            PriorityLevel preferred = cursor == NormalCursor.APPOINTMENT ? PriorityLevel.APPOINTMENT : PriorityLevel.WALK_IN;
            PriorityLevel alternate = preferred == PriorityLevel.APPOINTMENT ? PriorityLevel.WALK_IN : PriorityLevel.APPOINTMENT;
            QueueEntry normal = queues.get(preferred).poll();
            if (normal == null) normal = queues.get(alternate).poll();
            if (normal != null) {
                result.add(normal.getId());
                cursor = normal.getPriorityLevel() == PriorityLevel.APPOINTMENT ? NormalCursor.WALK_IN : NormalCursor.APPOINTMENT;
                served++;
                if (served >= config.getNormalRatioM()) { phase = CyclePhase.PRIORITY; served = 0; }
                continue;
            }
            if (!queues.get(PriorityLevel.PRIORITY).isEmpty()) {
                phase = CyclePhase.PRIORITY;
                served = 0;
                continue;
            }
            break;
        }
        return result;
    }
}
