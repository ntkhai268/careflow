package com.careflow.ai.service;

import com.careflow.ai.dto.request.VoiceTextProcessingRequest;
import com.careflow.ai.dto.response.VoiceClinicalFieldsResponse;
import com.careflow.ai.dto.response.VoiceExtractedFields;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiVoiceProcessingService {

    private final ElevenLabsClientService elevenLabsClientService;
    private final GeminiClientService geminiClientService;

    /**
     * Process audio file: ElevenLabs STT -> Gemini JSON extraction -> VoiceClinicalFieldsResponse
     */
    public VoiceClinicalFieldsResponse processVoiceToFields(MultipartFile audioFile) {
        log.info("Processing audio recording for clinical field extraction...");
        String transcript = elevenLabsClientService.transcribeAudio(audioFile);
        log.info("Transcript obtained: '{}'", transcript);

        VoiceExtractedFields extractedFields = geminiClientService.extractClinicalFieldsFromTranscript(transcript);

        return VoiceClinicalFieldsResponse.builder()
                .transcriptText(transcript)
                .extractedFields(extractedFields)
                .build();
    }

    /**
     * Process direct text: Gemini JSON extraction -> VoiceClinicalFieldsResponse (useful for testing text input)
     */
    public VoiceClinicalFieldsResponse processTextToFields(VoiceTextProcessingRequest request) {
        log.info("Processing direct text input for clinical field extraction...");
        String transcript = request.getTranscriptText();

        VoiceExtractedFields extractedFields = geminiClientService.extractClinicalFieldsFromTranscript(transcript);

        return VoiceClinicalFieldsResponse.builder()
                .transcriptText(transcript)
                .extractedFields(extractedFields)
                .build();
    }
}
