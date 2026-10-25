package com.careflow.consultation.config;

import com.careflow.common.exception.GlobalExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(GlobalExceptionHandler.class)
public class ConsultationErrorConfiguration {
}
