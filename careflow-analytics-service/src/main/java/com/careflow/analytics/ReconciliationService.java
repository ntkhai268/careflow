package com.careflow.analytics;
import com.careflow.common.analytics.AnalyticsSnapshot.Source;
import com.careflow.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
@Service
public class ReconciliationService {
    private final DataSource dataSource;private final JdbcTemplate jdbc;private final SourceClient client;private final SnapshotStore store;private final boolean demo,enabled;
    public ReconciliationService(DataSource ds,JdbcTemplate jdbc,SourceClient client,SnapshotStore store,@Value("${analytics.demo-enabled:false}") boolean demo,@Value("${analytics.sync-enabled:true}") boolean enabled){this.dataSource=ds;this.jdbc=jdbc;this.client=client;this.store=store;this.demo=demo;this.enabled=enabled;}
    @Scheduled(initialDelayString="${analytics.sync-initial-delay-ms:3000}",fixedDelayString="${analytics.sync-delay-ms:60000}")
    public void scheduled(){if(enabled&&!demo)try{run(null);}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("event=ANALYTICS_RECONCILE_RETRY");}}
    public List<Map<String,Object>> run(UUID actor){
        if(demo)throw new BusinessException(409,"Chế độ demo không đọc dữ liệu thật.");
        try(var lease=dataSource.getConnection();var statement=lease.createStatement()){
            boolean held;try(var result=statement.executeQuery("SELECT pg_try_advisory_lock(510022::bigint)")){result.next();held=result.getBoolean(1);}if(!held)throw new BusinessException(409,"Đang có một lượt đối soát khác.");
            try{
                long deadline=System.nanoTime()+20_000_000_000L;
                for(Source source:Source.values()){
                    if(System.nanoTime()>deadline)break;
                    var state=jdbc.queryForMap("SELECT cursor_id,cycle_rows FROM analytics.sync_state WHERE source=?",source.name());UUID cursor=(UUID)state.get("cursor_id");long rows=((Number)state.get("cycle_rows")).longValue();
                    jdbc.update("UPDATE analytics.sync_state SET status='RUNNING',started_at=COALESCE(started_at,now()),updated_at=now(),last_error_code=NULL WHERE source=?",source.name());
                    try{
                        for(int pages=0;pages<100;pages++){
                            var page=client.page(source,cursor);for(var fact:page.items())store.accept(fact,false);rows+=page.items().size();
                            UUID next=page.nextCursor();if(next!=null&&(next.equals(cursor)||page.items().isEmpty()))throw new IllegalStateException("Invalid source cursor");
                            cursor=next;
                            if(cursor==null){jdbc.update("UPDATE analytics.sync_state SET cursor_id=NULL,cycle_rows=0,started_at=NULL,last_success_at=now(),last_row_count=?,status='SYNCED',last_error_code=NULL,updated_at=now() WHERE source=?",rows,source.name());break;}
                            jdbc.update("UPDATE analytics.sync_state SET cursor_id=?,cycle_rows=?,status='PARTIAL',updated_at=now() WHERE source=?",cursor,rows,source.name());
                            if(System.nanoTime()>deadline)break;
                        }
                    }catch(RuntimeException e){jdbc.update("UPDATE analytics.sync_state SET status='DEGRADED',last_error_code='SOURCE_UNAVAILABLE',updated_at=now() WHERE source=?",source.name());}
                }
                if(actor!=null)jdbc.update("INSERT INTO analytics.operation_audit(id,actor_user_id,operation,affected_rows) VALUES (?,?,?,0)",UUID.randomUUID(),actor,"RECONCILE");
                return store.syncStates();
            }finally{statement.execute("SELECT pg_advisory_unlock(510022::bigint)");}
        }catch(SQLException e){throw new BusinessException(503,"Chưa chạy được đối soát Analytics.");}
    }
}
