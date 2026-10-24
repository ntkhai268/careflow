package com.careflow.prescription.service;
import java.util.*;import com.careflow.common.exception.BusinessException;
public final class PrescriptionDetails {
    private PrescriptionDetails(){}
    public static final Set<String> FIELDS=Set.of("strength","dosageForm","route","minInterval","maxDailyDose","manualReview");
    public static Map<String,String> validate(Map<String,String> input){if(input==null)return Map.of();if(input.size()>FIELDS.size())throw new BusinessException(400,"Quá nhiều trường chi tiết thuốc");var result=new LinkedHashMap<String,String>();for(var e:input.entrySet()){if(!FIELDS.contains(e.getKey())||e.getValue()==null||e.getValue().length()>200)throw new BusinessException(400,"Chi tiết thuốc không hợp lệ");result.put(e.getKey(),e.getValue().strip());}return result;}
}
