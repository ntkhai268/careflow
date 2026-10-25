package com.careflow.analytics;
import com.careflow.common.analytics.AnalyticsSnapshot;
import com.careflow.common.analytics.AnalyticsSnapshot.Source;
import java.time.*;
import java.util.*;
import com.careflow.analytics.SnapshotStore.ReportRow;
/** Cohort definitions are versioned in the R5 contract; no patient-level results leave this class. */
public final class ReportCalculator {
    public record Timing(long samples,Double meanMinutes,Double p50Minutes,Double p95Minutes){}
    public record Metrics(long booked,long arrived,long scheduledArrivals,long walkIns,long completed,
            long cancelledBookings,Double cancellationPercent,long missedVisits,long missedCalls,
            long reviewQueueEntries,Timing initialWait,Timing reviewWait,Timing consultationDuration,
            long excludedWaitSamples,long excludedDurationSamples,long consultationsMissingContext){}
    private ReportCalculator(){}
    public static Metrics calculate(List<ReportRow> rows){
        long booked=0,arrived=0,scheduled=0,walk=0,cancelled=0,missedVisits=0,missedCalls=0,reviews=0,badWait=0,badDuration=0,missing=0;
        var initialWait=new ArrayList<Double>();var reviewWait=new ArrayList<Double>();var durations=new ArrayList<Double>();
        Map<String,ReportRow> visits=new LinkedHashMap<>();
        for(var row:rows){var v=row.fact();
            if(v.source()==Source.APPOINTMENT){booked++;if("CANCELLED".equals(v.status()))cancelled++;}
            if(v.source()==Source.QUEUE){
                boolean initial="INITIAL".equals(v.phase()),review="RESULT_REVIEW".equals(v.phase());
                if(initial){if(v.checkedInAt()!=null){arrived++;if(v.appointmentId()==null)walk++;else scheduled++;}if(v.missedCount()>0)missedVisits++;}
                if(review)reviews++;
                missedCalls+=v.missedCount();
                if(v.startedAt()!=null||Set.of("IN_PROGRESS","COMPLETED").contains(v.status())){
                    Instant baseline=initial&&v.missedCount()==0?v.checkedInAt():v.eligibleSinceAt();
                    Double minutes=minutes(baseline,v.startedAt());
                    if(minutes==null||(!initial&&!review))badWait++;else(initial?initialWait:reviewWait).add(minutes);
                }
            }
            if(v.source()==Source.CONSULTATION&&"COMPLETED".equals(v.status())){
                String key=v.initialEntryId()!=null?"Q:"+v.initialEntryId():v.appointmentId()!=null?"A:"+v.appointmentId():"C:"+v.id();
                visits.merge(key,row,(a,b)->a.fact().updatedAt().isAfter(b.fact().updatedAt())?a:b);
            }
        }
        for(var row:visits.values()){
            var v=row.fact();Double duration=minutes(v.startedAt(),v.completedAt());if(duration==null)badDuration++;else durations.add(duration);if(row.missingContext())missing++;
        }
        return new Metrics(booked,arrived,scheduled,walk,visits.size(),cancelled,booked==0?null:round(cancelled*100.0/booked),missedVisits,missedCalls,reviews,timing(initialWait),timing(reviewWait),timing(durations),badWait,badDuration,missing);
    }
    private static Double minutes(Instant start,Instant end){return start==null||end==null||end.isBefore(start)?null:Duration.between(start,end).toMillis()/60000.0;}
    private static Timing timing(List<Double> values){if(values.isEmpty())return new Timing(0,null,null,null);values.sort(Double::compare);return new Timing(values.size(),round(values.stream().mapToDouble(Double::doubleValue).average().orElseThrow()),percentile(values,.5),percentile(values,.95));}
    private static Double percentile(List<Double> values,double q){double position=(values.size()-1)*q;int lower=(int)Math.floor(position),upper=(int)Math.ceil(position);return round(values.get(lower)+(values.get(upper)-values.get(lower))*(position-lower));}
    private static double round(double value){return Math.round(value*100.0)/100.0;}
}
