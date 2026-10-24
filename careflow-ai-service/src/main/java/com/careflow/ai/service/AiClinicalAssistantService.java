package com.careflow.ai.service;
import com.careflow.ai.clinical.ClinicalEngine;import com.careflow.common.exception.BusinessException;import org.springframework.beans.factory.ObjectProvider;import org.springframework.stereotype.Service;
@Service
public class AiClinicalAssistantService {
    private final ObjectProvider<ClinicalEngine> engines;
    public AiClinicalAssistantService(ObjectProvider<ClinicalEngine> engines){this.engines=engines;}
    public ClinicalEngine engine(){var value=engines.getIfAvailable();if(value==null)throw new BusinessException(503,"Trợ lý có nguồn chuyên môn chưa được cấu hình");return value;}
}
