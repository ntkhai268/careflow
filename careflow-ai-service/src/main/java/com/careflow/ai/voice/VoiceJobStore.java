package com.careflow.ai.voice;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.UUID;
import static com.careflow.ai.voice.VoiceTypes.*;

public class VoiceJobStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final VoiceProperties properties;
    private final com.careflow.ai.clinical.ClinicalCipher cipher;
    private final UUID instanceId=UUID.randomUUID();
    public VoiceJobStore(DataSource source,ObjectMapper mapper,VoiceProperties properties) {
        jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        this.mapper=mapper;this.properties=properties;cipher=properties.getContentKey().isBlank()?null:new com.careflow.ai.clinical.ClinicalCipher(properties.getContentKey());
    }
    private String seal(String value,UUID id,String field){return value==null?null:cipher==null?value:"ENC1:"+cipher.encrypt(value,id+":"+field);}
    private String open(String value,UUID id,String field){if(value==null)return null;if(!value.startsWith("ENC1:")){if(cipher!=null)throw new IllegalStateException("Unencrypted voice content needs controlled migration");return value;}if(cipher==null)throw new IllegalStateException("Voice content key is unavailable");return cipher.decrypt(value.substring(5),id+":"+field);}
    private JsonNode parse(String value) {
        try {return value==null?null:mapper.readTree(value);} catch(Exception e) {throw new IllegalStateException("Stored voice result is invalid");}
    }
    private Job map(ResultSet rs,int row) throws SQLException {
        return new Job(rs.getObject("id",UUID.class),rs.getObject("consultation_id",UUID.class),
                rs.getObject("actor_id",UUID.class),rs.getLong("version"),rs.getInt("transcript_revision"),
                rs.getString("state"),rs.getString("idempotency_key"),rs.getString("request_hash"),
                open(rs.getString("input_json"),rs.getObject("id",UUID.class),"input"),parse(open(rs.getString("result_json"),rs.getObject("id",UUID.class),"result")),rs.getString("error_code"),
                rs.getString("correlation_id"),rs.getObject("created_at",OffsetDateTime.class).toInstant(),
                rs.getObject("expires_at",OffsetDateTime.class).toInstant());
    }
    public Job get(UUID id) {
        var jobs=jdbc.query("SELECT * FROM voice_prescription_jobs WHERE id=?",this::map,id);
        if(jobs.isEmpty())throw new BusinessException(404,"Không tìm thấy công việc AI");
        return jobs.getFirst();
    }
    public Job find(UUID actor,String key) {
        var jobs=jdbc.query("SELECT * FROM voice_prescription_jobs WHERE actor_id=? AND idempotency_key=?",this::map,actor,key);
        return jobs.isEmpty()?null:jobs.getFirst();
    }
    public Job create(UUID consultation,Actor actor,String key,String hash,String input) {
        return tx.execute(status->{
            jdbc.queryForObject("SELECT id FROM voice_prescription_capacity WHERE id=1 FOR UPDATE",Integer.class);
            Job prior=find(actor.userId(),key);
            if(prior!=null) {
                if(!prior.requestHash().equals(hash)||!prior.consultationId().equals(consultation))throw new BusinessException(409,"Idempotency-Key đã dùng với nội dung khác");
                return prior;
            }
            expire();
            capacity(actor.userId());
            UUID id=UUID.randomUUID();Instant now=Instant.now();
            jdbc.update("INSERT INTO voice_prescription_jobs(id,consultation_id,actor_id,state,idempotency_key,request_hash,input_json,correlation_id,created_at,expires_at,lease_owner,lease_until) VALUES(?,?,?,'QUEUED',?,?,?,?,?,?,?,?)",
                id,consultation,actor.userId(),key,hash,seal(input,id,"input"),actor.correlationId(),OffsetDateTime.ofInstant(now,ZoneOffset.UTC),OffsetDateTime.ofInstant(now.plusSeconds(properties.getTtlSeconds()),ZoneOffset.UTC),instanceId,OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(30));
            audit(get(id),"CREATED");return get(id);
        });
    }
    public boolean claim(Job job) {
        return tx.execute(status->{
            int n=jdbc.update("UPDATE voice_prescription_jobs SET state='RUNNING',version=version+1 WHERE id=? AND version=? AND state='QUEUED' AND lease_owner=? AND expires_at>?",job.jobId(),job.version(),instanceId,OffsetDateTime.now(ZoneOffset.UTC));
            if(n==1)audit(get(job.jobId()),"RUNNING");return n==1;
        });
    }
    public void finish(Job job,String state,JsonNode result,String error) {
        tx.executeWithoutResult(status->{
            int n=jdbc.update("UPDATE voice_prescription_jobs SET state=?,version=version+1,result_json=?,input_json=NULL,error_code=? WHERE id=? AND version=? AND state IN ('QUEUED','RUNNING') AND expires_at>?",
                state,result==null?null:seal(result.toString(),job.jobId(),"result"),error,job.jobId(),job.version(),OffsetDateTime.now(ZoneOffset.UTC));
            if(n==1)audit(get(job.jobId()),state);
        });
    }
    public Job revise(Job job,long expected,String input) {
        return tx.execute(status->{
            jdbc.queryForObject("SELECT id FROM voice_prescription_capacity WHERE id=1 FOR UPDATE",Integer.class);
            capacity(job.actorId());
            int n=jdbc.update("UPDATE voice_prescription_jobs SET state='QUEUED',version=version+1,transcript_revision=transcript_revision+1,input_json=?,result_json=NULL,error_code=NULL,lease_owner=?,lease_until=? WHERE id=? AND version=? AND state='SUCCEEDED' AND expires_at>?",
                    seal(input,job.jobId(),"input"),instanceId,OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(30),job.jobId(),expected,OffsetDateTime.now(ZoneOffset.UTC));
            if(n!=1)throw new BusinessException(409,"Phiên bản đã thay đổi hoặc công việc không thể sửa");
            audit(get(job.jobId()),"REVISED");return get(job.jobId());
        });
    }
    public Job cancel(Job job) {
        return tx.execute(status->{
            jdbc.update("UPDATE voice_prescription_jobs SET state='CANCELLED',version=version+1,input_json=NULL,result_json=NULL,error_code=NULL WHERE id=? AND state NOT IN ('CANCELLED','EXPIRED')",job.jobId());
            audit(get(job.jobId()),"CANCELLED");return get(job.jobId());
        });
    }
    public void expire() {
        transitionAbandoned("expires_at<=? AND state NOT IN ('CANCELLED','EXPIRED')","EXPIRED","JOB_EXPIRED");
        // Retain content-free idempotency/audit metadata for 30 days only.
        OffsetDateTime cutoff=OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
        jdbc.update("DELETE FROM voice_prescription_audit WHERE created_at<?",cutoff);
        jdbc.update("DELETE FROM voice_prescription_jobs WHERE created_at<? AND state IN ('EXPIRED','CANCELLED','FAILED','SUCCEEDED')",cutoff);
    }
    public void recover() {
        // User bearer tokens are never persisted: queued/running work cannot be silently replayed after restart.
        transitionAbandoned("lease_until<=? AND state IN ('QUEUED','RUNNING')","FAILED","SERVER_RESTARTED");
        expire();
    }
    public void heartbeat() {
        jdbc.update("UPDATE voice_prescription_jobs SET lease_until=? WHERE lease_owner=? AND state IN ('QUEUED','RUNNING') AND expires_at>?",OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(30),instanceId,OffsetDateTime.now(ZoneOffset.UTC));
        recover();
    }
    private void transitionAbandoned(String condition,String state,String error) {
        tx.executeWithoutResult(status->{
            var jobs=jdbc.query("SELECT * FROM voice_prescription_jobs WHERE "+condition+" LIMIT 100",this::map,OffsetDateTime.now(ZoneOffset.UTC));
            for(Job job:jobs) {
                int n=jdbc.update("UPDATE voice_prescription_jobs SET state=?,version=version+1,input_json=NULL,result_json=NULL,error_code=? WHERE id=? AND version=? AND "+condition,
                    state,error,job.jobId(),job.version(),OffsetDateTime.now(ZoneOffset.UTC));
                if(n==1)audit(get(job.jobId()),state);
            }
        });
    }
    private void capacity(UUID actor) {
        int total=jdbc.queryForObject("SELECT COUNT(*) FROM voice_prescription_jobs WHERE state IN ('QUEUED','RUNNING')",Integer.class);
        int own=jdbc.queryForObject("SELECT COUNT(*) FROM voice_prescription_jobs WHERE actor_id=? AND state IN ('QUEUED','RUNNING')",Integer.class,actor);
        if(total>=properties.getQueueCapacity()+1||own>=properties.getPerDoctorCapacity())throw new BusinessException(429,"Hàng đợi AI đã đầy; thử lại sau");
    }
    private void audit(Job job,String action) {
        String hash=job.inputJson()!=null?VoiceRemoteClient.sha(job.inputJson()):job.result()!=null?job.result().path("transcriptHash").asText(job.requestHash()):job.requestHash();
        jdbc.update("INSERT INTO voice_prescription_audit(job_id,actor_id,action,version,correlation_id,input_hash,schema_version,catalog_version,model_info_json,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
            job.jobId(),job.actorId(),action,job.version(),job.correlationId(),hash,SCHEMA,
            job.result()==null?null:job.result().path("catalogVersion").asText(),
            job.result()==null?null:job.result().path("modelInfo").toString(),OffsetDateTime.now(ZoneOffset.UTC));
    }
}
