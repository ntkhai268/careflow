package com.careflow.ai.voice;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.UUID;
import java.util.Set;
import static com.careflow.ai.voice.VoiceTypes.*;

@RestController
@RequestMapping("/api/ai/voice-prescription/jobs")
@ConditionalOnProperty(name="careflow.voice.enabled",havingValue="true")
public class VoicePrescriptionController {
    private final VoiceJobService service;
    public VoicePrescriptionController(VoiceJobService service){this.service=service;}
    private ResponseEntity<ApiResponse<JobResponse>> response(Job job,int status) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(
            ApiResponse.<JobResponse>builder().status(status).message("AI proposal; prescription has not been saved or signed")
                .data(JobResponse.of(job)).build());
    }
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<JobResponse>> text(@RequestBody ObjectNode body,
            @RequestHeader("Idempotency-Key") String key,HttpServletRequest request) {
        body.fieldNames().forEachRemaining(field->{if(!Set.of("consultationId","transcript").contains(field))throw new IllegalArgumentException();});
        if(!body.path("transcript").isTextual())throw new IllegalArgumentException();
        TextRequest input=new TextRequest(UUID.fromString(body.path("consultationId").asText()),body.path("transcript").asText());
        return response(service.createText(input,key,request),202);
    }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<JobResponse>> audio(@RequestParam UUID consultationId,@RequestPart("audio") MultipartFile audio,
            @RequestHeader("Idempotency-Key") String key,HttpServletRequest request) throws IOException {
        if(audio.getSize()>VoiceJobService.MAX_AUDIO)throw new BusinessException(413,"Audio vượt 20 MiB");
        return response(service.createAudio(consultationId,audio.getBytes(),audio.getContentType(),key,request),202);
    }
    @GetMapping("/{id}") public ResponseEntity<ApiResponse<JobResponse>> get(@PathVariable UUID id,HttpServletRequest request){return response(service.get(id,request),200);}
    @PostMapping("/{id}/revisions") public ResponseEntity<ApiResponse<JobResponse>> revise(@PathVariable UUID id,
            @RequestBody ObjectNode body,HttpServletRequest request){
        body.fieldNames().forEachRemaining(field->{if(!Set.of("expectedVersion","transcript","append").contains(field))throw new IllegalArgumentException();});
        if(!body.path("expectedVersion").canConvertToLong()||body.path("expectedVersion").asLong()<1||!body.path("transcript").isTextual()
                ||(body.has("append")&&!body.path("append").isBoolean()))throw new IllegalArgumentException();
        return response(service.revise(id,new RevisionRequest(body.path("expectedVersion").asLong(),body.path("transcript").asText(),body.path("append").asBoolean(false)),request),202);
    }
    @DeleteMapping("/{id}") public ResponseEntity<ApiResponse<JobResponse>> cancel(@PathVariable UUID id,HttpServletRequest request){return response(service.cancel(id,request),200);}
}
