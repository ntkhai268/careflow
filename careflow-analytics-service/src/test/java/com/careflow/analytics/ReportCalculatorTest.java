package com.careflow.analytics;
import com.careflow.common.analytics.AnalyticsSnapshot;
import com.careflow.common.analytics.AnalyticsSnapshot.Source;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class ReportCalculatorTest {
    static final LocalDate DAY=LocalDate.of(2026,10,21);
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    AnalyticsSnapshot changed(AnalyticsSnapshot v,String key,Object value){ObjectNode node=json.valueToTree(v);node.set(key,json.valueToTree(value));try{return json.treeToValue(node,AnalyticsSnapshot.class);}catch(Exception e){throw new RuntimeException(e);}}
    SnapshotStore.ReportRow row(AnalyticsSnapshot v){return new SnapshotStore.ReportRow(v,v.businessDate(),v.departmentId(),v.departmentCode(),v.roomId(),false);}
    @Test void knownDatasetCountsVisitsOnceAndUsesRequeueBaseline(){
        var m=ReportCalculator.calculate(DemoSnapshots.forDay(DAY).stream().map(this::row).toList());
        assertThat(m.booked()).isEqualTo(2);assertThat(m.cancelledBookings()).isEqualTo(1);assertThat(m.cancellationPercent()).isEqualTo(50);
        assertThat(m.arrived()).isEqualTo(2);assertThat(m.scheduledArrivals()).isEqualTo(1);assertThat(m.walkIns()).isEqualTo(1);assertThat(m.completed()).isEqualTo(2);
        assertThat(m.missedVisits()).isEqualTo(1);assertThat(m.missedCalls()).isEqualTo(2);assertThat(m.reviewQueueEntries()).isEqualTo(1);
        assertThat(m.initialWait().samples()).isEqualTo(2);assertThat(m.initialWait().meanMinutes()).isEqualTo(7.5);assertThat(m.initialWait().p50Minutes()).isEqualTo(7.5);assertThat(m.initialWait().p95Minutes()).isEqualTo(9.75);
        assertThat(m.reviewWait().meanMinutes()).isEqualTo(5);assertThat(m.consultationDuration().meanMinutes()).isEqualTo(30);
    }
    @Test void emptyDenominatorsAreNullRatherThanInventedZeroMinutes(){var m=ReportCalculator.calculate(List.of());assertThat(m.booked()).isZero();assertThat(m.cancellationPercent()).isNull();assertThat(m.initialWait().meanMinutes()).isNull();assertThat(m.consultationDuration().samples()).isZero();}
    @Test void futureWaitingTicketDoesNotCountAsArrival(){var q=DemoSnapshots.forDay(DAY).get(2);q=changed(q,"checkedInAt",null);q=changed(q,"startedAt",null);q=changed(q,"status","WAITING");var m=ReportCalculator.calculate(List.of(row(q)));assertThat(m.arrived()).isZero();assertThat(m.initialWait().samples()).isZero();}
    @Test void missingAndNegativeTimestampsAreExcludedWithExplicitCounts(){var fixtures=DemoSnapshots.forDay(DAY);var q=changed(fixtures.get(2),"checkedInAt",fixtures.get(2).startedAt().plusSeconds(60));var c=changed(fixtures.get(5),"completedAt",fixtures.get(5).startedAt().minusSeconds(60));var m=ReportCalculator.calculate(List.of(row(q),row(c)));assertThat(m.initialWait().samples()).isZero();assertThat(m.excludedWaitSamples()).isEqualTo(1);assertThat(m.completed()).isEqualTo(1);assertThat(m.excludedDurationSamples()).isEqualTo(1);}
    @Test void duplicateLegacyConsultationsForOneVisitCountOnlyLatestCompletion(){var c=DemoSnapshots.forDay(DAY).get(5);var copy=changed(c,"id",UUID.randomUUID());var m=ReportCalculator.calculate(List.of(row(c),row(copy)));assertThat(m.completed()).isEqualTo(1);assertThat(m.consultationDuration().samples()).isEqualTo(1);}
    @Test void aReviewQueueDoesNotCountAsNewWalkInOrCompletedVisit(){var review=DemoSnapshots.forDay(DAY).get(4);var m=ReportCalculator.calculate(List.of(row(review)));assertThat(m.arrived()).isZero();assertThat(m.walkIns()).isZero();assertThat(m.completed()).isZero();assertThat(m.reviewQueueEntries()).isEqualTo(1);}
    @Test void demoSeedIdsAreStableWithinDayAndDifferentNextDay(){assertThat(DemoSnapshots.forDay(DAY)).isEqualTo(DemoSnapshots.forDay(DAY));assertThat(DemoSnapshots.id(DAY,"q1")).isNotEqualTo(DemoSnapshots.id(DAY.plusDays(1),"q1"));}
}
