package com.careflow.analytics;
import com.careflow.common.analytics.AnalyticsSnapshot;
import com.careflow.common.analytics.AnalyticsSnapshot.Source;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
@Repository
public class SnapshotStore {
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public SnapshotStore(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public void lock(){jdbc.queryForObject("SELECT pg_advisory_xact_lock(510021::bigint)",Object.class);}
    public AnalyticsSnapshot decode(String value){try{return json.readValue(value,AnalyticsSnapshot.class);}catch(Exception e){throw new IllegalStateException("Invalid stored analytics snapshot",e);}}
    public String encode(AnalyticsSnapshot value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Invalid analytics snapshot",e);}}
    public boolean processed(Source source,UUID event){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM analytics.event_receipts WHERE source=? AND event_id=?)",Boolean.class,source.name(),event));}
    public void receipt(Source source,UUID event,UUID id,String outcome){jdbc.update("INSERT INTO analytics.event_receipts(source,event_id,source_id,outcome) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",source.name(),event,id,outcome);}
    @Transactional
    public String accept(AnalyticsSnapshot value,boolean synthetic){
        lock();String body=encode(value),fingerprint;
        try{fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
        var old=jdbc.query("SELECT payload::text FROM analytics.source_projection WHERE source=? AND source_id=? AND synthetic=?",(rs,row)->decode(rs.getString(1)),value.source().name(),value.id(),synthetic);
        String outcome="APPLIED";
        if(!old.isEmpty()){
            var prior=old.getFirst();
            int order=value.source()==Source.APPOINTMENT?value.updatedAt().compareTo(prior.updatedAt()):Long.compare(value.revision(),prior.revision());
            if(order==0)order=value.updatedAt().compareTo(prior.updatedAt());
            if(order<0)outcome="STALE_SNAPSHOT";
            else if(order==0)outcome=body.equals(encode(prior))?"UNCHANGED":"SNAPSHOT_CONFLICT";
        }
        var ids=jdbc.query("INSERT INTO analytics.snapshot_history(source,source_id,synthetic,fingerprint,payload,accepted) VALUES (?,?,?,?,?::jsonb,?) ON CONFLICT DO NOTHING RETURNING id",(rs,row)->rs.getLong(1),value.source().name(),value.id(),synthetic,fingerprint,body,"APPLIED".equals(outcome));
        if("APPLIED".equals(outcome)){
            long id=ids.isEmpty()?jdbc.queryForObject("SELECT id FROM analytics.snapshot_history WHERE source=? AND source_id=? AND synthetic=? AND fingerprint=?",Long.class,value.source().name(),value.id(),synthetic,fingerprint):ids.getFirst();
            project(value,synthetic,id,body);
        }
        return outcome;
    }
    private void project(AnalyticsSnapshot v,boolean demo,long history,String body){
        jdbc.update("""
          INSERT INTO analytics.source_projection(source,source_id,synthetic,history_id,payload,business_date,department_id,department_code,room_id,appointment_id,initial_entry_id,consultation_id,source_version,source_updated_at)
          VALUES (?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?,?)
          ON CONFLICT(source,source_id,synthetic) DO UPDATE SET history_id=excluded.history_id,payload=excluded.payload,business_date=excluded.business_date,department_id=excluded.department_id,department_code=excluded.department_code,room_id=excluded.room_id,appointment_id=excluded.appointment_id,initial_entry_id=excluded.initial_entry_id,consultation_id=excluded.consultation_id,source_version=excluded.source_version,source_updated_at=excluded.source_updated_at
          """,v.source().name(),v.id(),demo,history,body,v.businessDate(),v.departmentId(),v.departmentCode(),v.roomId(),v.appointmentId(),v.initialEntryId(),v.consultationId(),v.revision(),Timestamp.from(v.updatedAt()));
    }
    public record ReportRow(AnalyticsSnapshot fact,LocalDate date,UUID departmentId,String departmentCode,String roomId,boolean missingContext){}
    @Transactional(readOnly=true)
    public List<ReportRow> reportRows(LocalDate from,LocalDate to,UUID department,String room,boolean demo){
        String date="COALESCE(q.business_date,a.business_date,p.business_date)",dep="COALESCE(q.department_id,a.department_id,p.department_id)",code="COALESCE(q.department_code,a.department_code,p.department_code)",r="COALESCE(q.room_id,a.room_id,p.room_id)";
        String sql="SELECT p.payload::text,"+date+" AS cohort,"+dep+" AS dept,"+code+" AS dept_code,"+r+" AS room,(p.source='CONSULTATION' AND q.source_id IS NULL AND a.source_id IS NULL) AS missing_context FROM analytics.source_projection p LEFT JOIN analytics.source_projection q ON p.source='CONSULTATION' AND q.source='QUEUE' AND q.source_id=p.initial_entry_id AND q.synthetic=p.synthetic LEFT JOIN analytics.source_projection a ON p.source='CONSULTATION' AND a.source='APPOINTMENT' AND a.source_id=p.appointment_id AND a.synthetic=p.synthetic WHERE p.synthetic=? AND "+date+" BETWEEN ? AND ?";
        List<Object> args=new ArrayList<>(List.of(demo,from,to));
        if(department!=null){sql+=" AND "+dep+"=?";args.add(department);}
        if(room!=null){sql+=" AND "+r+"=?";args.add(room);}
        sql+=" ORDER BY cohort,p.source,p.source_id LIMIT 100001";
        var rows=jdbc.query(sql,(rs,n)->new ReportRow(decode(rs.getString(1)),rs.getObject("cohort",LocalDate.class),rs.getObject("dept",UUID.class),rs.getString("dept_code"),rs.getString("room"),rs.getBoolean("missing_context")),args.toArray());
        if(rows.size()>100000)throw new com.careflow.common.exception.BusinessException(422,"Phạm vi có quá nhiều lượt. Hãy chọn khoảng ngày ngắn hơn.");return rows;
    }
    @Transactional
    public long rebuild(boolean demo,UUID actor){
        lock();jdbc.update("DELETE FROM analytics.source_projection WHERE synthetic=?",demo);
        var history=jdbc.queryForList("SELECT DISTINCT ON(source,source_id) id,payload::text AS payload FROM analytics.snapshot_history WHERE synthetic=? AND accepted=true ORDER BY source,source_id,id DESC",demo);
        for(var row:history){String body=(String)row.get("payload");project(decode(body),demo,((Number)row.get("id")).longValue(),body);}
        jdbc.update("INSERT INTO analytics.operation_audit(id,actor_user_id,operation,affected_rows) VALUES (?,?,?,?)",UUID.randomUUID(),actor,"REBUILD",history.size());return history.size();
    }
    public List<Map<String,Object>> syncStates(){return jdbc.queryForList("SELECT source,status,started_at,last_success_at,last_row_count,last_error_code,updated_at FROM analytics.sync_state ORDER BY source");}
    public Map<String,Object> metadata(boolean demo){
        var info=jdbc.queryForMap("SELECT min(observed_at) AS first_observed_at,max(observed_at) AS last_observed_at,count(*) FILTER(WHERE accepted=false) AS rejected_snapshots FROM analytics.snapshot_history WHERE synthetic=?",demo);
        if(!demo)info.putAll(jdbc.queryForMap("SELECT max(received_at) AS last_event_received_at,count(*) FILTER(WHERE outcome='SOURCE_RECORD_MISSING') AS missing_source_events FROM analytics.event_receipts"));
        info.put("dataset",demo?"SYNTHETIC_DEMO":"REAL");info.put("sources",syncStates());info.put("coverageNote","Chỉ gồm bản ghi nguồn hiện còn hoặc snapshot đã được Analytics quan sát; không phục hồi lịch sử bị nguồn xóa trước đó.");return info;
    }
}
