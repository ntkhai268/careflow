package com.careflow.queue.service;

import com.careflow.queue.domain.QueueEntry;
import com.careflow.queue.domain.SchedulingLane;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

final class ConsultationQueueScheduler {
    private static final List<SchedulingLane> ORDER = List.of(
            SchedulingLane.PRIORITY, SchedulingLane.NORMAL, SchedulingLane.RESULT_REVIEW);

    private ConsultationQueueScheduler() {}

    static List<ScheduledEntry> schedule(List<QueueEntry> waiting, SchedulingLane lastServedLane,
                                         Instant firstStart, int averageMinutes) {
        Map<SchedulingLane, ArrayDeque<QueueEntry>> lanes = lanes(waiting);
        List<ScheduledEntry> result = new ArrayList<>();
        SchedulingLane cursor = lastServedLane;
        Instant projected = firstStart;
        while (lanes.values().stream().anyMatch(queue -> !queue.isEmpty())) {
            SchedulingLane selected = nextNonEmptyLane(lanes, cursor);
            QueueEntry entry = lanes.get(selected).removeFirst();
            result.add(new ScheduledEntry(entry.getId(), selected, projected));
            cursor = selected;
            projected = projected.plus(Duration.ofMinutes(averageMinutes));
        }
        return result;
    }

    static QueueEntry next(List<QueueEntry> waiting, SchedulingLane lastServedLane) {
        Map<SchedulingLane, ArrayDeque<QueueEntry>> lanes = lanes(waiting);
        if (lanes.values().stream().allMatch(Collection::isEmpty)) return null;
        return lanes.get(nextNonEmptyLane(lanes, lastServedLane)).getFirst();
    }

    private static Map<SchedulingLane, ArrayDeque<QueueEntry>> lanes(List<QueueEntry> waiting) {
        Map<SchedulingLane, ArrayDeque<QueueEntry>> lanes = new EnumMap<>(SchedulingLane.class);
        ORDER.forEach(lane -> lanes.put(lane, new ArrayDeque<>()));
        waiting.stream()
                .filter(QueueEntry::isWaitingForCall)
                .sorted(Comparator.comparing(QueueEntry::getEligibleSinceAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(QueueEntry::getSequenceNumber))
                .forEach(entry -> lanes.get(entry.getSchedulingLane()).add(entry));
        return lanes;
    }

    private static SchedulingLane nextNonEmptyLane(
            Map<SchedulingLane, ArrayDeque<QueueEntry>> lanes, SchedulingLane lastServedLane) {
        int start = lastServedLane == null ? 0 : (ORDER.indexOf(lastServedLane) + 1) % ORDER.size();
        for (int offset = 0; offset < ORDER.size(); offset++) {
            SchedulingLane lane = ORDER.get((start + offset) % ORDER.size());
            if (!lanes.get(lane).isEmpty()) return lane;
        }
        throw new IllegalStateException("No non-empty scheduling lane");
    }

    record ScheduledEntry(UUID entryId, SchedulingLane lane, Instant projectedStartAt) {}
}
