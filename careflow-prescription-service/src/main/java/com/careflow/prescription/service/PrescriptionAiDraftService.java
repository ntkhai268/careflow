package com.careflow.prescription.service;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.jdbc.core.JdbcTemplate;import com.careflow.common.exception.BusinessException;import com.careflow.prescription.config.*;import com.careflow.prescription.dto.request.*;import com.careflow.prescription.dto.response.*;import com.careflow.prescription.repository.*;import com.fasterxml.jackson.databind.*;import java.time.*;import java.util.*;import java.security.*;import java.nio.charset.StandardCharsets;
@Service
public class PrescriptionAiDraftService {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private final JdbcTemplate jdbc;private final PrescriptionAiClient ai;private final ConsultationClient consultations;private final PrescriptionService prescriptions;private final DrugRepository drugs;
    public PrescriptionAiDraftService(JdbcTemplate jdbc,PrescriptionAiClient ai,ConsultationClient consultations,PrescriptionService prescriptions,DrugRepository drugs){this.jdbc=jdbc;this.ai=ai;this.consultations=consultations;this.prescriptions=prescriptions;this.drugs=drugs;}
    @Transactional
    public PrescriptionResponse save(UUID job,int revision,UUID actor,String role){
        return saveSource(job,revision,actor,role,false,null,false);
    }
    @Transactional
    public PrescriptionResponse saveEditable(UUID job,int revision,UUID actor,String role){
        return saveSource(job,revision,actor,role,true,null,false);
    }
    @Transactional
    public PrescriptionResponse appendEditable(UUID job,int revision,UUID actor,String role,UUID prescription){
        if(prescription==null)throw new BusinessException(400,"Thiếu toa nháp cần ghi thêm");
        return saveSource(job,revision,actor,role,true,prescription,false);
    }
    @Transactional
    public PrescriptionResponse replaceEditable(UUID job,int revision,UUID actor,String role,UUID prescription){
        if(prescription==null)throw new BusinessException(400,"Thiếu toa nháp cần thay thuốc");
        return saveSource(job,revision,actor,role,true,prescription,true);
    }
    private PrescriptionResponse saveSource(UUID job,int revision,UUID actor,String role,boolean editable,UUID prescription,boolean replaceItems){
        if(actor==null||!"DOCTOR".equals(role))throw new BusinessException(403,"Cần bác sĩ phụ trách ca khám");if(revision<1)throw new BusinessException(400,"Phiên bản transcript không hợp lệ");
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"AI_DRAFT:"+job+":"+revision);
        var prior=jdbc.queryForList("SELECT prescription_id,actor_id FROM prescription_ai_sources WHERE job_id=? AND transcript_revision=?",job,revision);
        if(!prior.isEmpty()){if(!actor.equals(prior.getFirst().get("actor_id")))throw new BusinessException(404,"Không tìm thấy toa nguồn AI");if(prescription!=null&&!prescription.equals(prior.getFirst().get("prescription_id")))throw new BusinessException(409,"Đoạn ghi âm đã được lưu vào một toa khác");return prescriptions.getPrescription((UUID)prior.getFirst().get("prescription_id"),actor,role);}
        JsonNode data=ai.job(job);if(!job.toString().equals(data.path("jobId").asText())||revision!=data.path("transcriptRevision").asInt()||!"SUCCEEDED".equals(data.path("state").asText()))throw new BusinessException(409,"Đề xuất AI chưa hoàn tất hoặc đã thay đổi phiên bản");
        try{if(!Instant.parse(data.path("expiresAt").asText()).isAfter(Instant.now()))throw new IllegalArgumentException();}catch(Exception e){throw new BusinessException(409,"Đề xuất AI đã hết hạn");}
        UUID consultation;try{consultation=UUID.fromString(data.path("consultationId").asText());}catch(Exception e){throw new BusinessException(503,"Đề xuất thiếu ca khám hợp lệ");}
        var context=consultations.getConsultation(consultation);if(context==null||!consultation.equals(context.id())||!actor.equals(context.doctorId())||context.patientId()==null)throw new BusinessException(403,"Ca khám không thuộc bác sĩ");
        var result=data.path("result");var sourceItems=result.path("items");var proposal=result.path("prescriptionDraft").path("items");
        if(!"careflow.voice-prescription.v1".equals(result.path("schemaVersion").asText())||!sourceItems.isArray()||sourceItems.isEmpty()||sourceItems.size()>50)throw new BusinessException(422,"Chưa nhận được tên thuốc. Hãy bổ sung tên thuốc trong transcript rồi lưu lại.");
        var items=new ArrayList<PrescriptionItemRequest>();var codes=new HashSet<String>();
        if(editable){items.addAll(editableItems(sourceItems,proposal));}else{
        if(!proposal.isArray()||proposal.isEmpty()||proposal.size()>50||proposal.size()!=sourceItems.size())throw new BusinessException(422,"AI chưa xác định đủ thuốc. Hãy sửa transcript rồi thử lưu lại");
        for(JsonNode item:proposal){int index=item.path("sourceItemIndex").asInt(-1);if(index<0||index>=sourceItems.size())throw new BusinessException(503,"Kết quả AI không có nguồn hợp lệ");var original=sourceItems.get(index);String code=item.path("medicineCode").asText();if(!code.equals(original.path("medicineCode").asText())||!original.path("fieldConflicts").isEmpty()||!codes.add(code))throw new BusinessException(422,"Thuốc hoặc trường đọc được đang mâu thuẫn. Hãy sửa transcript");
            var drug=drugs.findByCodeAndActiveTrue(code).orElseThrow(()->new BusinessException(422,"Thuốc không còn trong danh mục được phép kê"));
            if(!drug.getName().equals(item.path("medicineName").asText()))throw new BusinessException(409,"Danh mục thuốc đã thay đổi, hãy xử lý lại đề xuất");
            Map<String,String> details=new LinkedHashMap<>();var fields=item.path("unmappedFields");fields.fields().forEachRemaining(e->{if(!PrescriptionDetails.FIELDS.contains(e.getKey()))throw new BusinessException(422,"Có trường AI chưa ánh xạ được, cần chỉnh transcript");if(!e.getValue().isNull())details.put(e.getKey(),e.getValue().asText());});
            items.add(PrescriptionItemRequest.builder().medicineCode(code).medicineName(drug.getName()).unit(drug.getUnit()).dosage(text(item,"dosage")).frequency(text(item,"frequency")).timing(text(item,"timing")).duration(integer(item,"duration")).quantity(integer(item,"quantity")).notes(text(item,"notes")).details(PrescriptionDetails.validate(details)).build());
        }
        }
        PrescriptionResponse saved;
        if(prescription==null){
            var request=CreatePrescriptionRequest.builder().consultationId(consultation).patientId(context.patientId()).doctorId(actor).items(items).build();
            saved=prescriptions.createPrescription(request,actor,role);
        }else{
            var locked=entityManager.find(com.careflow.prescription.model.Prescription.class,prescription,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if(locked==null)throw new BusinessException(404,"Không tìm thấy toa nháp");
            var current=prescriptions.getPrescription(prescription,actor,role);
            if(!consultation.equals(current.getConsultationId())||!context.patientId().equals(current.getPatientId()))throw new BusinessException(403,"Đoạn ghi âm không thuộc ca khám của toa này");
            if(current.getStatus()!=com.careflow.prescription.model.PrescriptionStatus.DRAFT)throw new BusinessException(409,"Chỉ ghi thêm vào toa đang ở dạng nháp");
            if((replaceItems?0:current.getItems().size())+items.size()>50)throw new BusinessException(422,"Toa nháp tối đa50 dòng thuốc. Kiểm tra toa trước khi ghi thêm.");
            var combined=new ArrayList<PrescriptionItemRequest>();
            if(!replaceItems)for(var item:current.getItems())combined.add(PrescriptionItemRequest.builder().medicineCode(item.getMedicineCode()).medicineName(item.getMedicineName()).unit(item.getUnit()).dosage(item.getDosage()).frequency(item.getFrequency()).timing(item.getTiming()).duration(item.getDuration()).quantity(item.getQuantity()).notes(item.getNotes()).details(item.getDetails()).build());
            combined.addAll(items);
            var request=CreatePrescriptionRequest.builder().consultationId(consultation).patientId(current.getPatientId()).doctorId(actor).diagnosis(current.getDiagnosis()).notes(current.getNotes()).followUpDate(current.getFollowUpDate()).followUpDepartment(current.getFollowUpDepartment()).followUpTimeSlot(current.getFollowUpTimeSlot()).items(combined).build();
            saved=prescriptions.updatePrescription(prescription,request,actor,role);
        }
        entityManager.flush();
        String hash;try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(result.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
        jdbc.update("INSERT INTO prescription_ai_sources(job_id,transcript_revision,prescription_id,consultation_id,actor_id,result_hash) VALUES (?,?,?,?,?,?)",job,revision,saved.getId(),consultation,actor,hash);return saved;
    }
    private List<PrescriptionItemRequest> editableItems(JsonNode sources,JsonNode proposal){
        var items=new ArrayList<PrescriptionItemRequest>();
        for(int index=0;index<sources.size();index++){
            var source=sources.get(index);String name=field(source,"spokenName",255);
            if(name==null||name.isBlank())throw new BusinessException(422,"Dòng thuốc "+(index+1)+" chưa có tên. Bổ sung tên trong transcript rồi lưu lại.");
            String code=field(source,"medicineCode",50);var drug=code==null?null:drugs.findByCodeAndActiveTrue(code).orElse(null);
            var conflicts=source.path("fieldConflicts");Map<String,String> details=new LinkedHashMap<>();
            for(String key:List.of("strength","dosageForm","route","minInterval","maxDailyDose")){var value=field(source,key,200);if(value!=null&&!conflicts.has(key))details.put(key,value);}
            Integer duration=null;
            if(!conflicts.has("duration")){
                for(var row:proposal)if(row.path("sourceItemIndex").asInt(-1)==index)duration=editableInteger(row,"duration");
                if(duration==null){String raw=field(source,"duration",100);if(raw!=null&&raw.matches("(?iu)(?:dùng\\s+)?(?:trong\\s+)?[1-9][0-9]{0,3}\\s*ngày"))duration=Integer.valueOf(raw.replaceAll("\\D",""));}
            }
            items.add(PrescriptionItemRequest.builder().medicineCode(drug==null?null:drug.getCode()).medicineName(drug==null?name:drug.getName()).unit(drug==null?Objects.requireNonNullElse(field(source,"quantityUnit",50),""):drug.getUnit())
                .dosage(conflicts.has("dose")?null:field(source,"dose",100)).frequency(conflicts.has("frequency")?null:field(source,"frequency",100)).timing(conflicts.has("timing")?null:field(source,"timing",100))
                .quantity(conflicts.has("quantity")?null:editableInteger(source,"quantity")).duration(duration).details(details).build());
        }
        return items;
    }
    private static Integer editableInteger(JsonNode n,String field){var value=n.path(field);if(!value.isNumber())return null;try{int number=value.decimalValue().intValueExact();return number>0?number:null;}catch(ArithmeticException e){return null;}}
    private static String field(JsonNode n,String key,int limit){String value=text(n,key);if(value!=null&&value.length()>limit)throw new BusinessException(422,"Thông tin thuốc quá dài ở trường "+key);return value;}
    private static String text(JsonNode n,String field){if(!n.hasNonNull(field))return null;if(!n.get(field).isTextual()||n.get(field).asText().length()>500)throw new BusinessException(503,"Trường AI không đúng contract");return n.get(field).asText();}
    private static Integer integer(JsonNode n,String field){if(!n.hasNonNull(field))return null;if(!n.get(field).isIntegralNumber()||!n.get(field).canConvertToInt()||n.get(field).asInt()<=0)throw new BusinessException(422,"Số lượng/số ngày AI chưa hợp lệ");return n.get(field).asInt();}
}
