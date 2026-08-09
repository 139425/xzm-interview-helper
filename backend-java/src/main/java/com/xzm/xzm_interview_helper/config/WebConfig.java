package com.xzm.xzm_interview_helper.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        List<HttpMessageConverter<?>> newConverters = new ArrayList<>(converters.size());
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jsonConverter) {
                List<MediaType> supportedMediaTypes = new ArrayList<>(jsonConverter.getSupportedMediaTypes());
                if (!supportedMediaTypes.contains(MediaType.TEXT_EVENT_STREAM)) {
                    supportedMediaTypes.add(MediaType.TEXT_EVENT_STREAM);
                }
                jsonConverter.setSupportedMediaTypes(supportedMediaTypes);
                newConverters.add(jsonConverter);
            } else {
                newConverters.add(converter);
            }
        }
        converters.clear();
        converters.addAll(newConverters);
    }
}