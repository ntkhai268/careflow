package com.careflow.prescription.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.prescription.model.Prescription;
import com.careflow.prescription.model.PrescriptionItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PrescriptionAiReleaseGuard {

    private final JdbcTemplate jdbc;

    public PrescriptionAiReleaseGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void validate(Prescription prescription) {
        for (var item : prescription.getItems()) {
            if (blank(item.getMedicineCode())) {
                if (blank(item.getMedicineName())
                        || blank(item.getUnit())
                        || item.getDetails() == null
                        || !"confirmed".equals(item.getDetails().get("manualReview"))) {
                    throw new BusinessException(422,
                            "Kiểm tra tên, đơn vị và xác nhận thuốc kê bằng tên trước khi ký.");
                }
                requireInstructions(item);
            }
        }

        Boolean hasAiSource = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM prescription_ai_sources WHERE prescription_id=?)",
                Boolean.class,
                prescription.getId());
        if (!Boolean.TRUE.equals(hasAiSource)) {
            return;
        }

        for (var item : prescription.getItems()) {
            requireInstructions(item);
        }
    }

    private static void requireInstructions(PrescriptionItem item) {
        if (blank(item.getDosage())
                || blank(item.getFrequency())
                || blank(item.getTiming())
                || item.getDuration() == null
                || item.getDuration() <= 0
                || item.getQuantity() == null
                || item.getQuantity() <= 0) {
            throw new BusinessException(422,
                    "Toa còn thiếu liều, tần suất, thời điểm, số ngày hoặc số lượng. Bổ sung các ô còn trống trước khi ký.");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
