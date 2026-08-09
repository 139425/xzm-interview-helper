package com.xzm.xzm_interview_helper.config;

import lombok.Data;
import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "longcat")
@Data
public class LongCatConfig {
    private String apiKey;
    private String baseUrl;
}
