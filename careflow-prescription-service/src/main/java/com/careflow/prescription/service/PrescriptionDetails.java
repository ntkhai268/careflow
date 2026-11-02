package com.careflow.prescription.service;

import com.careflow.common.exception.BusinessException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class PrescriptionDetails {

    public static final Set<String> FIELDS = Set.of(
            "strength", "dosageForm", "route", "minInterval", "maxDailyDose", "manualReview");

    private PrescriptionDetails() {
    }

    public static Map<String, String> validate(Map<String, String> input) {
        if (input == null) {
            return Map.of();
        }
        if (input.size() > FIELDS.size()) {
            throw new BusinessException(400, "Quá nhiều trường chi tiết thuốc");
        }
        var result = new LinkedHashMap<String, String>();
        for (var entry : input.entrySet()) {
            if (!FIELDS.contains(entry.getKey())
                    || entry.getValue() == null
                    || entry.getValue().length() > 200) {
                throw new BusinessException(400, "Chi tiết thuốc không hợp lệ");
            }
            result.put(entry.getKey(), entry.getValue().strip());
        }
        return result;
    }
}
