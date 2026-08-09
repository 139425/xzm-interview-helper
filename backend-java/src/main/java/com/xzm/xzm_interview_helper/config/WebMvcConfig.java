package com.xzm.xzm_interview_helper.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Web MVC配置类
 * 确保HTTP请求使用标准的ObjectMapper，不受Redis配置影响
 */
@Slf4j
@Configuration
public class WebMvcConfig {

    /**
     * 配置Spring MVC专用的ObjectMapper
     * 使用@Primary注解确保这是默认的ObjectMapper
     * 这样可以避免Redis的ObjectMapper配置影响HTTP请求处理
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        log.info("配置Spring MVC专用的ObjectMapper（标准配置，不包含类型信息）");
        // 使用Spring Boot的默认配置构建ObjectMapper
        // 这个ObjectMapper不会包含Redis需要的类型信息配置
        return builder.build();
    }
}
