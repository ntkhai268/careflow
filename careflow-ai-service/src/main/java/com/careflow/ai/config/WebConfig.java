package com.careflow.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    // CORS is handled centrally by careflow-api-gateway to prevent duplicate Access-Control-Allow-Origin headers
}
