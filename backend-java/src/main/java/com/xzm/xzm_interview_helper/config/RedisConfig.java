package com.xzm.xzm_interview_helper.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis配置类
 * 提供RedisTemplate、StringRedisTemplate和缓存管理器的配置
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    /**
     * 配置Redis专用的ObjectMapper
     * 用于JSON序列化和反序列化
     * 注意：使用@Bean("redisObjectMapper")明确指定Bean名称，避免与Spring MVC的默认ObjectMapper冲突
     */
    @Bean("redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        return objectMapper;
    }

    /**
     * 配置RedisTemplate
     * 使用Jackson2JsonRedisSerializer进行序列化
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        log.info("开始配置RedisTemplate...");
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // 配置Jackson2JsonRedisSerializer (Spring Boot 3.0+ 兼容写法)
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(redisObjectMapper(), Object.class);

        // 配置String序列化器
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // 设置key和hashKey的序列化器
        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);

        // 设置value和hashValue的序列化器
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        // 启用默认序列化器
        template.setDefaultSerializer(jackson2JsonRedisSerializer);
        template.afterPropertiesSet();

        // 测试Redis连接
        try {
            String testResult = template.getConnectionFactory().getConnection().ping();
            log.info("Redis连接测试成功: PING -> {}", testResult);
        } catch (Exception e) {
            log.error("Redis连接测试失败: {}", e.getMessage(), e);
        }

        log.info("RedisTemplate配置完成");
        return template;
    }

    /**
     * 配置StringRedisTemplate
     * 专门用于字符串操作，性能更好
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate() {
        log.info("配置StringRedisTemplate...");
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory);
        return template;
    }

    /**
     * 配置Redis缓存管理器
     * 用于Spring Cache注解
     */
    @Bean
    public CacheManager cacheManager() {
        log.info("配置Redis缓存管理器...");
        
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                // 设置缓存过期时间为1小时
                .entryTtl(Duration.ofHours(1))
                // 设置key序列化器
                .serializeKeysWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                // 设置value序列化器
                .serializeValuesWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                        .fromSerializer(new Jackson2JsonRedisSerializer<>(redisObjectMapper(), Object.class)))
                // 禁用缓存空值
                .disableCachingNullValues();

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(config)
                .build();
    }
}