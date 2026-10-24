package com.careflow.prescription.service;
import org.springframework.stereotype.Component;import org.springframework.jdbc.core.JdbcTemplate;import com.careflow.prescription.model.Prescription;import com.careflow.common.exception.BusinessException;import java.util.*;
@Component
public class PrescriptionAiReleaseGuard {
    private final JdbcTemplate jdbc;public PrescriptionAiReleaseGuard(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void validate(Prescription p){
        for(var item:p.getItems())if(blank(item.getMedicineCode())){
            if(blank(item.getMedicineName())||blank(item.getUnit())||item.getDetails()==null||!"confirmed".equals(item.getDetails().get("manualReview")))throw new BusinessException(422,"Kiểm tra tên, đơn vị và xác nhận thuốc kê bằng tên trước khi ký.");
            requireInstructions(item);
        }
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT1 FROM prescription_ai_sources WHERE prescription_id=?)".replace("SELECT1","SELECT 1"),Boolean.class,p.getId())))return;
        for(var item:p.getItems())requireInstructions(item);
    }
    private static void requireInstructions(com.careflow.prescription.model.PrescriptionItem item){if(blank(item.getDosage())||blank(item.getFrequency())||blank(item.getTiming())||item.getDuration()==null||item.getDuration()<=0||item.getQuantity()==null||item.getQuantity()<=0)throw new BusinessException(422,"Toa còn thiếu liều, tần suất, thời điểm, số ngày hoặc số lượng. Bổ sung các ô còn trống trước khi ký.");}
    private static boolean blank(String s){return s==null||s.isBlank();}
}
