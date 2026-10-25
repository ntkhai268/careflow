package com.careflow.analytics;
import com.careflow.common.analytics.AnalyticsSnapshot;
import com.careflow.common.analytics.AnalyticsSnapshot.Source;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
public final class DemoSnapshots {
    private DemoSnapshots(){}
    public static UUID id(LocalDate day,String name){return UUID.nameUUIDFromBytes(("careflow-r5-demo:"+day+":"+name).getBytes(StandardCharsets.UTF_8));}
    private static Instant time(LocalDate day,String time){return day.atTime(LocalTime.parse(time)).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();}
    public static List<AnalyticsSnapshot> forDay(LocalDate day){
        UUID dep=id(day,"department"),a=id(day,"a1"),q=id(day,"q1"),c=id(day,"c1");
        return List.of(
            new AnalyticsSnapshot(Source.APPOINTMENT,a,0,time(day,"10:00"),day,dep,"DEMO","DEMO-ROOM","COMPLETED",a,null,null,null,null,null,null,null,0),
            new AnalyticsSnapshot(Source.APPOINTMENT,id(day,"a2"),0,time(day,"10:00"),day,dep,"DEMO","DEMO-ROOM","CANCELLED",id(day,"a2"),null,null,null,null,null,null,null,0),
            new AnalyticsSnapshot(Source.QUEUE,q,1,time(day,"10:00"),day,dep,"DEMO","DEMO-ROOM","COMPLETED",a,q,c,"INITIAL",time(day,"08:00"),time(day,"08:00"),time(day,"08:10"),time(day,"08:20"),0),
            new AnalyticsSnapshot(Source.QUEUE,id(day,"q2"),3,time(day,"10:00"),day,dep,"DEMO","DEMO-ROOM","COMPLETED",null,id(day,"q2"),id(day,"c2"),"INITIAL",time(day,"08:05"),time(day,"08:20"),time(day,"08:25"),time(day,"08:40"),2),
            new AnalyticsSnapshot(Source.QUEUE,id(day,"review"),1,time(day,"10:00"),day,dep,"DEMO","DEMO-ROOM","COMPLETED",a,null,c,"RESULT_REVIEW",null,time(day,"08:30"),time(day,"08:35"),time(day,"08:45"),0),
            new AnalyticsSnapshot(Source.CONSULTATION,c,2,time(day,"10:00"),day,dep,"DEMO","DEMO-ROOM","COMPLETED",a,q,c,null,null,null,time(day,"08:10"),time(day,"08:50"),0),
            new AnalyticsSnapshot(Source.CONSULTATION,id(day,"c2"),2,time(day,"10:00"),day,dep,"DEMO","DEMO-ROOM","COMPLETED",null,id(day,"q2"),id(day,"c2"),null,null,null,time(day,"08:25"),time(day,"08:45"),0));
    }
}
