package com.careflow.ai.voice;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.text.Normalizer;
import java.util.*;
import static com.careflow.ai.voice.VoiceTypes.*;

public final class VoiceResultValidator {
    private VoiceResultValidator() {}
    public static void validate(JsonNode result,JsonNode request) {
        try {
            String text=result.path("transcript").asText();int length=text.codePointCount(0,text.length());
            if(!SCHEMA.equals(result.path("schemaVersion").asText())||text.isBlank()||length>12000
                    ||!Normalizer.isNormalized(text,Normalizer.Form.NFC)
                    ||!VoiceRemoteClient.sha(text).equals(result.path("transcriptHash").asText())
                    ||request.path("transcriptRevision").asInt()!=result.path("transcriptRevision").asInt()
                    ||!request.path("catalogVersion").asText().equals(result.path("catalogVersion").asText()))throw new IllegalArgumentException();
            Map<String,JsonNode> catalog=new HashMap<>();for(JsonNode m:request.path("medicines"))catalog.put(m.path("code").asText(),m);
            JsonNode items=result.path("items");if(!items.isArray()||items.size()>50)throw new IllegalArgumentException();
            for(JsonNode item:items) {
                if(item.path("medicineCode").isTextual()&&!catalog.containsKey(item.path("medicineCode").asText()))throw new IllegalArgumentException();
                if(!item.path("evidence").isObject()||!item.path("evidence").has("spokenName"))throw new IllegalArgumentException();
                if(!item.path("spokenName").isTextual()||!item.path("spokenName").equals(item.path("evidence").path("spokenName").path("text")))throw new IllegalArgumentException();
                for(JsonNode evidence:item.path("evidence")) {
                    int start=evidence.path("start").asInt(-1),end=evidence.path("end").asInt(-1);
                    if(start<0||end<=start||end>length||!"unicode_codepoint".equals(evidence.path("offsetUnit").asText())
                            ||!text.substring(text.offsetByCodePoints(0,start),text.offsetByCodePoints(0,end)).equals(evidence.path("text").asText())
                            ||!result.path("transcriptHash").equals(evidence.path("transcriptHash"))
                            ||!result.path("transcriptRevision").equals(evidence.path("transcriptRevision")))throw new IllegalArgumentException();
                }
                for(JsonNode candidate:item.path("candidates"))if(!catalog.containsKey(candidate.path("medicineCode").asText()))throw new IllegalArgumentException();
            }
            JsonNode proposal=result.path("prescriptionDraft");
            if(!proposal.isObject()||!proposal.path("items").isArray()||!proposal.path("blockers").isArray()
                    ||!proposal.path("canSaveWithoutCorrection").isBoolean())throw new IllegalArgumentException();
            if(proposal.path("canSaveWithoutCorrection").asBoolean()&&(proposal.path("items").isEmpty()||!proposal.path("blockers").isEmpty()))throw new IllegalArgumentException();
            for(JsonNode draft:result.path("prescriptionDraft").path("items")) {
                int index=draft.path("sourceItemIndex").asInt(-1);
                if(index<0||index>=items.size()||!draft.path("medicineCode").equals(items.get(index).path("medicineCode")))throw new IllegalArgumentException();
                JsonNode medicine=catalog.get(draft.path("medicineCode").asText());
                if(medicine==null||!medicine.path("name").equals(draft.path("medicineName"))||!medicine.path("unit").equals(draft.path("unit")))throw new IllegalArgumentException();
                for(String field:List.of("quantity","duration"))if(!draft.path(field).isNull()
                    &&(!draft.path(field).isIntegralNumber()||!draft.path(field).canConvertToInt()||draft.path(field).asInt()<=0))throw new IllegalArgumentException();
                if(proposal.path("canSaveWithoutCorrection").asBoolean()&&(!draft.path("unmappedFields").isEmpty()||!items.get(index).path("fieldConflicts").isEmpty()))throw new IllegalArgumentException();
            }
        } catch(Exception e){throw new BusinessException(503,"Kết quả AI không đúng contract hoặc bằng chứng");}
    }
}
