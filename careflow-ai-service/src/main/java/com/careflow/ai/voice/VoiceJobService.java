package com.careflow.ai.voice;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.scheduling.annotation.Scheduled;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static com.careflow.ai.voice.VoiceTypes.*;

public class VoiceJobService {
    public static final int MAX_AUDIO=20*1024*1024;
    private static final Set<String> MIME=Set.of("audio/wav","audio/x-wav","audio/webm","audio/ogg","audio/mp4","audio/mpeg","video/webm","audio/flac");
    private final VoiceJobStore store;
    private final VoiceDependencies dependencies;
    private final ObjectMapper mapper;
    private final ThreadPoolExecutor executor;
    private final Set<String> scheduled=ConcurrentHashMap.newKeySet();
    public VoiceJobService(VoiceJobStore store,VoiceDependencies dependencies,ObjectMapper mapper,VoiceProperties p) {
        this.store=store;this.dependencies=dependencies;this.mapper=mapper;
        executor=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(p.getQueueCapacity()),
                task->{Thread t=new Thread(task,"voice-prescription-jobs");t.setDaemon(true);return t;});
    }
    private String transcript(String text) {
        if(text==null)throw new BusinessException(400,"Thiếu transcript");
        text=Normalizer.normalize(text.strip(),Normalizer.Form.NFC);
        if(text.isBlank()||text.codePointCount(0,text.length())>12000)throw new BusinessException(400,"Transcript cần có 1–12.000 ký tự");
        return text;
    }
    public Job createText(TextRequest input,String key,HttpServletRequest request) {
        Actor actor=dependencies.authenticate(request);
        return create(input.consultationId(),actor,key,mapper.createObjectNode().put("transcript",transcript(input.transcript())));
    }
    public Job createAudio(UUID consultation,byte[] audio,String mime,String key,HttpServletRequest request) {
        Actor actor=dependencies.authenticate(request);
        if(audio==null||audio.length==0)throw new BusinessException(400,"Audio trống");
        if(audio.length>MAX_AUDIO)throw new BusinessException(413,"Audio vượt 20 MiB");
        mime=mime==null?"":mime.split(";",2)[0].strip().toLowerCase(Locale.ROOT);
        if(!MIME.contains(mime))throw new BusinessException(415,"Định dạng audio chưa được hỗ trợ");
        return create(consultation,actor,key,mapper.createObjectNode().put("audioBase64",Base64.getEncoder().encodeToString(audio)).put("audioMimeType",mime));
    }
    private Job create(UUID consultation,Actor actor,String key,ObjectNode input) {
        if(key==null||!key.matches("[A-Za-z0-9._:-]{1,128}"))throw new BusinessException(400,"Cần Idempotency-Key hợp lệ");
        dependencies.authorize(actor,consultation);
        String hash=VoiceRemoteClient.sha(consultation+"\n"+input);
        Job job=store.create(consultation,actor,key,hash,input.toString());
        if("QUEUED".equals(job.state()))enqueue(job,actor);
        return store.get(job.jobId());
    }
    private Job owned(UUID id,Actor actor) {
        store.expire();Job job=store.get(id);
        if(!job.actorId().equals(actor.userId()))throw new BusinessException(404,"Không tìm thấy công việc AI");
        dependencies.authorize(actor,job.consultationId());return job;
    }
    public Job get(UUID id,HttpServletRequest request){return owned(id,dependencies.authenticate(request));}
    public Job cancel(UUID id,HttpServletRequest request){Actor a=dependencies.authenticate(request);return store.cancel(owned(id,a));}
    public Job revise(UUID id,RevisionRequest revision,HttpServletRequest request) {
        Actor actor=dependencies.authenticate(request);Job job=owned(id,actor);
        if(!"SUCCEEDED".equals(job.state())||job.result()==null||job.version()!=revision.expectedVersion())throw new BusinessException(409,"Công việc đã thay đổi hoặc chưa thể sửa");
        String text=transcript(revision.transcript());
        if(revision.append())text=transcript(job.result().path("transcript").asText()+"; "+text);
        Job next=store.revise(job,revision.expectedVersion(),mapper.createObjectNode().put("transcript",text).toString());
        enqueue(next,actor);return store.get(id);
    }
    private void enqueue(Job job,Actor actor) {
        String identity=job.jobId()+":"+job.version();
        if(!scheduled.add(identity))return;
        try {
            executor.execute(()->{
                try {
                    if(!store.claim(job))return;
                    Job running=store.get(job.jobId());
                    try {
                        dependencies.authorize(actor,job.consultationId());
                        ObjectNode input=(ObjectNode)mapper.readTree(job.inputJson());
                        input.put("schemaVersion",SCHEMA).put("transcriptRevision",job.transcriptRevision());
                        input.setAll(dependencies.catalog(actor));
                        var result=dependencies.infer(input);
                        VoiceResultValidator.validate(result,input);
                        dependencies.authorize(actor,job.consultationId());
                        if(Instant.now().isBefore(running.expiresAt()))store.finish(running,"SUCCEEDED",result,null);
                        else store.expire();
                    } catch(BusinessException e) {
                        String code=e.getStatus()==401||e.getStatus()==403||e.getStatus()==409?"ACCESS_CHANGED":e.getStatus()==429?"WORKER_BUSY":e.getStatus()==422?"INVALID_AUDIO_OR_TEXT":"DEPENDENCY_UNAVAILABLE";
                        store.finish(running,"FAILED",null,code);
                    } catch(Exception e) {store.finish(running,"FAILED",null,"INFERENCE_FAILED");}
                } finally {scheduled.remove(identity);}
            });
        } catch(RejectedExecutionException e) {
            scheduled.remove(identity);store.finish(job,"FAILED",null,"QUEUE_FULL");
            throw new BusinessException(429,"Hàng đợi AI đã đầy");
        }
    }
    @Scheduled(fixedDelay=60000) public void expire(){store.expire();}
    @Scheduled(fixedDelay=10000) public void heartbeat(){store.heartbeat();}
    @PreDestroy public void close(){executor.shutdownNow();}
}
