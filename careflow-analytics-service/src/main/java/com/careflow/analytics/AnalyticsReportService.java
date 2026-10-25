package com.careflow.analytics;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import com.careflow.common.exception.BusinessException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
@Service
public class AnalyticsReportService {
    private final SnapshotStore store;private final boolean demo;
    public AnalyticsReportService(SnapshotStore store,@Value("${analytics.demo-enabled:false}") boolean demo){this.store=store;this.demo=demo;}
    public record Daily(LocalDate date,ReportCalculator.Metrics metrics){}
    public record Group(UUID departmentId,String departmentCode,String roomId,ReportCalculator.Metrics metrics){}
    public record Report(LocalDate from,LocalDate to,String timezone,String definitionVersion,ReportCalculator.Metrics summary,List<Daily> daily,List<Group> groups,Map<String,Object> metadata){}
    private record GroupKey(UUID id,String code,String room){}
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Report report(LocalDate from,LocalDate to,UUID department,String room){
        if(from==null||to==null||to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>92)throw new BusinessException(400,"Khoảng ngày phải từ1 đến93 ngày.");
        if(room!=null&&!room.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,79}"))throw new BusinessException(400,"Mã phòng không hợp lệ.");
        var rows=store.reportRows(from,to,department,room,demo);var daily=new ArrayList<Daily>();
        for(LocalDate date=from;!date.isAfter(to);date=date.plusDays(1)){final LocalDate day=date;daily.add(new Daily(day,ReportCalculator.calculate(rows.stream().filter(r->r.date().equals(day)).toList())));}
        Map<UUID,String> departmentCodes=new HashMap<>();for(var row:rows)if(row.departmentId()!=null&&row.departmentCode()!=null)departmentCodes.putIfAbsent(row.departmentId(),row.departmentCode());
        Map<GroupKey,List<SnapshotStore.ReportRow>> grouped=new LinkedHashMap<>();for(var row:rows)grouped.computeIfAbsent(new GroupKey(row.departmentId(),row.departmentId()==null?row.departmentCode():departmentCodes.get(row.departmentId()),row.roomId()),k->new ArrayList<>()).add(row);
        var groups=grouped.entrySet().stream().map(e->new Group(e.getKey().id(),e.getKey().code(),e.getKey().room(),ReportCalculator.calculate(e.getValue()))).toList();
        var metadata=store.metadata(demo);metadata.put("generatedAt",Instant.now());metadata.put("recordCount",rows.size());metadata.put("periodBasis","APPOINTMENT_OR_INITIAL_VISIT_DATE");
        return new Report(from,to,"Asia/Ho_Chi_Minh","R5_V1",ReportCalculator.calculate(rows),daily,groups,metadata);
    }
}
