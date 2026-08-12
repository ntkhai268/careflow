package com.careflow.ai.controller;

import com.careflow.ai.dto.request.VoiceTextProcessingRequest;
import com.careflow.ai.dto.response.VoiceClinicalFieldsResponse;
import com.careflow.ai.service.AiVoiceProcessingService;
import com.careflow.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiVoiceController {

    private final AiVoiceProcessingService aiVoiceProcessingService;

    @PostMapping(value = "/voice-to-clinical-fields", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<VoiceClinicalFieldsResponse>> processVoiceToFields(
            @RequestParam("file") MultipartFile file) {
        log.info("Received voice recording for clinical fields extraction. File size: {} bytes", file.getSize());
        VoiceClinicalFieldsResponse response = aiVoiceProcessingService.processVoiceToFields(file);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/text-to-clinical-fields")
    public ResponseEntity<ApiResponse<VoiceClinicalFieldsResponse>> processTextToFields(
            @Valid @RequestBody VoiceTextProcessingRequest request) {
        log.info("Received text input for clinical fields extraction.");
        VoiceClinicalFieldsResponse response = aiVoiceProcessingService.processTextToFields(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
